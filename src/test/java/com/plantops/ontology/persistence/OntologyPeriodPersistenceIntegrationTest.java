package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodGranularity;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.persistence.entity.OntPeriodEntity;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class OntologyPeriodPersistenceIntegrationTest {

    @Inject
    OntologyPersistenceService persistence;

    @Test
    @TestTransaction
    void restoresShiftPeriodHierarchy() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        LocalDate start = LocalDate.of(2026, 6, 5);
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(start);

        OntologyGraph source = OntologyGraph.builder()
                .periodsOrdered(periods)
                .build();

        String revisionId = persistence.importCommittedP0(workspaceId, source);
        assertFalse(OntPeriodEntity.forRevision(workspaceId, revisionId).isEmpty());

        OntologyGraph restored = persistence.loadRevision(workspaceId, revisionId);
        assertEquals(periods.size(), restored.periodsOrdered().size());
        assertEquals(PeriodGranularity.DAY, restored.periodsOrdered().get(0).getGranularity());
        assertEquals(PeriodGranularity.SHIFT, restored.periodsOrdered().get(1).getGranularity());
        assertEquals("S1", restored.periodsOrdered().get(1).getShiftId());
        assertEquals(OntologyIds.periodId(0), restored.periodsOrdered().get(1).getParentPeriodId());
    }

    @Test
    @TestTransaction
    void sampleP0GraphRoundTripsPeriods() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph source = OntologyPersistenceTestFixtures.sampleP0Graph();
        String revisionId = persistence.importCommittedP0(workspaceId, source);
        OntologyGraph restored = persistence.loadRevision(workspaceId, revisionId);
        OntologyPersistenceTestFixtures.assertP0Parity(source, restored);
    }
}
