package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OntologyRcaProjectorTest {

    private static final String SUPPLY_ORDER_ID = "WO-RCA-PROJ3";
    private static final String RESOURCE_ID = "RES-RCA-PROJ3";
    private static final String OPERATION_ID = OntologyIds.operationId(SUPPLY_ORDER_ID, 1);
    private static final String OOSR_ID = OntologyIds.operationOnStandardResourceId(OPERATION_ID, RESOURCE_ID);
    private static final LocalDate PLANNED_DATE = LocalDate.of(2026, 6, 5);
    private static final String SRP_ID = OntologyIds.srpId(RESOURCE_ID, 4);

    @Test
    void derivesEligibleTimeSlotsFromSrpPeriod() {
        OntologyGraph graph = baseGraph();
        List<TimeSlot> slots = horizonSlots();

        List<TimeSlot> eligible = OntologyRcaProjector.eligibleTimeSlotsForSrp(graph, SRP_ID, slots);
        assertEquals(1, eligible.size());
        assertEquals(PLANNED_DATE, eligible.get(0).getDate());
        assertEquals(RESOURCE_ID, eligible.get(0).getResourceId());
    }

    @Test
    void overlaysOntologyAssignedMinutesOntoSolverCandidates() {
        OntologyGraph graph = baseGraph();
        List<TimeSlot> slots = horizonSlots();
        graph.replaceResourceCapacityAssignments(List.of(new ResourceCapacityAssignment(
                "RCA-WO@OP1_0#0",
                OPERATION_ID,
                OOSR_ID,
                SRP_ID,
                180,
                180,
                false,
                null)));

        com.plantops.solver.masterplan.ResourceCapacityAssignment solverCandidate =
                new com.plantops.solver.masterplan.ResourceCapacityAssignment();
        solverCandidate.setOperationId(OPERATION_ID);
        solverCandidate.setResourceId(RESOURCE_ID);
        solverCandidate.setWorkOrderNo(SUPPLY_ORDER_ID);
        solverCandidate.setOperationSeq(1);
        solverCandidate.setAssignedMinutes(0);

        OntologyRcaProjector.overlayOntologyOntoSolverCandidates(graph, List.of(solverCandidate), slots);

        assertEquals(180, solverCandidate.getAssignedMinutes());
        assertNotNull(solverCandidate.getTimeSlot());
        assertEquals(PLANNED_DATE, solverCandidate.getTimeSlot().getDate());
    }

    @Test
    void syncsSolverResultsBackToOntologyRca() {
        OntologyGraph graph = baseGraph();
        List<TimeSlot> slots = horizonSlots();
        ResourceCapacityAssignment ontologyRca = new ResourceCapacityAssignment(
                "RCA-WO@OP1_0#0",
                OPERATION_ID,
                OOSR_ID,
                SRP_ID,
                0,
                240,
                false,
                null);
        graph.replaceResourceCapacityAssignments(List.of(ontologyRca));

        com.plantops.solver.masterplan.ResourceCapacityAssignment solverAssigned =
                new com.plantops.solver.masterplan.ResourceCapacityAssignment();
        solverAssigned.setOperationId(OPERATION_ID);
        solverAssigned.setResourceId(RESOURCE_ID);
        solverAssigned.setAssignedMinutes(240);
        solverAssigned.setTimeSlot(slots.get(4));

        OntologyRcaProjector.syncOntologyFromSolverAssignments(graph, List.of(solverAssigned), slots);

        assertEquals(240, ontologyRca.getAssignedMinutes());
    }

    private static List<TimeSlot> horizonSlots() {
        List<TimeSlot> slots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate date = PLANNED_DATE.minusDays(4 - i);
            slots.add(new TimeSlot(
                    RESOURCE_ID + "-D" + i,
                    i,
                    date,
                    date,
                    TimeslotGranularity.DAY,
                    "DAY",
                    RESOURCE_ID,
                    480));
        }
        return slots;
    }

    private static OntologyGraph baseGraph() {
        Period period = new Period(OntologyIds.periodId(4), 4, PLANNED_DATE, PLANNED_DATE);
        Operation operation = new Operation();
        operation.setId(OPERATION_ID);
        operation.setSupplyOrderId(SUPPLY_ORDER_ID);
        operation.setRoutingSequenceNo(1);

        StandardResourcePeriod srp = new StandardResourcePeriod(SRP_ID, RESOURCE_ID, period.getId());

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
