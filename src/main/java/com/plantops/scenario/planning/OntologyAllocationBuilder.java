package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从本体 {@link Operation} + OOSR 展开 {@link OrderAllocation}（含 FINITE_CAPACITY 拆段）。 */
public final class OntologyAllocationBuilder {

    private static final int DEFAULT_SHIFT_MINUTES = 480;

    private OntologyAllocationBuilder() {
    }

    public static List<OrderAllocation> buildForGraph(
            OntologyGraph graph,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay) {
        return buildForGraph(graph, slots, capacityConstrained, timingBounds, overlay, 1.0);
    }

    public static List<OrderAllocation> buildForGraph(
            OntologyGraph graph,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay,
            double demandScale) {
        List<OrderAllocation> out = new ArrayList<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            out.addAll(buildForSupplyOrder(
                    graph,
                    supplyOrder,
                    slots,
                    capacityConstrained,
                    false,
                    5,
                    timingBounds,
                    overlay,
                    demandScale));
        }
        return out;
    }

    public static List<OrderAllocation> buildForSupplyOrder(
            OntologyGraph graph,
            SupplyOrder supplyOrder,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            boolean locked,
            int priority,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay) {
        return buildForSupplyOrder(
                graph, supplyOrder, slots, capacityConstrained, locked, priority, timingBounds, overlay, 1.0);
    }

    public static List<OrderAllocation> buildForSupplyOrder(
            OntologyGraph graph,
            SupplyOrder supplyOrder,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            boolean locked,
            int priority,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay,
            double demandScale) {
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrder.getId());
        if (operations.isEmpty()) {
            return List.of();
        }
        List<OrderAllocation> out = new ArrayList<>();
        int globalSegment = 0;
        for (Operation operation : operations) {
            String primaryResourceId = OperationResourceBinding.primaryResourceId(graph, operation.getId());
            if (primaryResourceId == null || primaryResourceId.isBlank()) {
                continue;
            }
            int stepDuration = MasterPlanDemandScaler.scaleMinutes(
                    durationMinutesFromProduction(operation.getProductionDuration()),
                    demandScale);
            int maxCap = maxSlotCapacityForResource(primaryResourceId, slots);
            List<OrderAllocation> segments = capacityConstrained
                    ? splitOperationAllocations(
                            supplyOrder,
                            operation,
                            graph,
                            primaryResourceId,
                            stepDuration,
                            maxCap,
                            locked,
                            priority,
                            globalSegment)
                    : List.of(singleOperationAllocation(
                            supplyOrder,
                            operation,
                            graph,
                            primaryResourceId,
                            stepDuration,
                            locked,
                            priority,
                            allocationIdForOperationSegment(
                                    supplyOrder.getId(), operation.getRoutingSequenceNo(), 0),
                            globalSegment));
            if (segments.isEmpty()) {
                continue;
            }
            globalSegment += segments.size();
            out.addAll(segments);
        }
        if (!out.isEmpty()) {
            out.get(out.size() - 1).setLastSegment(true);
        }
        return applyEligibleTimeSlots(out, slots, overlay, timingBounds);
    }

    static void applyParallelGroupId(OrderAllocation allocation, Operation operation, int segmentIndex) {
        String base = operation.getParallelGroupId();
        if (base != null && !base.isBlank()) {
            allocation.setParallelGroupId(base + "#S" + segmentIndex);
        }
    }

    static int durationMinutesFromProduction(long productionDurationSeconds) {
        if (productionDurationSeconds <= 0) {
            return 1;
        }
        return (int) Math.max(1, (productionDurationSeconds + 59) / 60);
    }

    private static OrderAllocation singleOperationAllocation(
            SupplyOrder supplyOrder,
            Operation operation,
            OntologyGraph graph,
            String primaryResourceId,
            int duration,
            boolean locked,
            int priority,
            String planningId,
            int segmentIndex) {
        OrderAllocation allocation = new OrderAllocation();
        allocation.setId(planningId);
        allocation.setWorkOrderNo(supplyOrder.getId());
        allocation.setProductCode(supplyOrder.getProductCode());
        allocation.setResourceId(primaryResourceId);
        allocation.setAllowedResourceIds(OperationResourceBinding.allowedResourceIds(graph, operation.getId()));
        allocation.setOperationName(operation.getOperationName());
        allocation.setOperationSeq(operation.getRoutingSequenceNo());
        allocation.setDueDate(supplyOrder.getNeedDate());
        allocation.setPriority(priority);
        allocation.setDurationMinutes(Math.max(duration, 1));
        allocation.setWorkOrderQuantity(BigDecimal.valueOf(supplyOrder.getQuantity()));
        allocation.setSegmentIndex(segmentIndex);
        allocation.setLastSegment(false);
        allocation.setLocked(locked || operation.isLocked());
        applyParallelGroupId(allocation, operation, segmentIndex);
        return allocation;
    }

    private static List<OrderAllocation> splitOperationAllocations(
            SupplyOrder supplyOrder,
            Operation operation,
            OntologyGraph graph,
            String primaryResourceId,
            int totalDuration,
            int maxSlotCapacity,
            boolean locked,
            int priority,
            int segmentStart) {
        int cap = Math.max(1, maxSlotCapacity);
        int remaining = Math.max(1, totalDuration);
        List<OrderAllocation> segments = new ArrayList<>();
        int seg = 0;
        while (remaining > 0) {
            int chunk = Math.min(remaining, cap);
            String planningId = allocationIdForOperationSegment(
                    supplyOrder.getId(), operation.getRoutingSequenceNo(), seg);
            OrderAllocation allocation = singleOperationAllocation(
                    supplyOrder,
                    operation,
                    graph,
                    primaryResourceId,
                    chunk,
                    locked,
                    priority,
                    planningId,
                    segmentStart + seg);
            allocation.setDurationMinutes(chunk);
            segments.add(allocation);
            remaining -= chunk;
            seg++;
        }
        return segments;
    }

    static String allocationIdForOperationSegment(
            String supplyOrderId,
            int routingSequenceNo,
            int segmentIndex) {
        return supplyOrderId + "@OP" + routingSequenceNo + "_0#" + segmentIndex;
    }

    private static int maxSlotCapacityForResource(String resourceId, List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .mapToInt(TimeSlot::getCapacityMinutes)
                .max()
                .orElse(DEFAULT_SHIFT_MINUTES);
    }

    private static List<OrderAllocation> applyEligibleTimeSlots(
            List<OrderAllocation> allocations,
            List<TimeSlot> slots,
            MasterPlanCapacityOverlay overlay,
            WorkOrderTimingBoundsContext timingBounds) {
        MasterPlanCapacityOverlay effectiveOverlay = overlay != null
                ? overlay
                : MasterPlanCapacityOverlay.empty();
        WorkOrderTimingBoundsContext effectiveBounds = timingBounds != null
                ? timingBounds
                : new WorkOrderTimingBoundsContext(java.util.Map.of());
        List<OrderAllocation> replannable = new ArrayList<>();
        for (OrderAllocation allocation : allocations) {
            String rid = allocation.getResourceId();
            Set<String> eligibleResources = new LinkedHashSet<>();
            if (allocation.getAllowedResourceIds() != null && !allocation.getAllowedResourceIds().isEmpty()) {
                eligibleResources.addAll(allocation.getAllowedResourceIds());
            } else if (rid != null && !rid.isBlank()) {
                eligibleResources.add(rid);
            }
            List<TimeSlot> base = slots.stream()
                    .filter(s -> eligibleResources.contains(s.getResourceId()))
                    .filter(effectiveOverlay::isSlotEligibleForReplan)
                    .toList();
            if (base.isEmpty()) {
                continue;
            }
            List<TimeSlot> feasible = base.stream()
                    .filter(s -> effectiveBounds.slotAllowed(allocation.getWorkOrderNo(), s))
                    .toList();
            allocation.setEligibleTimeSlots(feasible.isEmpty() ? base : feasible);
            replannable.add(allocation);
        }
        return replannable;
    }
}
