package com.plantops.ontology.scheduling;

import com.plantops.api.dto.DetailScheduleOperationDto;
import com.plantops.solver.detailschedule.ScheduleTimingUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SCH-P0: legacy {@code detail_schedule_operation} → ENT-OP-SCH / ENT-RCA-SCH (read-only). */
public final class DetailScheduleLegacyProjector {

    private DetailScheduleLegacyProjector() {}

    public static DetailScheduleOntologyView project(
            String detailScheduleVersionId,
            LocalDate planningAnchorDate,
            List<DetailScheduleOperationDto> operations) {
        if (detailScheduleVersionId == null || detailScheduleVersionId.isBlank()) {
            throw new IllegalArgumentException("detailScheduleVersionId required");
        }
        LocalDate anchor = planningAnchorDate != null ? planningAnchorDate : LocalDate.now();
        if (operations == null || operations.isEmpty()) {
            return new DetailScheduleOntologyView(detailScheduleVersionId, anchor, List.of(), List.of());
        }
        List<OperationSchedule> opSchedules = new ArrayList<>(operations.size());
        List<PhysicalResourceCapacityAssignmentSchedule> rcas = new ArrayList<>(operations.size());
        for (DetailScheduleOperationDto op : operations) {
            if (op == null || op.operationId() == null) {
                continue;
            }
            OperationSchedule schedule = toOperationSchedule(detailScheduleVersionId, anchor, op);
            opSchedules.add(schedule);
            rcas.add(toCapacityAssignment(schedule, op));
        }
        return new DetailScheduleOntologyView(
                detailScheduleVersionId,
                anchor,
                List.copyOf(opSchedules),
                List.copyOf(rcas));
    }

    static String operationScheduleId(String detailScheduleVersionId, String operationId) {
        return "OPS-SCH-" + detailScheduleVersionId + "-" + operationId;
    }

    static String capacityAssignmentScheduleId(String operationId, String physicalResourceId) {
        return "RCAS-" + operationId + "-" + physicalResourceId;
    }

    private static OperationSchedule toOperationSchedule(
            String detailScheduleVersionId,
            LocalDate anchor,
            DetailScheduleOperationDto op) {
        int start = op.startMinute() != null ? op.startMinute() : 0;
        int end = op.endMinute() != null ? op.endMinute() : start;
        int duration = Math.max(1, end - start);
        LocalDateTime plannedStart = ScheduleTimingUtil.startDateTime(anchor, start);
        LocalDateTime plannedEnd = ScheduleTimingUtil.completionDateTime(anchor, start, duration);

        OperationSchedule schedule = new OperationSchedule();
        schedule.setId(operationScheduleId(detailScheduleVersionId, op.operationId()));
        schedule.setDetailScheduleVersionId(detailScheduleVersionId);
        schedule.setOperationId(op.operationId());
        schedule.setWorkOrderNo(op.workOrderNo());
        schedule.setBatchNo(op.batchNo());
        schedule.setPhysicalResourceId(op.lineId());
        schedule.setStandardResourceId(op.resourceId());
        schedule.setSequenceIndex(op.sequenceIndex());
        schedule.setOperationSeq(op.operationSeq());
        schedule.setOperationName(op.operationName());
        schedule.setProductCode(op.productCode());
        schedule.setStartMinute(start);
        schedule.setEndMinute(end);
        schedule.setDurationMinutes(duration);
        schedule.setPlanningAnchorDate(anchor);
        schedule.setPlannedStartTs(plannedStart);
        schedule.setPlannedEndTs(plannedEnd);
        schedule.setPinned(op.pinned());
        return schedule;
    }

    private static PhysicalResourceCapacityAssignmentSchedule toCapacityAssignment(
            OperationSchedule schedule,
            DetailScheduleOperationDto op) {
        String physicalId = op.lineId() != null && !op.lineId().isBlank() ? op.lineId() : "UNKNOWN";
        LocalDate slotDate = ScheduleTimingUtil.completionDate(
                schedule.getPlanningAnchorDate(), schedule.getStartMinute(), schedule.getDurationMinutes());
        if (slotDate == null) {
            slotDate = ScheduleTimingUtil.startDate(schedule.getPlanningAnchorDate(), schedule.getStartMinute());
        }

        PhysicalResourceCapacityAssignmentSchedule rca = new PhysicalResourceCapacityAssignmentSchedule();
        rca.setId(capacityAssignmentScheduleId(op.operationId(), physicalId));
        rca.setOperationScheduleId(schedule.getId());
        rca.setOperationId(op.operationId());
        rca.setPhysicalResourceId(physicalId);
        rca.setStandardResourceId(op.resourceId());
        rca.setAssignedMinutes(schedule.getDurationMinutes());
        rca.setOperationTotalMinutes(schedule.getDurationMinutes());
        rca.setLocked(op.pinned());
        rca.setSlotDate(slotDate);
        rca.setPlannedStartTs(schedule.getPlannedStartTs());
        rca.setPlannedEndTs(schedule.getPlannedEndTs());
        return rca;
    }
}
