package com.plantops.scenario;

import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.DetailScheduleOperationDto;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.persistence.entity.ProductionBatchEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.detailschedule.ScheduleTimingUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生产排程页左侧关键 KPI。 */
@ApplicationScoped
public class DetailScheduleKpiService {

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    public List<DemandPoolKpiDto> pageKpis(String detailScheduleVersionId, List<DetailScheduleOperationDto> operations) {
        List<DemandPoolKpiDto> out = new ArrayList<>();
        out.add(pendingBatchKpi());
        if (operations == null || operations.isEmpty()) {
            out.add(kpi("ds_avg_line_util", "产能利用率", 0, "%", "info"));
            out.add(kpi("ds_changeover", "切换次数", 0, "次 / 0 分", "info"));
            out.add(kpi("ds_late_operation_ratio", "延期工序比例", 0, "%", "ok"));
            return out;
        }
        LocalDate anchor = LocalDate.now();
        int horizonDays = timeslotHorizonService.totalCalendarDays();
        int horizonMinutes = horizonDays * ScheduleTimingUtil.MINUTES_PER_DAY;
        ChangeoverRuleIndex changeoverRules = businessRuleScopeService.loadChangeoverIndex();

        out.add(lineUtilizationKpi(operations, anchor, horizonDays, horizonMinutes));
        out.add(changeoverKpi(operations, changeoverRules));
        out.add(lateOperationRatioKpi(operations, anchor));
        return out;
    }

