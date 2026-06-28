package com.plantops.ontology.supply;

import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.ontology.OntologyGraph;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序时间窗推导（M3 简化 fallback + B.4 {@link OperationTimingBridge} 桥接入口）。
 */
public final class OperationTimeWindowDerivations {

    private OperationTimeWindowDerivations() {
    }

    public static void recalculateFallback(
            List<Operation> operations,
            LocalDate planningStart,
            LocalDate needDate) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        LocalDateTime horizonStart = OperationTimeAnchor.horizonStart(planningStart);
        LocalDateTime needEnd = OperationTimeAnchor.needDateEnd(
                needDate, planningStart != null ? planningStart : LocalDate.now());

        for (Operation operation : operations) {
            operation.clearPlannedTimes();
            long elapsed = operation.totalElapsedSeconds();
            operation.setEarliestPossibleStartOwn(horizonStart);
            operation.setEarliestPossibleEndOwn(OperationTimeAnchor.plusElapsed(horizonStart, elapsed));
        }

        LocalDateTime chainStart = horizonStart;
        for (Operation operation : operations) {
            long elapsed = operation.totalElapsedSeconds();
            operation.setEarliestPossibleStartTotal(chainStart);
            LocalDateTime chainEnd = OperationTimeAnchor.plusElapsed(chainStart, elapsed);
            operation.setEarliestPossibleEndTotal(chainEnd);
            chainStart = chainEnd;
        }

        LocalDateTime latestEnd = needEnd;
        for (int i = operations.size() - 1; i >= 0; i--) {
            Operation operation = operations.get(i);
            long elapsed = operation.totalElapsedSeconds();
            operation.setLatestDesiredEnd(latestEnd);
            operation.setLatestDesiredStart(OperationTimeAnchor.minusElapsed(latestEnd, elapsed));
            latestEnd = operation.getLatestDesiredStart();
        }

        for (Operation operation : operations) {
            LocalDateTime earliest = operation.getEarliestPossibleStartTotal();
            LocalDateTime latest = operation.getLatestDesiredEnd();
            operation.setInfeasible(earliest != null && latest != null && earliest.isAfter(latest));
        }
    }

    public static void recalculate(
            OntologyGraph graph,
            String supplyOrderId,
            LocalDate planningStart,
            WorkOrderTimingWindowDto window) {
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrderId);
        if (supplyOrder == null || operations.isEmpty()) {
            return;
        }
        OperationTimingBridge.applyWorkOrderWindow(
                operations,
                window,
                planningStart,
                supplyOrder.getNeedDate());
    }

    public static void recalculateLatestDesired(
            OntologyGraph graph,
            String supplyOrderId,
            LocalDate planningStart,
            WorkOrderTimingWindowDto window) {
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrderId);
        if (supplyOrder == null || operations.isEmpty()) {
            return;
        }
        OperationTimingBridge.recalculateLatestDesired(
                operations, planningStart, supplyOrder.getNeedDate(), window);
    }
}
