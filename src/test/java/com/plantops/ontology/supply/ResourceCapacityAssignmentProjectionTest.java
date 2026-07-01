package com.plantops.ontology.supply;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.rol.ChangeOperation;
import com.plantops.scenario.planning.OntologyTimefoldMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCapacityAssignmentProjectionTest {

    private static final String SUPPLY_ORDER_ID = "WO-RCA-PROJ";
    private static final String RESOURCE_ID = "RES-RCA-PROJ";
    private static final String OPERATION_ID = OntologyIds.operationId(SUPPLY_ORDER_ID, 1);
    private static final String OOSR_ID = OntologyIds.operationOnStandardResourceId(OPERATION_ID, RESOURCE_ID);
    private static final LocalDate PLANNED_DATE = LocalDate.of(2026, 6, 5);
    private static final String SRP_ID = OntologyIds.srpId(RESOURCE_ID, 4);

    @Test
    void optimizeWriteBackCreatesRcasAndRollsUpSrpReserved() {
        OntologyGraph graph = baseGraph();
        List<MasterPlanAllocationDto> allocations = List.of(
                allocation("WO-RCA-PROJ@OP1_0#0", 120),
                allocation("WO-RCA-PROJ@OP1_0#1", 60));

        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        ResourceCapacityAssignmentProjection.apply(graph, allocations, periodIndex);

        assertTrue(ResourceCapacityAssignmentValidation.validate(graph).isEmpty());
        assertEquals(2, graph.resourceCapacityAssignmentsById().size());
        assertEquals(180.0, ResourceCapacityAssignmentRollup.reservedMinutesBySrpId(graph).get(SRP_ID));

        double reservedFromChangeSet = new OntologyTimefoldMapper()
                .toChangeSet(allocations, graph, periodIndex)
                .operations()
                .stream()
                .filter(op -> ChangeOperation.TARGET_STANDARD_RESOURCE_PERIOD.equals(op.targetType()))
                .filter(op -> SRP_ID.equals(op.targetId()))
                .mapToDouble(op -> ((Number) op.value()).doubleValue())
                .findFirst()
                .orElseThrow();
        assertEquals(180.0, reservedFromChangeSet);
    }

    private static MasterPlanAllocationDto allocation(String allocationId, int durationMinutes) {
        return new MasterPlanAllocationDto(
                allocationId,
                0,
                SUPPLY_ORDER_ID,
                null,
                "MRP",
                "FG-RCA-PROJ",
                new BigDecimal("40"),
                "SO-RCA",
                1,
                RESOURCE_ID,
                0,
                PLANNED_DATE,
                "DAY",
                LocalDateTime.of(2026, 6, 5, 8, 0),
                LocalDateTime.of(2026, 6, 5, 16, 0),
                durationMinutes);
    }

    private static OntologyGraph baseGraph() {
        Period period = new Period(OntologyIds.periodId(4), 4, PLANNED_DATE, PLANNED_DATE);
        Operation operation = new Operation();
        operation.setId(OPERATION_ID);
        operation.setSupplyOrderId(SUPPLY_ORDER_ID);
        operation.setRoutingSequenceNo(1);
        operation.setSequenceNr(1);

        StandardResourcePeriod srp = new StandardResourcePeriod(SRP_ID, RESOURCE_ID, period.getId());
        srp.setTotalCapacity(480);
        srp.recalculateCapacityFields();

        return OntologyGraph.builder()
                .operation(operation)
                .operationOnStandardResource(new OperationOnStandardResource(
                        OOSR_ID, OPERATION_ID, RESOURCE_ID, 1, 0, 60))
                .standardResourcePeriod(srp)
                .periodsOrdered(List.of(
                        new Period(OntologyIds.periodId(0), 0, PLANNED_DATE.minusDays(4), PLANNED_DATE.minusDays(4)),
                        new Period(OntologyIds.periodId(1), 1, PLANNED_DATE.minusDays(3), PLANNED_DATE.minusDays(3)),
                        new Period(OntologyIds.periodId(2), 2, PLANNED_DATE.minusDays(2), PLANNED_DATE.minusDays(2)),
                        new Period(OntologyIds.periodId(3), 3, PLANNED_DATE.minusDays(1), PLANNED_DATE.minusDays(1)),
                        period))
                .build();
    }
}
