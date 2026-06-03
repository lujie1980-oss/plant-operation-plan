package com.plantops.scenario;

import com.plantops.api.dto.ScheduleFeedbackApplyResultDto;
import com.plantops.api.dto.ScheduleFeedbackDto;
import com.plantops.api.dto.WorkOrderScheduleOperationDto;
import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.persistence.entity.ScheduleFeedbackScope;
import com.plantops.solver.detailschedule.ScheduleTimingUtil;
import com.plantops.solver.masterplan.SlotFixedLoad;
import com.plantops.solver.masterplan.TimeslotGranularity;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ScheduleFeedbackService {

    private static final Pattern OP_ID_SEQ = Pattern.compile("^OP-(.+)-(\\d+)$");

    @Transactional
    public ScheduleFeedbackApplyResultDto recordFromDetailSchedule(
            String detailScheduleVersionId,
            String masterPlanVersionId,
            LocalDate cutoffDate) {
        PlanVersionEntity dsVersion = PlanVersionEntity.findByVersionId(detailScheduleVersionId);
        if (dsVersion == null || !"DETAIL_SCHEDULE".equals(dsVersion.planType)) {
            throw new NotFoundException("Detail schedule version not found: " + detailScheduleVersionId);
        }
        LocalDate anchor = LocalDate.now();
        LocalDate cutoff = cutoffDate != null ? cutoffDate : anchor;

        ScheduleFeedbackEntity.deleteForDetailSchedule(detailScheduleVersionId);

        String batchId = "FB-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        List<DetailScheduleOperationEntity> ops = DetailScheduleOperationEntity
                .find("workspaceId = ?1 and planVersionId = ?2", DetailScheduleOperationEntity.ws(), detailScheduleVersionId)
                .list();

        int frozen = 0;
        int suggestion = 0;
        for (DetailScheduleOperationEntity op : ops) {
            int duration = Math.max(1, op.endMinute - op.startMinute);
            LocalDate startDay = ScheduleTimingUtil.startDate(anchor, op.startMinute);
            LocalDate endDay = ScheduleTimingUtil.completionDate(anchor, op.startMinute, duration);
            if (endDay == null) {
                endDay = startDay;
            }
            LocalDateTime plannedStart = ScheduleTimingUtil.startDateTime(anchor, op.startMinute);
            LocalDateTime plannedEnd = ScheduleTimingUtil.completionDateTime(anchor, op.startMinute, duration);

            ScheduleFeedbackScope scope = !endDay.isAfter(cutoff)
                    ? ScheduleFeedbackScope.FROZEN
                    : ScheduleFeedbackScope.SUGGESTION;
            if (scope == ScheduleFeedbackScope.FROZEN) {
                frozen++;
            } else {
                suggestion++;
            }

            ScheduleFeedbackEntity row = new ScheduleFeedbackEntity();
            row.feedbackId = batchId;
            row.masterPlanVersionId = masterPlanVersionId;
            row.detailScheduleVersionId = detailScheduleVersionId;
            row.workOrderNo = op.workOrderNo;
            row.operationSeq = parseOperationSeq(op.operationId);
            row.operationId = op.operationId;
            row.resourceId = resolveResourceId(op);
            row.plannedStart = plannedStart;
            row.plannedEnd = plannedEnd;
            row.slotDate = endDay;
            row.durationMinutes = Math.max(1, op.endMinute - op.startMinute);
            row.scope = scope.name();
            row.planningAnchorDate = anchor;
            row.feedbackTs = now;
            row.stampWorkspace();
            row.persist();
        }

        return new ScheduleFeedbackApplyResultDto(
                batchId,
                detailScheduleVersionId,
                masterPlanVersionId,
                cutoff,
                ops.size(),
                frozen,
                suggestion);
    }

    public List<ScheduleFeedbackDto> listForDetailSchedule(String detailScheduleVersionId) {
        return ScheduleFeedbackEntity.listForDetailSchedule(detailScheduleVersionId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<ScheduleFeedbackDto> listFrozenUpTo(LocalDate cutoff) {
        LocalDate effective = cutoff != null ? cutoff : LocalDate.now();
        return ScheduleFeedbackEntity.listFrozenUpTo(effective).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 将冻结反馈映射到主计划时间槽上的固定负荷。
     */
    public List<SlotFixedLoad> buildFixedLoadsForSlots(List<TimeSlot> slots, LocalDate cutoff) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        LocalDate effectiveCutoff = cutoff != null ? cutoff : LocalDate.now();
        List<ScheduleFeedbackEntity> frozen = ScheduleFeedbackEntity.listFrozenUpTo(effectiveCutoff);
        if (frozen.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> minutesBySlotId = new LinkedHashMap<>();
        for (ScheduleFeedbackEntity fb : frozen) {
            TimeSlot slot = matchSlot(slots, fb.resourceId, fb.slotDate);
            if (slot != null) {
                minutesBySlotId.merge(slot.getId(), fb.durationMinutes, Integer::sum);
            }
        }
        List<SlotFixedLoad> loads = new ArrayList<>();
        minutesBySlotId.forEach((slotId, minutes) -> loads.add(new SlotFixedLoad(slotId, minutes)));
        return loads;
    }

    /**
     * 工单是否已在 cutoff 前通过排程完成（末道反馈 end ≤ cutoff）。
     */
    public boolean isWorkOrderFrozenThroughCutoff(String workOrderNo, LocalDate cutoff) {
        LocalDate effective = cutoff != null ? cutoff : LocalDate.now();
        List<ScheduleFeedbackEntity> rows = ScheduleFeedbackEntity.list(
                "workspaceId = ?1 and workOrderNo = ?2 and scope = ?3",
                ScheduleFeedbackEntity.ws(),
                workOrderNo,
                ScheduleFeedbackScope.FROZEN.name());
        if (rows.isEmpty()) {
            return false;
        }
        return rows.stream()
                .map(r -> r.slotDate)
                .max(LocalDate::compareTo)
                .map(d -> !d.isAfter(effective))
                .orElse(false);
    }

    public Map<String, List<ScheduleFeedbackEntity>> frozenAllocationsByWorkOrder(LocalDate cutoff) {
        Map<String, List<ScheduleFeedbackEntity>> grouped = new LinkedHashMap<>();
        for (ScheduleFeedbackEntity fb : ScheduleFeedbackEntity.listFrozenUpTo(
                cutoff != null ? cutoff : LocalDate.now())) {
            grouped.computeIfAbsent(fb.workOrderNo, k -> new ArrayList<>()).add(fb);
        }
        return grouped;
    }

    public TimeSlot resolveSlot(List<TimeSlot> slots, String resourceId, LocalDate slotDate) {
        return matchSlot(slots, resourceId, slotDate);
    }

    private static TimeSlot matchSlot(List<TimeSlot> slots, String resourceId, LocalDate slotDate) {
        TimeSlot best = null;
        for (TimeSlot slot : slots) {
            if (!resourceId.equals(slot.getResourceId())) {
                continue;
            }
            if (slotDate.isBefore(slot.getDate()) || slotDate.isAfter(slot.getPeriodEnd())) {
                continue;
            }
            if (best == null || slot.getGranularity().ordinal() < best.getGranularity().ordinal()) {
                best = slot;
            }
        }
        if (best != null) {
            return best;
        }
        for (TimeSlot slot : slots) {
            if (resourceId.equals(slot.getResourceId()) && slot.getDate().equals(slotDate)) {
                return slot;
            }
        }
        return null;
    }

    private static int parseOperationSeq(String operationId) {
        if (operationId == null) {
            return 0;
        }
        Matcher m = OP_ID_SEQ.matcher(operationId);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String resolveResourceId(DetailScheduleOperationEntity op) {
        if (op.lineId != null && !op.lineId.isBlank()) {
            com.plantops.persistence.entity.ProductionLineEntity line =
                    com.plantops.persistence.entity.ProductionLineEntity.find(
                            "workspaceId = ?1 and lineId = ?2",
                            ScheduleFeedbackEntity.ws(),
                            op.lineId)
                            .firstResult();
            if (line != null && line.resourceId != null && !line.resourceId.isBlank()) {
                return line.resourceId;
            }
        }
        return "UNKNOWN";
    }

    public record WorkOrderFeedbackFlags(
            boolean hasScheduleFeedback,
            boolean hasFrozenScheduleFeedback,
            int operationCount) {
    }

    /** 解析与主计划版本关联的排程版本（沿 parent 链或最新反馈）。 */
    public String resolveDetailScheduleVersionId(String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            PlanVersionEntity cursor = PlanVersionEntity.findByVersionId(masterPlanVersionId);
            while (cursor != null) {
                if (cursor.sourceDetailScheduleVersionId != null
                        && !cursor.sourceDetailScheduleVersionId.isBlank()) {
                    return cursor.sourceDetailScheduleVersionId;
                }
                cursor = cursor.parentPlanVersionId != null
                        ? PlanVersionEntity.findByVersionId(cursor.parentPlanVersionId)
                        : null;
            }
        }
        ScheduleFeedbackEntity latest = ScheduleFeedbackEntity
                .find("workspaceId = ?1 order by feedbackTs desc", ScheduleFeedbackEntity.ws())
                .firstResult();
        if (latest != null) {
            return latest.detailScheduleVersionId;
        }
        PlanVersionEntity latestDs = PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> "DETAIL_SCHEDULE".equals(v.planType))
                .max(java.util.Comparator.comparing(
                        v -> v.planGeneratedTs,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(null);
        return latestDs != null ? latestDs.planVersionId : null;
    }

    public Map<String, WorkOrderFeedbackFlags> feedbackFlagsByWorkOrder(String detailScheduleVersionId) {
        if (detailScheduleVersionId == null || detailScheduleVersionId.isBlank()) {
            return Map.of();
        }
        Map<String, WorkOrderFeedbackFlags> map = new LinkedHashMap<>();
        for (ScheduleFeedbackEntity fb : ScheduleFeedbackEntity.listForDetailSchedule(detailScheduleVersionId)) {
            WorkOrderFeedbackFlags prev = map.getOrDefault(
                    fb.workOrderNo, new WorkOrderFeedbackFlags(false, false, 0));
            boolean frozen = ScheduleFeedbackScope.FROZEN.name().equals(fb.scope);
            map.put(
                    fb.workOrderNo,
                    new WorkOrderFeedbackFlags(
                            true,
                            prev.hasFrozenScheduleFeedback() || frozen,
                            prev.operationCount() + 1));
        }
        return map;
    }

    public int frozenMinutesForCapacityBucket(
            String resourceId,
            TimeslotHorizonService.BucketKey key) {
        if (resourceId == null || key == null) {
            return 0;
        }
        int sum = 0;
        List<ScheduleFeedbackEntity> rows = ScheduleFeedbackEntity.list(
                "workspaceId = ?1 and resourceId = ?2 and scope = ?3",
                ScheduleFeedbackEntity.ws(),
                resourceId,
                ScheduleFeedbackScope.FROZEN.name());
        for (ScheduleFeedbackEntity fb : rows) {
            if (key.granularity() == TimeslotGranularity.WEEK) {
                if (!fb.slotDate.isBefore(key.periodStart()) && !fb.slotDate.isAfter(key.periodEnd())) {
                    sum += fb.durationMinutes;
                }
            } else if (fb.slotDate.equals(key.bucketDate())) {
                sum += fb.durationMinutes;
            }
        }
        return sum;
    }

    public List<WorkOrderScheduleOperationDto> scheduleOperationsForWorkOrder(
            String workOrderNo,
            String detailScheduleVersionId) {
        if (workOrderNo == null || workOrderNo.isBlank()) {
            return List.of();
        }
        String dsId = detailScheduleVersionId;
        if (dsId == null || dsId.isBlank()) {
            dsId = resolveDetailScheduleVersionId(null);
        }
        if (dsId == null) {
            return List.of();
        }
        List<ScheduleFeedbackEntity> rows = ScheduleFeedbackEntity.list(
                "workspaceId = ?1 and detailScheduleVersionId = ?2 and workOrderNo = ?3 order by operationSeq, operationId",
                ScheduleFeedbackEntity.ws(),
                dsId,
                workOrderNo);
        if (!rows.isEmpty()) {
            return rows.stream().map(this::toScheduleOperationDto).toList();
        }
        return buildOperationsFromDetailSchedule(dsId, workOrderNo);
    }

    private List<WorkOrderScheduleOperationDto> buildOperationsFromDetailSchedule(
            String detailScheduleVersionId,
            String workOrderNo) {
        List<DetailScheduleOperationEntity> ops = DetailScheduleOperationEntity
                .find("workspaceId = ?1 and planVersionId = ?2 and workOrderNo = ?3 order by sequenceIndex",
                        DetailScheduleOperationEntity.ws(), detailScheduleVersionId, workOrderNo)
                .list();
        if (ops.isEmpty()) {
            return List.of();
        }
        LocalDate anchor = LocalDate.now();
        List<WorkOrderScheduleOperationDto> result = new ArrayList<>();
        for (DetailScheduleOperationEntity op : ops) {
            int duration = Math.max(1, op.endMinute - op.startMinute);
            LocalDate startDay = ScheduleTimingUtil.startDate(anchor, op.startMinute);
            LocalDate endDay = ScheduleTimingUtil.completionDate(anchor, op.startMinute, duration);
            if (endDay == null) {
                endDay = startDay;
            }
            LocalDateTime plannedStart = ScheduleTimingUtil.startDateTime(anchor, op.startMinute);
            LocalDateTime plannedEnd = ScheduleTimingUtil.completionDateTime(anchor, op.startMinute, duration);
            String resourceId = resolveResourceId(op);
            int seq = parseOperationSeq(op.operationId);
            String opName = operationNameFor(op.workOrderNo, seq);
            result.add(new WorkOrderScheduleOperationDto(
                    op.operationId,
                    seq,
                    opName,
                    resourceId,
                    plannedStart,
                    plannedEnd,
                    duration,
                    "SUGGESTION"));
        }
        return result;
    }

    private WorkOrderScheduleOperationDto toScheduleOperationDto(ScheduleFeedbackEntity fb) {
        return new WorkOrderScheduleOperationDto(
                fb.operationId,
                fb.operationSeq,
                operationNameFor(fb.workOrderNo, fb.operationSeq),
                fb.resourceId,
                fb.plannedStart,
                fb.plannedEnd,
                fb.durationMinutes,
                fb.scope);
    }

    private String operationNameFor(String workOrderNo, int operationSeq) {
        com.plantops.persistence.entity.WorkOrderEntity wo =
                com.plantops.persistence.entity.WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            return "工序 " + operationSeq;
        }
        return ProductRoutingSteps.forProduct(wo.productCode).stream()
                .filter(s -> s.sequenceNo() == operationSeq)
                .map(ProductRoutingSteps.Step::operationName)
                .findFirst()
                .orElse("工序 " + operationSeq);
    }

    private ScheduleFeedbackDto toDto(ScheduleFeedbackEntity e) {
        return new ScheduleFeedbackDto(
                e.feedbackId,
                e.masterPlanVersionId,
                e.detailScheduleVersionId,
                e.workOrderNo,
                e.operationSeq,
                e.operationId,
                e.resourceId,
                e.plannedStart,
                e.plannedEnd,
                e.slotDate,
                e.durationMinutes,
                e.scope,
                e.planningAnchorDate,
                e.feedbackTs);
    }
}
