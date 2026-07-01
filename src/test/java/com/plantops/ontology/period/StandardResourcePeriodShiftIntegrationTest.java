package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.ResourceCapacityAssignmentProjection;
import com.plantops.ontology.supply.ResourceCapacityAssignmentRollup;
import com.plantops.api.dto.MasterPlanAllocationDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StandardResourcePeriodShiftIntegrationTest {

    private static final String RESOURCE_ID = "RES-SHIFT-S2";
    private static final String SUPPLY_ORDER_ID = "WO-SHIFT-S2";
    private static final String OPERATION_ID = OntologyIds.operationId(SUPPLY_ORDER_ID, 1);
    private static final String OOSR_ID = OntologyIds.operationOnStandardResourceId(OPERATION_ID, RESOURCE_ID);
    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);

    @Test
    void shiftCalendarRcaOnLeafSrpRollsUpToParentDay() {
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(DAY);
        PeriodIndex periodIndex = PeriodIndex.of(periods);
        assertEquals(3, periods.size());
        assertEquals(2, periodIndex.leafPeriods().size());

        Period parent = periods.get(0);
        Period s1 = periods.get(1);
        Period s2 = periods.get(2);
        assertEquals(false, parent.isLeaf());
        assertEquals("S1", s1.getShiftId());
        assertEquals("S2", s2.getShiftId());

        String parentSrpId = OntologyIds.srpId(RESOURCE_ID, parent.getSequenceNr());
        String s1SrpId = OntologyIds.srpId(RESOURCE_ID, s1.getSequenceNr());
        String s2SrpId = OntologyIds.srpId(RESOURCE_ID, s2.getSequenceNr());

        StandardResourcePeriod parentSrp = new StandardResourcePeriod(parentSrpId, RESOURCE_ID, parent.getId());
        StandardResourcePeriod s1Srp = srp(s1SrpId, RESOURCE_ID, s1.getId(), 480);
        StandardResourcePeriod s2Srp = srp(s2SrpId, RESOURCE_ID, s2.getId(), 360);

        Operation operation = new Operation();
        operation.setId(OPERATION_ID);
        operation.setSupplyOrderId(SUPPLY_ORDER_ID);
        operation.setRoutingSequenceNo(1);

        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(periods)
                .operation(operation)
                .operationOnStandardResource(new OperationOnStandardResource(
                        OOSR_ID, OPERATION_ID, RESOURCE_ID, 1, 0, 60))
                .standardResourcePeriod(parentSrp)
                .standardResourcePeriod(s1Srp)
                .standardResourcePeriod(s2Srp)
                .build();

        List<MasterPlanAllocationDto> allocations = List.of(
                allocation("WO-SHIFT-S2@OP1_0#0", "S1", 120),
                allocation("WO-SHIFT-S2@OP1_0#1", "S2", 90));

        ResourceCapacityAssignmentProjection.apply(graph, allocations, periodIndex);
        assertEquals(2, graph.resourceCapacityAssignmentsById().size());
        assertNull(graph.resourceCapacityAssignmentsById().values().stream()
                .filter(rca -> parentSrpId.equals(rca.getStandardResourcePeriodId()))
                .findFirst()
                .orElse(null));

        s1Srp.setReservedCapacity(ResourceCapacityAssignmentRollup.reservedMinutesBySrpId(graph).get(s1SrpId));
        s2Srp.setReservedCapacity(ResourceCapacityAssignmentRollup.reservedMinutesBySrpId(graph).get(s2SrpId));
        s1Srp.recalculateCapacityFields();
        s2Srp.recalculateCapacityFields();
        StandardResourcePeriodRollup.rollupParentReserved(graph);

        assertEquals(120.0, s1Srp.getReservedCapacity());
        assertEquals(90.0, s2Srp.getReservedCapacity());
        assertEquals(210.0, parentSrp.getReservedCapacity());
    }

    private static StandardResourcePeriod srp(String id, String resourceId, String periodId, double total) {
        StandardResourcePeriod srp = new StandardResourcePeriod(id, resourceId, periodId);
        srp.setTotalCapacity(total);
        srp.recalculateCapacityFields();
        return srp;
    }

    private static MasterPlanAllocationDto allocation(String allocationId, String shiftId, int minutes) {
        return new MasterPlanAllocationDto(
                allocationId,
                0,
                SUPPLY_ORDER_ID,
                null,
                "MRP",
                "FG-SHIFT",
                new BigDecimal("40"),
                "SO-SHIFT",
                1,
                RESOURCE_ID,
                0,
                DAY,
                shiftId,
                LocalDateTime.of(2026, 6, 5, 8, 0),
                LocalDateTime.of(2026, 6, 5, 16, 0),
                minutes);
    }
}
