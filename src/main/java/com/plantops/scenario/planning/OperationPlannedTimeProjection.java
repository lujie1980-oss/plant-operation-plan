package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationTimeAnchor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将主计划求解结果回写到 {@link Operation#plannedStartTotal} / {@link Operation#plannedEndTotal}。 */
public final class OperationPlannedTimeProjection {

    private OperationPlannedTimeProjection() {
    }

    public static void apply(OntologyGraph graph, List<MasterPlanAllocationDto> allocations) {
        if (graph == null || allocations == null || allocations.isEmpty()) {
            return;
        }
        Map<String, List<MasterPlanAllocationDto>> byWorkOrder = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.workOrderNo() == null) {
                continue;
            }
            byWorkOrder.computeIfAbsent(allocation.workOrderNo(), k -> new ArrayList<>()).add(allocation);
        }
        for (Map.Entry<String, List<MasterPlanAllocationDto>> entry : byWorkOrder.entrySet()) {
            applyForSupplyOrder(graph, entry.getKey(), entry.getValue());
        }
    }

    private static void applyForSupplyOrder(
            OntologyGraph graph,
            String supplyOrderId,
            List<MasterPlanAllocationDto> allocations) {
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrderId);
        if (operations.isEmpty()) {
            return;
        }
        for (Operation operation : operations) {
            operation.clearPlannedTimes();
            List<MasterPlanAllocationDto> segments = segmentsForOperation(operation, allocations);
            if (segments.isEmpty()) {
                continue;
            }
            LocalDateTime plannedStart = null;
            LocalDateTime plannedEnd = null;
            for (MasterPlanAllocationDto segment : segments) {
                LocalDateTime start = resolveStart(segment);
                LocalDateTime end = resolveEnd(segment);
                if (start != null && (plannedStart == null || start.isBefore(plannedStart))) {
                    plannedStart = start;
                }
                if (end != null && (plannedEnd == null || end.isAfter(plannedEnd))) {
                    plannedEnd = end;
                }
            }
            operation.setPlannedStartTotal(plannedStart);
            operation.setPlannedEndTotal(plannedEnd);
        }
    }

    private static List<MasterPlanAllocationDto> segmentsForOperation(
            Operation operation,
            List<MasterPlanAllocationDto> allocations) {
        int opSeq = operation.getRoutingSequenceNo();
        return allocations.stream()
                .filter(a -> a.segmentIndex() >= 0)
                .filter(a -> matchesOperationSeq(a, opSeq))
                .toList();
    }

    private static boolean matchesOperationSeq(MasterPlanAllocationDto allocation, int routingSequenceNo) {
        String id = allocation.allocationId();
        if (id == null) {
            return false;
        }
        int marker = id.indexOf("@OP");
        int hash = id.indexOf('#', marker);
        if (marker < 0 || hash <= marker + 3) {
            return false;
        }
        try {
            int seq = Integer.parseInt(id.substring(marker + 3, hash).split("_")[0]);
            return seq == routingSequenceNo;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static LocalDateTime resolveStart(MasterPlanAllocationDto allocation) {
        if (allocation.plannedStartTs() != null) {
            return allocation.plannedStartTs();
        }
        LocalDate slotDate = allocation.slotDate();
        if (slotDate != null) {
            return slotDate.atTime(OperationTimeAnchor.WORKDAY_START);
        }
        return null;
    }

    private static LocalDateTime resolveEnd(MasterPlanAllocationDto allocation) {
        if (allocation.plannedEndTs() != null) {
            return allocation.plannedEndTs();
        }
        LocalDate slotDate = allocation.slotDate();
        if (slotDate != null) {
            return slotDate.atTime(OperationTimeAnchor.WORKDAY_END);
        }
        return null;
    }
}
