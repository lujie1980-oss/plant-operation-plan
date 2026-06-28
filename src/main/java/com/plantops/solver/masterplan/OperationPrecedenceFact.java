package com.plantops.solver.masterplan;

/**
 * 同工单内工序串行：后继工序开工不得早于前驱工序全部机台子任务完工（并行拆分取 max end）。
 */
public record OperationPrecedenceFact(
        String workOrderNo,
        int predecessorOperationSeq,
        int successorOperationSeq) {
}
