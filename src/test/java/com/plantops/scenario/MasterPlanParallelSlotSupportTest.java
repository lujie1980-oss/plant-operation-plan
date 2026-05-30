package com.plantops.scenario;

import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterPlanParallelSlotSupportTest {

    @Test
    void intersectEligibleSlotsKeepsCommonSlots() {
        TimeSlot shared = slot("S1", "RES-A", 1);
        TimeSlot onlyLeft = slot("S2", "RES-A", 2);
        TimeSlot onlyRight = slot("S3", "RES-B", 3);

        List<TimeSlot> intersection = MasterPlanParallelSlotSupport.intersectEligibleSlots(
                List.of(shared, onlyLeft),
                List.of(shared, onlyRight));

        assertEquals(1, intersection.size());
        assertEquals("S1", intersection.get(0).getId());
    }

    @Test
    void intersectParallelGroupSlotsAppliesSharedEligible() {
        TimeSlot shared = slot("S1", "RES-A", 1);
        TimeSlot onlyFirst = slot("S2", "RES-A", 2);
        OrderAllocation first = grouped("G1", List.of(shared, onlyFirst));
        OrderAllocation second = grouped("G1", List.of(shared));

        int[] stats = MasterPlanParallelSlotSupport.intersectParallelGroupSlots(
                List.of(first, second), null);

        assertEquals(1, stats[0]);
        assertEquals(0, stats[1]);
        assertEquals(1, first.getEligibleTimeSlots().size());
        assertEquals("S1", first.getEligibleTimeSlots().get(0).getId());
        assertEquals(first.getEligibleTimeSlots(), second.getEligibleTimeSlots());
    }

    @Test
    void intersectParallelGroupSlotsClearsGroupWhenNoCommonSlot() {
        OrderAllocation first = grouped("G1", List.of(slot("S1", "RES-A", 1)));
        OrderAllocation second = grouped("G1", List.of(slot("S2", "RES-B", 2)));

        int[] stats = MasterPlanParallelSlotSupport.intersectParallelGroupSlots(
                List.of(first, second), null);

        assertEquals(0, stats[0]);
        assertEquals(1, stats[1]);
        assertNull(first.getParallelGroupId());
        assertNull(second.getParallelGroupId());
    }

    @Test
    void expandOrphanEligibleSlotsMergesAllowedResources() {
        OrderAllocation orphan = new OrderAllocation();
        orphan.setParallelOrphan(true);
        orphan.setAllowedResourceIds(List.of("RES-A", "RES-B"));
        orphan.setEligibleTimeSlots(List.of(slot("S1", "RES-A", 1)));

        MasterPlanParallelSlotSupport.expandOrphanEligibleSlots(
                orphan,
                List.of(
                        slot("S1", "RES-A", 1),
                        slot("S2", "RES-B", 2),
                        slot("S3", "RES-C", 3)),
                MasterPlanCapacityOverlay.empty());

        assertEquals(2, orphan.getEligibleTimeSlots().size());
        assertTrue(orphan.getEligibleTimeSlots().stream().anyMatch(s -> "S2".equals(s.getId())));
    }

    private static OrderAllocation grouped(String groupId, List<TimeSlot> eligible) {
        OrderAllocation allocation = new OrderAllocation();
        allocation.setId("A-" + groupId);
        allocation.setParallelGroupId(groupId);
        allocation.setEligibleTimeSlots(eligible);
        return allocation;
    }

    private static TimeSlot slot(String id, String resourceId, int index) {
        return new TimeSlot(id, index, LocalDate.now(), null, resourceId, 480);
    }
}
