package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从本体 {@link Operation} + OOSR 展开 {@link ResourceCapacityAssignment}（多机台并行拆分 + 跨天日段）。 */
public final class ResourceCapacityAssignmentBuilder {

    private static final int DEFAULT_SHIFT_MINUTES = 480;

    private ResourceCapacityAssignmentBuilder() {
    }

    public record BuildResult(
            List<ResourceCapacityAssignment> assignments,
            List<OperationPrecedenceFact> operationPrecedenceFacts) {
    }

    public static BuildResult buildForGraph(
            OntologyGraph graph,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay) {
        return buildForGraph(graph, slots, capacityConstrained, timingBounds, overlay, null, 1.0);
    }

    public static BuildResult buildForGraph(
            OntologyGraph graph,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay,
            double demandScale) {
        return buildForGraph(graph, slots, capacityConstrained, timingBounds, overlay, null, demandScale);
    }

    public static BuildResult buildForGraph(
            OntologyGraph graph,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay,
            Set<String> scopedWorkOrderNos,
            double demandScale) {
        List<ResourceCapacityAssignment> assignments = new ArrayList<>();
        List<OperationPrecedenceFact> precedenceFacts = new ArrayList<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            if (scopedWorkOrderNos != null
                    && !scopedWorkOrderNos.isEmpty()
                    && !scopedWorkOrderNos.contains(supplyOrder.getId())) {
                continue;
            }
            BuildResult woResult = buildForSupplyOrder(
                    graph,
                    supplyOrder,
                    slots,
                    capacityConstrained,
                    false,
                    5,
                    timingBounds,
                    overlay,
                    demandScale);
            assignments.addAll(woResult.assignments());
            precedenceFacts.addAll(woResult.operationPrecedenceFacts());
        }
        return new BuildResult(assignments, dedupePrecedence(precedenceFacts));
    }

    public static BuildResult buildForSupplyOrder(
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

    public static BuildResult buildForSupplyOrder(
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
            return new BuildResult(List.of(), List.of());
        }
        List<Operation> ordered = operations.stream()
                .sorted(Comparator.comparingInt(Operation::getRoutingSequenceNo))
                .toList();

        List<ResourceCapacityAssignment> out = new ArrayList<>();
        List<OperationPrecedenceFact> precedenceFacts = new ArrayList<>();
        for (Operation operation : ordered) {
            List<OperationOnStandardResource> bindings = graph.operationsOnStandardResourceFor(operation.getId()).stream()
                    .sorted(OperationResourceBinding.byPriority())
                    .toList();
            if (bindings.isEmpty()) {
                continue;
            }
            int totalMinutes = MasterPlanDemandScaler.scaleMinutes(
                    OntologyAllocationBuilder.durationMinutesFromProduction(operation.getProductionDuration()),
                    demandScale);

            List<OperationOnStandardResource> eligibleBindings = bindings.stream()
                    .filter(b -> b.getStandardResourceId() != null && !b.getStandardResourceId().isBlank())
                    .toList();
            if (eligibleBindings.isEmpty()) {
                continue;
            }
            int daySegments = daySegmentsForOperation(
                    totalMinutes,
                    parallelDayCapacityForResources(
                            eligibleBindings.stream().map(OperationOnStandardResource::getStandardResourceId).toList(),
                            slots),
                    capacityConstrained);

            for (OperationOnStandardResource binding : eligibleBindings) {
                String resourceId = binding.getStandardResourceId();
                int maxDayCap = maxSlotCapacityForResource(resourceId, slots);
                for (int daySeg = 0; daySeg < daySegments; daySeg++) {
                    ResourceCapacityAssignment assignment = baseAssignment(
                            supplyOrder,
                            operation,
                            resourceId,
                            binding.getResourcePriority(),
                            totalMinutes,
                            maxDayCap,
                            daySeg,
                            locked,
                            priority);
                    assignment.setEligibleTimeSlots(eligibleSlotsForResource(
                            supplyOrder.getId(),
                            resourceId,
                            slots,
                            overlay,
                            timingBounds));
                    if (!assignment.getEligibleTimeSlots().isEmpty()) {
                        out.add(assignment);
                    }
                }
            }
        }

        for (int i = 0; i < ordered.size() - 1; i++) {
            precedenceFacts.add(new OperationPrecedenceFact(
                    supplyOrder.getId(),
                    ordered.get(i).getRoutingSequenceNo(),
                    ordered.get(i + 1).getRoutingSequenceNo()));
        }

        return new BuildResult(out, precedenceFacts);
    }

    /**
     * DB 主计划路径：从 {@link WorkOrderEntity} + {@link ProductRoutingSteps} 展开多机台候选。
     */
    public static BuildResult buildForWorkOrder(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            List<ProductRoutingSteps.Operation> operations,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            boolean locked,
            int priority,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay) {
        return buildForWorkOrder(
                wo, scheduleCtx, operations, slots, capacityConstrained, locked, priority, timingBounds, overlay, 1.0);
    }

    public static BuildResult buildForWorkOrder(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            List<ProductRoutingSteps.Operation> operations,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            boolean locked,
            int priority,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanCapacityOverlay overlay,
            double demandScale) {
        if (wo == null || operations == null || operations.isEmpty()) {
            return new BuildResult(List.of(), List.of());
        }
        List<ProductRoutingSteps.Operation> ordered = operations.stream()
                .sorted(Comparator.comparingInt(ProductRoutingSteps.Operation::sequenceNo))
                .toList();

        List<ResourceCapacityAssignment> out = new ArrayList<>();
        List<OperationPrecedenceFact> precedenceFacts = new ArrayList<>();
        for (ProductRoutingSteps.Operation operation : ordered) {
            if (operation.resourceOptions() == null || operation.resourceOptions().isEmpty()) {
                continue;
            }
            int totalMinutes = MasterPlanDemandScaler.scaleMinutes(
                    ProductRoutingSteps.durationMinutesForOperation(operation, wo.quantity),
                    demandScale);

            List<ProductRoutingSteps.ResourceOption> bindings = operation.resourceOptions().stream()
                    .sorted(Comparator.comparingInt(o -> o.resourcePriority() != null ? o.resourcePriority() : 1))
                    .filter(o -> o.resourceId() != null && !o.resourceId().isBlank())
                    .toList();
            if (bindings.isEmpty()) {
                continue;
            }
            int daySegments = daySegmentsForOperation(
                    totalMinutes,
                    parallelDayCapacityForResources(
                            bindings.stream().map(ProductRoutingSteps.ResourceOption::resourceId).toList(),
                            slots),
                    capacityConstrained);

            for (ProductRoutingSteps.ResourceOption binding : bindings) {
                String resourceId = binding.resourceId();
                int maxDayCap = maxSlotCapacityForResource(resourceId, slots);
                for (int daySeg = 0; daySeg < daySegments; daySeg++) {
                    ResourceCapacityAssignment assignment = baseAssignmentFromWorkOrder(
                            wo,
                            scheduleCtx,
                            operation,
                            resourceId,
                            binding.resourcePriority() != null ? binding.resourcePriority() : 1,
                            totalMinutes,
                            maxDayCap,
                            daySeg,
                            locked,
                            priority);
                    assignment.setEligibleTimeSlots(eligibleSlotsForResource(
                            wo.workOrderNo,
                            resourceId,
                            slots,
                            overlay,
                            timingBounds));
                    if (!assignment.getEligibleTimeSlots().isEmpty()) {
                        out.add(assignment);
                    }
                }
            }
        }

        for (int i = 0; i < ordered.size() - 1; i++) {
            precedenceFacts.add(new OperationPrecedenceFact(
                    wo.workOrderNo,
                    ordered.get(i).sequenceNo(),
                    ordered.get(i + 1).sequenceNo()));
        }
        return new BuildResult(out, precedenceFacts);
    }

    private static ResourceCapacityAssignment baseAssignmentFromWorkOrder(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            ProductRoutingSteps.Operation operation,
            String resourceId,
            int resourcePriority,
            int operationTotalMinutes,
            int slotCapacityMinutes,
            int daySegmentIndex,
            boolean locked,
            int priority) {
        ResourceCapacityAssignment assignment = new ResourceCapacityAssignment();
        assignment.setId(ResourceCapacityAssignment.allocationId(
                wo.workOrderNo,
                operation.sequenceNo(),
                resourceId,
                daySegmentIndex));
        assignment.setWorkOrderNo(wo.workOrderNo);
        assignment.setOperationId(null);
        assignment.setOperationSeq(operation.sequenceNo());
        assignment.setOperationKey(ResourceCapacityAssignment.operationKey(wo.workOrderNo, operation.sequenceNo()));
        assignment.setDaySegmentIndex(daySegmentIndex);
        assignment.setResourceId(resourceId);
        assignment.setResourcePriority(resourcePriority);
        assignment.setProductCode(wo.productCode);
        assignment.setOperationName(operation.operationName());
        assignment.setOperationTotalMinutes(operationTotalMinutes);
        assignment.setSlotCapacityMinutes(Math.max(1, slotCapacityMinutes));
        assignment.setParentWorkOrderNo(wo.parentWorkOrderNo);
        assignment.setSalesOrderNo(scheduleCtx.salesOrderNo);
        assignment.setSalesOrderLineNo(scheduleCtx.salesOrderLineNo);
        assignment.setDueDate(scheduleCtx.dueDate);
        assignment.setPriority(priority);
        assignment.setWorkOrderQuantity(wo.quantity != null ? wo.quantity : BigDecimal.ZERO);
        assignment.setLocked(locked);
        return assignment;
    }

    public static void enrichFromWorkOrder(
            List<ResourceCapacityAssignment> assignments,
            WorkOrderEntity workOrder,
            WorkOrderScheduleContext scheduleCtx) {
        if (assignments == null || workOrder == null) {
            return;
        }
        for (ResourceCapacityAssignment assignment : assignments) {
            assignment.setParentWorkOrderNo(workOrder.parentWorkOrderNo);
            assignment.setSalesOrderNo(scheduleCtx.salesOrderNo);
            assignment.setSalesOrderLineNo(scheduleCtx.salesOrderLineNo);
        }
    }

    private static ResourceCapacityAssignment baseAssignment(
            SupplyOrder supplyOrder,
            Operation operation,
            String resourceId,
            int resourcePriority,
            int operationTotalMinutes,
            int slotCapacityMinutes,
            int daySegmentIndex,
            boolean locked,
            int priority) {
        ResourceCapacityAssignment assignment = new ResourceCapacityAssignment();
        assignment.setId(ResourceCapacityAssignment.allocationId(
                supplyOrder.getId(),
                operation.getRoutingSequenceNo(),
                resourceId,
                daySegmentIndex));
        assignment.setWorkOrderNo(supplyOrder.getId());
        assignment.setOperationId(operation.getId());
        assignment.setOperationSeq(operation.getRoutingSequenceNo());
        assignment.setOperationKey(ResourceCapacityAssignment.operationKey(
                supplyOrder.getId(), operation.getRoutingSequenceNo()));
        assignment.setDaySegmentIndex(daySegmentIndex);
        assignment.setResourceId(resourceId);
        assignment.setResourcePriority(OperationResourceBinding.defaultPriority(resourcePriority));
        assignment.setProductCode(supplyOrder.getProductCode());
        assignment.setOperationName(operation.getOperationName());
        assignment.setOperationTotalMinutes(operationTotalMinutes);
        assignment.setSlotCapacityMinutes(Math.max(1, slotCapacityMinutes));
        assignment.setDueDate(supplyOrder.getNeedDate());
        assignment.setPriority(priority);
        assignment.setWorkOrderQuantity(BigDecimal.valueOf(supplyOrder.getQuantity()));
        assignment.setLocked(locked || operation.isLocked());
        assignment.setOperationLatestDesiredEnd(operation.getLatestDesiredEnd());
        assignment.setOperationLatestDesiredStart(operation.getLatestDesiredStart());
        String parallelGroupId = operation.getParallelGroupId();
        if (parallelGroupId != null && !parallelGroupId.isBlank()) {
            assignment.setParallelGroupId(parallelGroupId + "#D" + daySegmentIndex);
        }
        return assignment;
    }

    /**
     * 日段数按工序总工时与<strong>全部候选机台日产能之和</strong>估算；
     * 多机台可并行分摊，避免每台机各自展开 ceil(total/单机产能) 的冗余占位行。
     */
    static int daySegmentsForOperation(
            int totalMinutes,
            int parallelDayCapacityMinutes,
            boolean capacityConstrained) {
        if (!capacityConstrained || totalMinutes <= 0) {
            return 1;
        }
        int effectiveParallel = Math.max(1, parallelDayCapacityMinutes);
        return Math.max(1, (int) Math.ceil((double) totalMinutes / effectiveParallel));
    }

    private static int parallelDayCapacityForResources(
            List<String> resourceIds,
            List<TimeSlot> slots) {
        int sum = 0;
        for (String resourceId : resourceIds) {
            if (resourceId == null || resourceId.isBlank()) {
                continue;
            }
            sum += maxSlotCapacityForResource(resourceId, slots);
        }
        return sum;
    }

    private static List<TimeSlot> eligibleSlotsForResource(
            String workOrderNo,
            String resourceId,
            List<TimeSlot> slots,
            MasterPlanCapacityOverlay overlay,
            WorkOrderTimingBoundsContext timingBounds) {
        MasterPlanCapacityOverlay effectiveOverlay = overlay != null
                ? overlay
                : MasterPlanCapacityOverlay.empty();
        WorkOrderTimingBoundsContext effectiveBounds = timingBounds != null
                ? timingBounds
                : WorkOrderTimingBoundsContext.empty();
        List<TimeSlot> base = slots.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .filter(effectiveOverlay::isSlotEligibleForReplan)
                .toList();
        if (base.isEmpty()) {
            return List.of();
        }
        List<TimeSlot> feasible = base.stream()
                .filter(s -> effectiveBounds.slotAllowed(workOrderNo, s))
                .toList();
        return feasible.isEmpty() ? base : feasible;
    }

    private static int maxSlotCapacityForResource(String resourceId, List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .mapToInt(TimeSlot::getCapacityMinutes)
                .max()
                .orElse(DEFAULT_SHIFT_MINUTES);
    }

    private static List<OperationPrecedenceFact> dedupePrecedence(List<OperationPrecedenceFact> facts) {
        Map<String, OperationPrecedenceFact> unique = new LinkedHashMap<>();
        for (OperationPrecedenceFact fact : facts) {
            unique.put(fact.workOrderNo() + ":" + fact.predecessorOperationSeq() + "->" + fact.successorOperationSeq(), fact);
        }
        return List.copyOf(unique.values());
    }
}
