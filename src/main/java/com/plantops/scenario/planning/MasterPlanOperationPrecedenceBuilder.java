package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从工单工序分配构建主计划工序先后边（串行路由）。 */
public final class MasterPlanOperationPrecedenceBuilder {

    private MasterPlanOperationPrecedenceBuilder() {
    }

    public static List<OperationPrecedenceEdge> buildSerialOperationEdges(List<OrderAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        Map<String, List<OrderAllocation>> byWorkOrder = new LinkedHashMap<>();
        for (OrderAllocation allocation : allocations) {
            if (allocation.getWorkOrderNo() == null) {
                continue;
            }
            byWorkOrder.computeIfAbsent(allocation.getWorkOrderNo(), k -> new ArrayList<>()).add(allocation);
        }
        List<OperationPrecedenceEdge> edges = new ArrayList<>();
        for (List<OrderAllocation> woAllocations : byWorkOrder.values()) {
            edges.addAll(edgesForWorkOrder(woAllocations));
        }
        return edges;
    }

    private static List<OperationPrecedenceEdge> edgesForWorkOrder(List<OrderAllocation> woAllocations) {
        Map<Integer, List<OrderAllocation>> byOperationSeq = new LinkedHashMap<>();
        for (OrderAllocation allocation : woAllocations) {
            byOperationSeq.computeIfAbsent(allocation.getOperationSeq(), k -> new ArrayList<>()).add(allocation);
        }
        List<Integer> opSeqs = byOperationSeq.keySet().stream().sorted().toList();
        if (opSeqs.size() < 2) {
            return List.of();
        }
        List<OperationPrecedenceEdge> edges = new ArrayList<>();
        for (int i = 0; i < opSeqs.size() - 1; i++) {
            OrderAllocation predecessor = lastSegment(byOperationSeq.get(opSeqs.get(i)));
            OrderAllocation successor = firstSegment(byOperationSeq.get(opSeqs.get(i + 1)));
            if (predecessor != null && successor != null) {
                edges.add(new OperationPrecedenceEdge(predecessor.getId(), successor.getId()));
            }
        }
        return edges;
    }

    private static OrderAllocation firstSegment(List<OrderAllocation> segments) {
        return segments.stream()
                .min(Comparator.comparingInt(OrderAllocation::getSegmentIndex))
                .orElse(null);
    }

    private static OrderAllocation lastSegment(List<OrderAllocation> segments) {
        return segments.stream()
                .max(Comparator.comparingInt(OrderAllocation::getSegmentIndex))
                .orElse(null);
    }
}
