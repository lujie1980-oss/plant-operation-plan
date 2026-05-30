package com.plantops.scenario;



import com.plantops.masterdata.BusinessRuleScopeService;

import com.plantops.masterdata.BusinessRuleTypeIds;

import com.plantops.persistence.entity.ParallelOperationRuleEntity;

import com.plantops.persistence.entity.ProductionLineEntity;

import com.plantops.scenario.planning.diagnostics.MasterPlanPlanningDiagnosticsCollector;

import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;

import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;

import com.plantops.solver.masterplan.OrderAllocation;

import com.plantops.solver.masterplan.TimeSlot;

import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



/**

 * 主计划并行工序：同一销售订单行上配对料号的首道工序分配标记同组，约束同槽开工。

 * 规则来源与 S05 {@link ParallelOperationBindingService} 一致（{@link ParallelOperationRuleEntity}）。

 */

@ApplicationScoped

public class MasterPlanParallelBindingService {



    @Inject

    BusinessRuleScopeService ruleScope;



    public MasterPlanParallelBindingResult applyBindings(

            List<OrderAllocation> allocations,

            List<TimeSlot> allSlots,

            MasterPlanCapacityOverlay overlay,

            WorkOrderTimingBoundsContext timingBounds,

            MasterPlanPlanningDiagnosticsCollector diag) {

        if (allocations == null || allocations.isEmpty()) {

            return new MasterPlanParallelBindingResult(0, 0, 0, 0);

        }

        if (!ruleScope.isMasterPlanEnabled(BusinessRuleTypeIds.PARALLEL_OPERATIONS)) {

            return new MasterPlanParallelBindingResult(0, 0, 0, 0);

        }

        MasterPlanCapacityOverlay effectiveOverlay = overlay != null ? overlay : MasterPlanCapacityOverlay.empty();

        Map<String, List<OrderAllocation>> byOrderLine = indexByOrderLine(allocations);

        int groups = 0;

        int orphans = 0;

        for (ParallelOperationRuleEntity rule : ParallelOperationRuleEntity.listInWorkspace()) {

            ProductionLineEntity line = ProductionLineEntity.findByLineId(rule.lineId);

            if (line == null || line.resourceId == null || line.resourceId.isBlank()) {

                continue;

            }

            String resourceId = line.resourceId;

            for (Map.Entry<String, List<OrderAllocation>> entry : byOrderLine.entrySet()) {

                OrderAllocation first = findLeadAllocation(entry.getValue(), rule.firstProductCode, resourceId);

                OrderAllocation second = findLeadAllocation(entry.getValue(), rule.secondProductCode, resourceId);

                if (first != null && second != null) {

                    String groupId = "MPP-" + rule.id + "-" + entry.getKey() + "-" + rule.lineId;

                    int pairedDuration = Math.max(first.getDurationMinutes(), second.getDurationMinutes());

                    linkPair(first, second, groupId, rule.lineId, pairedDuration);

                    groups++;

                } else {
                    if (first != null && markOrphan(first, rule)) {
                        orphans++;
                    }
                    if (second != null && markOrphan(second, rule)) {
                        orphans++;
                    }
                }

            }

        }



        int[] intersectionStats = MasterPlanParallelSlotSupport.intersectParallelGroupSlots(allocations, diag);

        int slotIntersectionsApplied = intersectionStats[0];

        int slotIntersectionFallbacks = intersectionStats[1];



        for (OrderAllocation allocation : allocations) {

            if (!allocation.isParallelOrphan()) {

                continue;

            }

            MasterPlanParallelSlotSupport.expandOrphanEligibleSlots(allocation, allSlots, effectiveOverlay);

            refineOrphanEligibleSlots(allocation, timingBounds, diag);

        }



        return new MasterPlanParallelBindingResult(

                groups,

                orphans,

                slotIntersectionsApplied,

                slotIntersectionFallbacks);

    }



    private static void linkPair(

            OrderAllocation first,

            OrderAllocation second,

            String groupId,

            String lineId,

            int pairedDuration) {

        first.setParallelGroupId(groupId);

        second.setParallelGroupId(groupId);

        first.setParallelOrphan(false);

        second.setParallelOrphan(false);

        first.setDesignatedLineId(lineId);

        second.setDesignatedLineId(lineId);

        first.setDurationMinutes(pairedDuration);

        second.setDurationMinutes(pairedDuration);

    }



    private static boolean markOrphan(OrderAllocation allocation, ParallelOperationRuleEntity rule) {

        if (allocation.getParallelGroupId() != null) {

            return false;

        }

        allocation.setParallelOrphan(true);

        allocation.setDesignatedLineId(rule.lineId);

        allocation.setAllowedResourceIds(

                MasterPlanParallelSlotSupport.resolveAllowedResourceIds(allocation.getProductCode()));

        return true;

    }



    private static void refineOrphanEligibleSlots(

            OrderAllocation orphan,

            WorkOrderTimingBoundsContext timingBounds,

            MasterPlanPlanningDiagnosticsCollector diag) {

        List<TimeSlot> eligible = orphan.getEligibleTimeSlots();

        if (eligible == null || eligible.isEmpty() || timingBounds == null) {

            return;

        }

        List<TimeSlot> feasible = eligible.stream()

                .filter(s -> timingBounds.slotAllowed(orphan.getWorkOrderNo(), s))

                .toList();

        if (feasible.isEmpty()) {

            diag.recordWarn(

                    PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK,

                    orphan.getWorkOrderNo(),

                    orphan.getId(),

                    "并行孤儿工序无「不早于最早可行」槽位，回退全部 "

                            + eligible.size()

                            + " 个扩展槽位并由软约束惩罚");

            return;

        }

        orphan.setEligibleTimeSlots(feasible);

    }



    static OrderAllocation findLeadAllocation(

            List<OrderAllocation> candidates,

            String productCode,

            String resourceId) {

        return candidates.stream()

                .filter(a -> productCode.equals(a.getProductCode()))

                .filter(a -> canUseResource(a, resourceId))

                .min(Comparator

                        .comparingInt(OrderAllocation::getOperationSeq)

                        .thenComparingInt(OrderAllocation::getSegmentIndex))

                .orElse(null);

    }



    private static boolean canUseResource(OrderAllocation allocation, String resourceId) {

        if (resourceId == null) {

            return false;

        }

        if (resourceId.equals(allocation.getResourceId())) {

            return true;

        }

        List<String> allowed = allocation.getAllowedResourceIds();

        return allowed != null && allowed.contains(resourceId);

    }



    private static Map<String, List<OrderAllocation>> indexByOrderLine(List<OrderAllocation> allocations) {

        Map<String, List<OrderAllocation>> index = new HashMap<>();

        for (OrderAllocation allocation : allocations) {

            if (allocation.getSalesOrderNo() == null) {

                continue;

            }

            String key = allocation.getSalesOrderNo() + "#" + allocation.getSalesOrderLineNo();

            index.computeIfAbsent(key, k -> new ArrayList<>()).add(allocation);

        }

        return index;

    }

}


