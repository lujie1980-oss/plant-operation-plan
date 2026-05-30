package com.plantops.scenario.planning;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 从工单 + 工艺工序（按 sequenceNo 分组）构建 {@link OrderAllocation}（含 FINITE_CAPACITY 拆段）。 */
final class MasterPlanAllocationBuilder {

    private static final int DEFAULT_SHIFT_MINUTES = 480;

    private MasterPlanAllocationBuilder() {
    }

    static List<OrderAllocation> buildForWorkOrder(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            List<ProductRoutingSteps.Operation> operations,
            List<TimeSlot> slots,
            boolean capacityConstrained,
            boolean locked,
            BusinessRuleScopeService businessRuleScopeService) {
        int fallbackWorkOrderDuration = ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
        List<OrderAllocation> out = new ArrayList<>();
        int globalSegment = 0;
        for (int i = 0; i < operations.size(); i++) {
            ProductRoutingSteps.Operation operation = operations.get(i);
            String primaryResourceId = operation.primaryResourceId();
            if (primaryResourceId == null || primaryResourceId.isBlank()) {
                continue;
            }
            int stepDuration = ProductRoutingSteps.durationMinutesForOperation(operation, wo.quantity);
            int maxCap = maxSlotCapacityForResource(primaryResourceId, slots);
            List<OrderAllocation> segments = capacityConstrained
                    ? splitOperationAllocations(
                            wo,
                            scheduleCtx,
                            operation,
                            primaryResourceId,
                            stepDuration,
                            maxCap,
                            locked,
                            globalSegment,
                            businessRuleScopeService)
                    : List.of(singleOperationAllocation(
                            wo,
                            scheduleCtx,
                            operation,
                            primaryResourceId,
                            stepDuration,
                            locked,
                            allocationIdForOperationSegment(wo.workOrderNo, operation.sequenceNo(), 0),
                            globalSegment,
                            businessRuleScopeService));
            if (segments.isEmpty()) {
                continue;
            }
            globalSegment += segments.size();
            out.addAll(segments);
        }
        if (!out.isEmpty()) {
            out.get(out.size() - 1).setLastSegment(true);
        }
        return out;
    }

    private static OrderAllocation singleOperationAllocation(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            ProductRoutingSteps.Operation operation,
            String primaryResourceId,
            int duration,
            boolean locked,
            String planningId,
            int segmentIndex,
            BusinessRuleScopeService businessRuleScopeService) {
        OrderAllocation a = new OrderAllocation();
        a.setId(planningId);
        a.setWorkOrderNo(wo.workOrderNo);
        a.setParentWorkOrderNo(wo.parentWorkOrderNo);
        a.setSalesOrderNo(scheduleCtx.salesOrderNo);
        a.setSalesOrderLineNo(scheduleCtx.salesOrderLineNo);
        a.setProductCode(wo.productCode);
        a.setResourceId(primaryResourceId);
        a.setAllowedResourceIds(operation.allowedResourceIds());
        a.setOperationName(operation.operationName());
        a.setOperationSeq(operation.sequenceNo());
        boolean demandRules = businessRuleScopeService.isMasterPlanEnabled(
                BusinessRuleTypeIds.DEMAND_PRIORITY_RULES);
        a.setDueDate(scheduleCtx.dueDate);
        a.setPriority(demandRules ? scheduleCtx.priority : 5);
        a.setDurationMinutes(Math.max(duration, 1));
        a.setWorkOrderQuantity(wo.quantity != null ? wo.quantity : BigDecimal.ZERO);
        a.setSegmentIndex(segmentIndex);
        a.setLastSegment(false);
        a.setLocked(locked);
        return a;
    }

    private static List<OrderAllocation> splitOperationAllocations(
            WorkOrderEntity wo,
            WorkOrderScheduleContext scheduleCtx,
            ProductRoutingSteps.Operation operation,
            String primaryResourceId,
            int totalDuration,
            int maxSlotCapacity,
            boolean locked,
            int segmentStart,
            BusinessRuleScopeService businessRuleScopeService) {
        int cap = Math.max(1, maxSlotCapacity);
        int remaining = Math.max(1, totalDuration);
        List<OrderAllocation> segments = new ArrayList<>();
        int seg = 0;
        while (remaining > 0) {
            int chunk = Math.min(remaining, cap);
            String planningId = allocationIdForOperationSegment(
                    wo.workOrderNo, operation.sequenceNo(), seg);
            OrderAllocation a = singleOperationAllocation(
                    wo, scheduleCtx, operation, primaryResourceId, chunk, locked, planningId, segmentStart + seg,
                    businessRuleScopeService);
            a.setDurationMinutes(chunk);
            segments.add(a);
            remaining -= chunk;
            seg++;
        }
        return segments;
    }

    private static String allocationIdForOperationSegment(
            String workOrderNo,
            int operationSeq,
            int segmentIndex) {
        return workOrderNo + "@OP" + operationSeq + "_0#" + segmentIndex;
    }

    private static int maxSlotCapacityForResource(String resourceId, List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .mapToInt(TimeSlot::getCapacityMinutes)
                .max()
                .orElse(DEFAULT_SHIFT_MINUTES);
    }
}
