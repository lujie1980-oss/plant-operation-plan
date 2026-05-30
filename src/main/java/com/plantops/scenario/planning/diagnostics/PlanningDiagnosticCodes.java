package com.plantops.scenario.planning.diagnostics;

/** 推演诊断计数器与 issue 的 reasonCode 常量（API 稳定标识）。 */
public final class PlanningDiagnosticCodes {

    private PlanningDiagnosticCodes() {
    }

    // --- 工单级（S04/S05 共用）---
    public static final String WO_NOT_SCHEDULABLE = "WO_NOT_SCHEDULABLE";
    public static final String WO_FROZEN_THROUGH_CUTOFF = "WO_FROZEN_THROUGH_CUTOFF";
    public static final String WO_NO_ROUTING = "WO_NO_ROUTING";
    public static final String WO_NO_ALLOCATIONS = "WO_NO_ALLOCATIONS";

    // --- 主计划工序分配 ---
    public static final String ALLOC_NO_RESOURCE_SLOTS = "ALLOC_NO_RESOURCE_SLOTS";
    /** 时窗内无「不早于最早可行」槽位，回退全部可用槽并由软约束惩罚 */
    public static final String ALLOC_TIMING_FALLBACK = "ALLOC_TIMING_FALLBACK";
    /** 并行组成员可行槽无交集，已解除同槽约束 */
    public static final String ALLOC_PARALLEL_NO_COMMON_SLOT = "ALLOC_PARALLEL_NO_COMMON_SLOT";

    // --- 详细排程 ---
    public static final String WO_KITTING_SHORT = "WO_KITTING_SHORT";
    public static final String OP_MP_CONTRACT = "OP_MP_CONTRACT";
    public static final String OP_MP_TARGET_FALLBACK = "OP_MP_TARGET_FALLBACK";

    // --- 计数器键：主计划 ---
    public static final String MP_WORK_ORDERS_SCANNED = "workOrdersScanned";
    public static final String MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE = "workOrdersSkippedNotSchedulable";
    public static final String MP_WORK_ORDERS_SKIPPED_FROZEN = "workOrdersSkippedFrozen";
    public static final String MP_WORK_ORDERS_SKIPPED_NO_ROUTING = "workOrdersSkippedNoRouting";
    public static final String MP_WORK_ORDERS_WITH_ALLOCATIONS = "workOrdersWithAllocations";
    public static final String MP_ORDER_ALLOCATIONS_CANDIDATE = "orderAllocationsCandidate";
    public static final String MP_ORDER_ALLOCATIONS_REPLANNABLE = "orderAllocationsReplannable";
    public static final String MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS = "orderAllocationsDroppedNoSlots";
    public static final String MP_ORDER_ALLOCATIONS_TIMING_FALLBACK = "orderAllocationsTimingFallback";
    public static final String MP_ORDER_ALLOCATIONS_LOCKED = "orderAllocationsLocked";
    public static final String MP_TIME_SLOT_COUNT = "timeSlotCount";
    public static final String MP_BOM_DEPENDENCY_EDGE_COUNT = "bomDependencyEdgeCount";
    public static final String MP_INVENTORY_PRODUCT_COUNT = "inventoryProductCount";
    public static final String MP_PARALLEL_GROUPS = "parallelOperationGroups";
    public static final String MP_PARALLEL_ORPHANS = "parallelOperationOrphans";
    public static final String MP_PARALLEL_SLOT_INTERSECTIONS = "parallelSlotIntersections";
    public static final String MP_PARALLEL_SLOT_FALLBACKS = "parallelSlotIntersectionFallbacks";
    public static final String MP_OPERATION_PRECEDENCE_EDGES = "operationPrecedenceEdges";

    // --- 计数器键：详细排程 ---
    public static final String DS_WORK_ORDERS_SCANNED = "workOrdersScanned";
    public static final String DS_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE = "workOrdersSkippedNotSchedulable";
    public static final String DS_WORK_ORDERS_SKIPPED_NO_ROUTING = "workOrdersSkippedNoRouting";
    public static final String DS_WORK_ORDERS_INCLUDED = "workOrdersIncluded";
    public static final String DS_OPERATIONS_TOTAL = "operationsTotal";
    public static final String DS_OPERATIONS_KITTING_INELIGIBLE = "operationsKittingIneligible";
    public static final String DS_OPERATIONS_WITH_MP_CONTRACT = "operationsWithMpContract";
    public static final String DS_OPERATIONS_MP_TARGET_FALLBACK = "operationsMpTargetFallback";
    public static final String DS_SCHEDULE_LINES_TOTAL = "scheduleLinesTotal";
    public static final String DS_SCHEDULE_LINES_OPENED = "scheduleLinesOpened";
    public static final String DS_MP_CONTRACTS_LOADED = "masterPlanContractsLoaded";
    public static final String DS_INVENTORY_PRODUCT_COUNT = "inventoryProductCount";
    public static final String DS_PARALLEL_PAIRED_OPS = "parallelPairedOperations";
    public static final String DS_PARALLEL_ORPHAN_OPS = "parallelOrphanOperations";
    public static final String DS_CONTINUOUS_PRODUCTION_OPS = "continuousProductionOperations";
}
