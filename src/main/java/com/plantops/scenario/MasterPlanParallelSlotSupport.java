package com.plantops.scenario;

import com.plantops.scenario.planning.diagnostics.MasterPlanPlanningDiagnosticsCollector;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 主计划并行工序：可行槽交集 / 孤儿资源扩展。 */
final class MasterPlanParallelSlotSupport {

    private MasterPlanParallelSlotSupport() {
    }

    static List<String> resolveAllowedResourceIds(String productCode) {
        LinkedHashSet<String> resourceIds = new LinkedHashSet<>();
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }
        for (ProductResourceEntity row : ProductResourceEntity.findByProductOrdered(productCode)) {
            if (row.resourceId != null && !row.resourceId.isBlank()) {
                resourceIds.add(row.resourceId);
            }
        }
        return List.copyOf(resourceIds);
    }

    static List<TimeSlot> intersectEligibleSlots(List<TimeSlot> left, List<TimeSlot> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return List.of();
        }
        Set<String> rightIds = right.stream().map(TimeSlot::getId).collect(Collectors.toSet());
        List<TimeSlot> intersection = new ArrayList<>();
        for (TimeSlot slot : left) {
            if (rightIds.contains(slot.getId())) {
                intersection.add(slot);
            }
        }
        return List.copyOf(intersection);
    }

    static List<TimeSlot> mergeEligibleSlots(List<TimeSlot> current, List<TimeSlot> extra) {
        if (extra == null || extra.isEmpty()) {
            return current != null ? List.copyOf(current) : List.of();
        }
        LinkedHashMap<String, TimeSlot> merged = new LinkedHashMap<>();
        if (current != null) {
            for (TimeSlot slot : current) {
                merged.put(slot.getId(), slot);
            }
        }
        for (TimeSlot slot : extra) {
            merged.putIfAbsent(slot.getId(), slot);
        }
        return List.copyOf(merged.values());
    }

    static List<TimeSlot> slotsForResources(
            List<TimeSlot> allSlots,
            List<String> resourceIds,
            MasterPlanCapacityOverlay overlay) {
        if (allSlots == null || resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new LinkedHashSet<>(resourceIds);
        return allSlots.stream()
                .filter(s -> allowed.contains(s.getResourceId()))
                .filter(overlay::isSlotEligibleForReplan)
                .toList();
    }

    /**
     * 并行组内成对分配取 eligible 交集；空交集时清除 groupId 并返回 [applied, fallbacks]。
     */
    static int[] intersectParallelGroupSlots(
            List<OrderAllocation> allocations,
            MasterPlanPlanningDiagnosticsCollector diag) {
        Map<String, List<OrderAllocation>> byGroup = new LinkedHashMap<>();
        for (OrderAllocation allocation : allocations) {
            String groupId = allocation.getParallelGroupId();
            if (groupId == null || groupId.isBlank()) {
                continue;
            }
            byGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(allocation);
        }
        int applied = 0;
        int fallbacks = 0;
        for (List<OrderAllocation> members : byGroup.values()) {
            if (members.size() < 2) {
                continue;
            }
            List<TimeSlot> shared = members.get(0).getEligibleTimeSlots();
            for (int i = 1; i < members.size(); i++) {
                shared = intersectEligibleSlots(shared, members.get(i).getEligibleTimeSlots());
            }
            if (shared.isEmpty()) {
                fallbacks++;
                String groupId = members.get(0).getParallelGroupId();
                for (OrderAllocation member : members) {
                    if (diag != null) {
                        diag.recordWarn(
                                PlanningDiagnosticCodes.ALLOC_PARALLEL_NO_COMMON_SLOT,
                                member.getWorkOrderNo(),
                                member.getId(),
                                "并行组 " + groupId + " 成员可行槽无交集，已解除同槽约束");
                    }
                    member.setParallelGroupId(null);
                }
                continue;
            }
            for (OrderAllocation member : members) {
                member.setEligibleTimeSlots(shared);
            }
            applied++;
        }
        return new int[]{applied, fallbacks};
    }

    static void expandOrphanEligibleSlots(
            OrderAllocation orphan,
            List<TimeSlot> allSlots,
            MasterPlanCapacityOverlay overlay) {
        if (orphan == null || !orphan.isParallelOrphan()) {
            return;
        }
        List<String> allowed = orphan.getAllowedResourceIds();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        List<TimeSlot> extra = slotsForResources(allSlots, allowed, overlay);
        orphan.setEligibleTimeSlots(mergeEligibleSlots(orphan.getEligibleTimeSlots(), extra));
    }
}
