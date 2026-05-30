package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.api.dto.planning.PlanningDiagnosticIssue;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主计划推演过程中累积计数与 issue（Builder 单线程使用）。 */
public final class MasterPlanPlanningDiagnosticsCollector {

    public static final int DEFAULT_MAX_ISSUES = 100;

    private final int maxIssues;
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final List<PlanningDiagnosticIssue> issues = new ArrayList<>();
    private boolean issuesTruncated;

    public MasterPlanPlanningDiagnosticsCollector() {
        this(DEFAULT_MAX_ISSUES);
    }

    public MasterPlanPlanningDiagnosticsCollector(int maxIssues) {
        this.maxIssues = Math.max(1, maxIssues);
        initCounters();
    }

    private void initCounters() {
        counters.put(PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED, 0);
        counters.put(PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE, 0);
        counters.put(PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_FROZEN, 0);
        counters.put(PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NO_ROUTING, 0);
        counters.put(PlanningDiagnosticCodes.MP_WORK_ORDERS_WITH_ALLOCATIONS, 0);
        counters.put(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_CANDIDATE, 0);
        counters.put(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE, 0);
        counters.put(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS, 0);
        counters.put(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK, 0);
        counters.put(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_LOCKED, 0);
        counters.put(PlanningDiagnosticCodes.MP_TIME_SLOT_COUNT, 0);
        counters.put(PlanningDiagnosticCodes.MP_BOM_DEPENDENCY_EDGE_COUNT, 0);
        counters.put(PlanningDiagnosticCodes.MP_INVENTORY_PRODUCT_COUNT, 0);
        counters.put(PlanningDiagnosticCodes.MP_PARALLEL_GROUPS, 0);
        counters.put(PlanningDiagnosticCodes.MP_PARALLEL_ORPHANS, 0);
        counters.put(PlanningDiagnosticCodes.MP_PARALLEL_SLOT_INTERSECTIONS, 0);
        counters.put(PlanningDiagnosticCodes.MP_PARALLEL_SLOT_FALLBACKS, 0);
        counters.put(PlanningDiagnosticCodes.MP_OPERATION_PRECEDENCE_EDGES, 0);
    }

    public void increment(String counterKey) {
        counters.merge(counterKey, 1, Integer::sum);
    }

    public void set(String counterKey, int value) {
        counters.put(counterKey, value);
    }

    public void add(String counterKey, int delta) {
        counters.merge(counterKey, delta, Integer::sum);
    }

    public void recordSkip(String reasonCode, String workOrderNo, String entityId, String message) {
        increment(skipCounterFor(reasonCode));
        recordIssue("SKIP", reasonCode, workOrderNo, entityId, message);
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

    private static String skipCounterFor(String reasonCode) {
        return switch (reasonCode) {
            case PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE ->
                    PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE;
            case PlanningDiagnosticCodes.WO_FROZEN_THROUGH_CUTOFF ->
                    PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_FROZEN;
            case PlanningDiagnosticCodes.WO_NO_ROUTING ->
                    PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NO_ROUTING;
            default -> PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED;
        };
    }

    public MasterPlanPlanningDiagnosticsDto toDto(
            MasterPlanCapacityStrategy strategy,
            boolean overlayActive) {
        return toDto(strategy, overlayActive, null);
    }

    public MasterPlanPlanningDiagnosticsDto toDto(
            MasterPlanCapacityStrategy strategy,
            boolean overlayActive,
            String inventorySnapshotId) {
        return new MasterPlanPlanningDiagnosticsDto(
                LocalDateTime.now(),
                strategy != null ? strategy.name() : null,
                overlayActive,
                inventorySnapshotId,
                Map.copyOf(counters),
                List.copyOf(issues),
                issuesTruncated);
    }
}
