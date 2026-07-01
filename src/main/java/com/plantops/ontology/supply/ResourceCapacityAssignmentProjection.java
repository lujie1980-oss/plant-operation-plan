package com.plantops.ontology.supply;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** optimize 结果 → ENT-RCA 写回（TODO-22 R2）。 */
public final class ResourceCapacityAssignmentProjection {

    private ResourceCapacityAssignmentProjection() {
    }

    public static void apply(
            OntologyGraph graph,
            List<MasterPlanAllocationDto> allocations,
            PeriodIndex periodIndex) {
        if (graph == null || allocations == null || allocations.isEmpty() || periodIndex == null) {
            if (graph != null && (allocations == null || allocations.isEmpty())) {
                graph.replaceResourceCapacityAssignments(List.of());
            }
            return;
        }
        graph.replaceResourceCapacityAssignments(project(graph, allocations, periodIndex));
    }

    public static List<ResourceCapacityAssignment> project(
            OntologyGraph graph,
            List<MasterPlanAllocationDto> allocations,
            PeriodIndex periodIndex) {
        Map<String, Integer> operationTotalMinutes = operationTotalMinutesByOperationId(graph, allocations);
        List<ResourceCapacityAssignment> rcas = new ArrayList<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            ResourceCapacityAssignment rca = toRca(graph, allocation, periodIndex, operationTotalMinutes);
            if (rca != null) {
                rcas.add(rca);
            }
        }
        return List.copyOf(rcas);
    }

    private static ResourceCapacityAssignment toRca(
            OntologyGraph graph,
            MasterPlanAllocationDto allocation,
            PeriodIndex periodIndex,
            Map<String, Integer> operationTotalMinutes) {
        if (allocation == null
                || allocation.workOrderNo() == null
                || allocation.workOrderNo().isBlank()
                || allocation.resourceId() == null
                || allocation.resourceId().isBlank()
                || allocation.allocationId() == null) {
            return null;
        }
        Operation operation = resolveOperation(graph, allocation);
        if (operation == null) {
            return null;
        }
        String oosrId = OntologyIds.operationOnStandardResourceId(operation.getId(), allocation.resourceId());
        if (graph.operationOnStandardResource(oosrId) == null) {
            return null;
        }
        LocalDate plannedDate = resolvePlannedDate(allocation);
        if (plannedDate == null) {
            return null;
        }
        int seq = periodIndex.sequenceFor(plannedDate, allocation.shiftId());
        Period period = periodIndex.periodAt(seq);
        if (period == null || !period.isLeaf()) {
            return null;
        }
        String srpId = OntologyIds.srpId(allocation.resourceId(), seq);
        if (graph.srp(srpId) == null) {
            return null;
        }
        int assignedMinutes = Math.max(0, allocation.durationMinutes());
        int operationTotal = operationTotalMinutes.getOrDefault(operation.getId(), assignedMinutes);
        return new ResourceCapacityAssignment(
                "RCA-" + allocation.allocationId(),
                operation.getId(),
                oosrId,
                srpId,
                assignedMinutes,
                operationTotal,
                operation.isLocked(),
                operation.getParallelGroupId());
    }

    private static Map<String, Integer> operationTotalMinutesByOperationId(
            OntologyGraph graph,
            List<MasterPlanAllocationDto> allocations) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            Operation operation = resolveOperation(graph, allocation);
            if (operation == null) {
                continue;
            }
            totals.merge(operation.getId(), Math.max(0, allocation.durationMinutes()), Integer::sum);
        }
        return totals;
    }

    private static Operation resolveOperation(OntologyGraph graph, MasterPlanAllocationDto allocation) {
        List<Operation> operations = graph.operationsForSupplyOrder(allocation.workOrderNo());
        if (operations.isEmpty()) {
            return null;
        }
        int routingSequenceNo = parseRoutingSequenceNo(allocation.allocationId());
        if (routingSequenceNo > 0) {
            for (Operation operation : operations) {
                if (operation.getRoutingSequenceNo() == routingSequenceNo) {
                    return operation;
                }
            }
        }
        return operations.size() == 1 ? operations.get(0) : null;
    }

    static int parseRoutingSequenceNo(String allocationId) {
        if (allocationId == null) {
            return -1;
        }
        int marker = allocationId.indexOf("@OP");
        int hash = allocationId.indexOf('#', marker);
        if (marker < 0 || hash <= marker + 3) {
            return -1;
        }
        try {
            return Integer.parseInt(allocationId.substring(marker + 3, hash).split("_")[0]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static LocalDate resolvePlannedDate(MasterPlanAllocationDto allocation) {
        if (allocation.slotDate() != null) {
            return allocation.slotDate();
        }
        if (allocation.plannedEndTs() != null) {
            return allocation.plannedEndTs().toLocalDate();
        }
        if (allocation.plannedStartTs() != null) {
            return allocation.plannedStartTs().toLocalDate();
        }
        return null;
    }
}
