package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodGranularity;
import com.plantops.ontology.period.StandardResourcePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCapacityAssignmentValidationTest {

    private static final String SUPPLY_ORDER_ID = "SO-RCA-TEST";
    private static final String OPERATION_ID = OntologyIds.operationId(SUPPLY_ORDER_ID, 1);
    private static final String RESOURCE_A = "RES-A";
    private static final String RESOURCE_B = "RES-B";
    private static final String OOSR_A = OntologyIds.operationOnStandardResourceId(OPERATION_ID, RESOURCE_A);
    private static final String OOSR_B = OntologyIds.operationOnStandardResourceId(OPERATION_ID, RESOURCE_B);
    private static final String SRP_A0 = OntologyIds.srpId(RESOURCE_A, 0);
    private static final String SRP_B0 = OntologyIds.srpId(RESOURCE_B, 0);

    @Test
    void stableKeyAndMinuteConservationAcrossSplitAssignments() {
        OntologyGraph graph = baseGraphBuilder()
                .resourceCapacityAssignment(rca(OOSR_A, SRP_A0, 120, 240))
                .resourceCapacityAssignment(rca(OOSR_B, SRP_B0, 120, 240))
                .build();

        String expectedId = OntologyIds.resourceCapacityAssignmentId(OPERATION_ID, OOSR_A, SRP_A0);
        assertEquals(expectedId, graph.resourceCapacityAssignment(expectedId).getId());
        assertEquals(2, graph.resourceCapacityAssignmentsForOperation(OPERATION_ID).size());
        assertEquals(1, graph.resourceCapacityAssignmentsForSrp(SRP_A0).size());
        assertTrue(ResourceCapacityAssignmentValidation.validate(graph).isEmpty());
    }

    @Test
    void rejectsOosrResourceMismatchWithSrp() {
        OntologyGraph graph = baseGraphBuilder()
                .resourceCapacityAssignment(rca(OOSR_A, SRP_B0, 240, 240))
                .build();

        assertTrue(ResourceCapacityAssignmentValidation.validate(graph).stream()
                .anyMatch(msg -> msg.contains("OOSR resource") && msg.contains("SRP resource")));
    }

    @Test
    void rejectsMinuteConservationViolation() {
        OntologyGraph graph = baseGraphBuilder()
                .resourceCapacityAssignment(rca(OOSR_A, SRP_A0, 100, 240))
                .build();

        assertTrue(ResourceCapacityAssignmentValidation.validate(graph).stream()
                .anyMatch(msg -> msg.contains("assignedMinutes sum")));
    }

    private static OntologyGraph.Builder baseGraphBuilder() {
        Operation operation = new Operation();
        operation.setId(OPERATION_ID);
        operation.setSupplyOrderId(SUPPLY_ORDER_ID);
        operation.setSequenceNr(1);

        Period period = new Period(OntologyIds.periodId(0), 0, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        StandardResourcePeriod srpA = new StandardResourcePeriod(SRP_A0, RESOURCE_A, period.getId());
        StandardResourcePeriod srpB = new StandardResourcePeriod(SRP_B0, RESOURCE_B, period.getId());

        return OntologyGraph.builder()
                .operation(operation)
                .operationOnStandardResource(new OperationOnStandardResource(
                        OOSR_A, OPERATION_ID, RESOURCE_A, 1, 0, 60))
                .operationOnStandardResource(new OperationOnStandardResource(
                        OOSR_B, OPERATION_ID, RESOURCE_B, 2, 0, 60))
                .standardResourcePeriod(srpA)
                .standardResourcePeriod(srpB)
                .periodsOrdered(java.util.List.of(period));
    }

    @Test
    void rejectsRcaOnParentDaySrp() {
        Period parent = new Period(OntologyIds.periodId(0), 0, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        parent.setLeaf(false);
        Period shift = new Period(OntologyIds.periodId(1), 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        shift.setGranularity(PeriodGranularity.SHIFT);
        shift.setShiftId("S1");
        shift.setParentPeriodId(parent.getId());

        StandardResourcePeriod parentSrp = new StandardResourcePeriod(
                OntologyIds.srpId(RESOURCE_A, 0), RESOURCE_A, parent.getId());

        OntologyGraph graph = baseGraphBuilder()
                .periodsOrdered(java.util.List.of(parent, shift))
                .standardResourcePeriod(parentSrp)
                .resourceCapacityAssignment(rca(OOSR_A, parentSrp.getId(), 60, 60))
                .build();

        assertTrue(ResourceCapacityAssignmentValidation.validate(graph).stream()
                .anyMatch(msg -> msg.contains("leaf SRP")));
    }

    @Test
    void rejectsParallelGroupOnDifferentLeafSrps() {
        OntologyGraph graph = baseGraphBuilder()
                .resourceCapacityAssignment(rcaWithGroup(OOSR_A, SRP_A0, 120, 240, "GRP-1"))
                .resourceCapacityAssignment(rcaWithGroup(OOSR_B, SRP_B0, 120, 240, "GRP-1"))
                .build();

        assertTrue(ResourceCapacityAssignmentValidation.validateParallelGroups(graph).stream()
                .anyMatch(msg -> msg.contains("parallel group")));
    }

    private static ResourceCapacityAssignment rcaWithGroup(
            String oosrId, String srpId, int assignedMinutes, int operationTotalMinutes, String groupId) {
        String id = OntologyIds.resourceCapacityAssignmentId(OPERATION_ID, oosrId, srpId);
        return new ResourceCapacityAssignment(
                id,
                OPERATION_ID,
                oosrId,
                srpId,
                assignedMinutes,
                operationTotalMinutes,
                false,
                groupId);
    }

    private static ResourceCapacityAssignment rca(
            String oosrId, String srpId, int assignedMinutes, int operationTotalMinutes) {
        return rcaWithGroup(oosrId, srpId, assignedMinutes, operationTotalMinutes, null);
    }
}