    private DemandPoolKpiDto pendingBatchKpi() {
        int total = 0;
        int eligible = 0;
        for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveOrdered()) {
            WorkOrderEntity wo = WorkOrderEntity.findByNo(batch.workOrderNo);
            if (wo == null || !WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
                continue;
            }
            total++;
            if (batch.pendingScheduleEligible == null || batch.pendingScheduleEligible) {
                eligible++;
            }
        }
        String unit = total > 0 ? "/" + total + " 批" : "批";
        String severity = total == 0 ? "info" : eligible == 0 ? "danger" : eligible < total ? "warn" : "ok";
        return kpi("ds_pending_batches", "待排批次", eligible, unit, severity);
    }

    private DemandPoolKpiDto lineUtilizationKpi(
            List<DetailScheduleOperationDto> operations,
            LocalDate anchor,
            int horizonDays,
            int horizonMinutes) {
        Map<String, Integer> busyByLine = new HashMap<>();
        for (DetailScheduleOperationDto op : operations) {
            if (op.lineId() == null || op.lineId().isBlank()
                    || op.startMinute() == null || op.endMinute() == null) {
                continue;
            }
            int start = Math.max(0, op.startMinute());
            int end = Math.min(horizonMinutes, op.endMinute());
            if (end > start) {
                busyByLine.merge(op.lineId(), end - start, Integer::sum);
            }
        }

        List<Double> utils = new ArrayList<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            int capacity = 0;
            for (int d = 0; d < horizonDays; d++) {
                capacity += timeslotHorizonService.capacityForProductionLine(line, anchor.plusDays(d));
            }
            if (capacity <= 0) {
                continue;
            }
            int busy = busyByLine.getOrDefault(line.lineId, 0);
            utils.add(Math.min(999.0, busy * 100.0 / capacity));
        }
        double avg = utils.isEmpty() ? 0.0 : utils.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        String severity = avg > 100 ? "danger" : avg > 90 ? "warn" : avg > 0 ? "ok" : "info";
        return kpi("ds_avg_line_util", "产能利用率", round1(avg), "%", severity);
    }

    private DemandPoolKpiDto changeoverKpi(
            List<DetailScheduleOperationDto> operations,
            ChangeoverRuleIndex changeoverRules) {
        Map<String, List<DetailScheduleOperationDto>> byLine = new LinkedHashMap<>();
        for (DetailScheduleOperationDto op : operations) {
            if (op.lineId() == null || op.lineId().isBlank()) {
                continue;
            }
            byLine.computeIfAbsent(op.lineId(), k -> new ArrayList<>()).add(op);
        }

        int switchCount = 0;
        int switchMinutes = 0;
        for (List<DetailScheduleOperationDto> lineOps : byLine.values()) {
            lineOps.sort(Comparator
                    .comparingInt((DetailScheduleOperationDto op) -> op.startMinute() != null ? op.startMinute() : 0)
                    .thenComparingInt(DetailScheduleOperationDto::sequenceIndex));
            DetailScheduleOperationDto previous = null;
            for (DetailScheduleOperationDto op : lineOps) {
                if (previous != null) {
                    int seq = parseOperationSeq(op.operationId());
                    int minutes = changeoverRules.computeMinutes(
                            routingOperationNameFor(op, seq),
                            op.resourceId(),
                            seq,
                            previous.productCode(),
                            op.productCode());
                    if (minutes > 0) {
                        switchCount++;
                        switchMinutes += minutes;
                    }
                }
                previous = op;
            }
        }
        String severity = switchCount > 0 ? "info" : "ok";
        return kpi("ds_changeover", "切换次数", switchCount, "次 / " + switchMinutes + " 分", severity);
    }

    private DemandPoolKpiDto lateOperationRatioKpi(List<DetailScheduleOperationDto> operations, LocalDate anchor) {
        Map<String, Integer> maxSeqByWo = new HashMap<>();
        Map<String, LocalDate> dueByWo = new HashMap<>();
        for (DetailScheduleOperationDto op : operations) {
            int seq = parseOperationSeq(op.operationId());
            if (seq < 0) {
                continue;
            }
            maxSeqByWo.merge(op.workOrderNo(), seq, Math::max);
            dueByWo.computeIfAbsent(op.workOrderNo(), this::resolveDueDate);
        }

        int lastOpTotal = 0;
        int lastOpLate = 0;
        for (DetailScheduleOperationDto op : operations) {
            int seq = parseOperationSeq(op.operationId());
            if (seq < 0 || op.startMinute() == null || op.endMinute() == null) {
                continue;
            }
            Integer maxSeq = maxSeqByWo.get(op.workOrderNo());
            if (maxSeq == null || seq != maxSeq) {
                continue;
            }
            lastOpTotal++;
            LocalDate due = dueByWo.get(op.workOrderNo());
            int duration = Math.max(1, op.endMinute() - op.startMinute());
            LocalDate actualEnd = ScheduleTimingUtil.completionDate(anchor, op.startMinute(), duration);
            if (due != null && actualEnd != null && actualEnd.isAfter(due)) {
                lastOpLate++;
            }
        }

        double ratio = lastOpTotal == 0 ? 0.0 : lastOpLate * 100.0 / lastOpTotal;
        String severity = ratio > 30 ? "danger" : ratio > 10 ? "warn" : "ok";
        return kpi("ds_late_operation_ratio", "延期工序比例", round1(ratio), "%", severity);
    }

    private String routingOperationNameFor(DetailScheduleOperationDto op, int seq) {
        if (seq < 0) {
            return "工序";
        }
        WorkOrderEntity wo = WorkOrderEntity.findByNo(op.workOrderNo());
        if (wo == null) {
            return "工序 " + seq;
        }
        return ProductRoutingSteps.forProduct(wo.productCode).stream()
                .filter(s -> s.sequenceNo() == seq)
                .map(ProductRoutingSteps.Step::operationName)
                .findFirst()
                .orElse("工序 " + seq);
    }

    static int parseOperationSeq(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return -1;
        }
        int underscore = operationId.lastIndexOf('_');
        int dash = operationId.lastIndexOf('-', underscore > 0 ? underscore : operationId.length());
        if (dash < 0 || underscore <= dash) {
            return -1;
        }
        try {
            return Integer.parseInt(operationId.substring(dash + 1, underscore));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private LocalDate resolveDueDate(String workOrderNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            return null;
        }
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
        return order != null ? order.dueDate : LocalDate.now().plusDays(7);
    }

    private static String normalizeDispatch(WorkOrderEntity wo) {
        return wo.dispatchStatus != null ? wo.dispatchStatus.trim().toUpperCase() : "";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static DemandPoolKpiDto kpi(String id, String label, double value, String unit, String severity) {
        return new DemandPoolKpiDto(id, label, value, unit, severity);
    }
}
