package com.plantops.solver.masterplan;

/**
 * 主计划工序先后：后继分配槽位 index 不得早于前驱（通常为前道工序末段 → 后道工序首段）。
 */
public record OperationPrecedenceEdge(String predecessorAllocationId, String successorAllocationId) {
}
