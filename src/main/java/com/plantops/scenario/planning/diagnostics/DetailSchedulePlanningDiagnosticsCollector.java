package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.api.dto.planning.PlanningDiagnosticIssue;
import com.plantops.solver.detailschedule.OperationAssignment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 详细排程推演过程中累积计数与 issue。 */
public final class DetailSchedulePlanningDiagnosticsCollector {

    public static final int DEFAULT_MAX_ISSUES = 100;

    private final int maxIssues;
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final List<PlanningDiagnosticIssue> issues = new ArrayList<>();
    private boolean issuesTruncated;

    public DetailSchedulePlanningDiagnosticsCollector() {
        this(DEFAULT_MAX_ISSUES);
    }

    public DetailSchedulePlanningDiagnosticsCollector(int maxIssues) {
        this.maxIssues = Math.max(1, maxIssues);
        initCounters();
    }

    private void initCounters() {
        counters.put(PlanningDiagnosticCodes.DS_WORK_ORDERS_SCANNED, 0);
        counters.put(PlanningDiagnosticCodes.DS_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE, 0);
        counters.put(PlanningDiagnosticCodes.DS_WORK_ORDERS_SKIPPED_NO_ROUTING, 0);
        counters.put(PlanningDiagnosticCodes.DS_WORK_ORDERS_INCLUDED, 0);
        counters.put(PlanningDiagnosticCodes.DS_OPERATIONS_TOTAL, 0);
        counters.put(PlanningDiagnosticCodes.DS_OPERATIONS_KITTING_INELIGIBLE, 0);
        counters.put(PlanningDiagnosticCodes.DS_OPERATIONS_WITH_MP_CONTRACT, 0);
        counters.put(PlanningDiagnosticCodes.DS_OPERATIONS_MP_TARGET_FALLBACK, 0);
        counters.put(PlanningDiagnosticCodes.DS_SCHEDULE_LINES_TOTAL, 0);
        counters.put(PlanningDiagnosticCodes.DS_SCHEDULE_LINES_OPENED, 0);
        counters.put(PlanningDiagnosticCodes.DS_MP_CONTRACTS_LOADED, 0);
        counters.put(PlanningDiagnosticCodes.DS_INVENTORY_PRODUCT_COUNT, 0);
        counters.put(PlanningDiagnosticCodes.DS_PARALLEL_PAIRED_OPS, 0);
        counters.put(PlanningDiagnosticCodes.DS_PARALLEL_ORPHAN_OPS, 0);
        counters.put(PlanningDiagnosticCodes.DS_CONTINUOUS_PRODUCTION_OPS, 0);
    }

    public void increment(String counterKey) {
        counters.merge(counterKey, 1, Integer::sum);
    }

    public void set(String counterKey, int value) {
        counters.put(counterKey, value);
    }

    public void recordSkip(String reasonCode, String workOrderNo, String message) {
        increment(skipCounterFor(reasonCode));
        recordIssue("SKIP", reasonCode, workOrderNo, null, message);
    }

    public void recordWarn(String reasonCode, String workOrderNo, String entityId, String message) {
        recordIssue("WARN", reasonCode, workOrderNo, entityId, message);
    }

    private void recordIssue(String severity, String reasonCode, String workOrderNo, String entityId, String message) {
        if (issues.size() >= maxIssues) {
            issuesTruncated = true;
            return;
        }
        issues.add(new PlanningDiagnosticIssue(severity, reasonCode, workOrderNo, entityId, message));
    }

    /** 绑定规则应用后扫描 OperationAssignment 上的并行/连续标记。 */
    public void scanBindingFlags(List<OperationAssignment> operations) {
        int paired = 0;
        int orphan = 0;
        int continuous = 0;
        Set<String> pairGroups = new HashSet<>();
        for (OperationAssignment op : operations) {
            if (op.isParallelPaired()) {
                paired++;
                if (op.getPairGroupId() != null) {
                    pairGroups.add(op.getPairGroupId());
                }
            }
            if (op.isParallelOrphan()) {
                orphan++;
            }
            if (op.isContinuousProduction()) {
                continuous++;
            }
        }
        set(PlanningDiagnosticCodes.DS_PARALLEL_PAIRED_OPS, paired);
        set(PlanningDiagnosticCodes.DS_PARALLEL_ORPHAN_OPS, orphan);
        set(PlanningDiagnosticCodes.DS_CONTINUOUS_PRODUCTION_OPS, continuous);
    }

    private static String skipCounterFor(String reasonCode) {
        return switch (reasonCode) {
            case PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE ->
                    PlanningDiagnosticCodes.DS_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE;
            case PlanningDiagnosticCodes.WO_NO_ROUTING ->
                    PlanningDiagnosticCodes.DS_WORK_ORDERS_SKIPPED_NO_ROUTING;
            default -> PlanningDiagnosticCodes.DS_WORK_ORDERS_SCANNED;
        };
    }

    public DetailSchedulePlanningDiagnosticsDto toDto(String masterPlanVersionId) {
        return toDto(masterPlanVersionId, null);
    }

    public DetailSchedulePlanningDiagnosticsDto toDto(String masterPlanVersionId, String inventorySnapshotId) {
        return new DetailSchedulePlanningDiagnosticsDto(
                LocalDateTime.now(),
                masterPlanVersionId,
                inventorySnapshotId,
                Map.copyOf(counters),
                List.copyOf(issues),
                issuesTruncated);
    }
}
