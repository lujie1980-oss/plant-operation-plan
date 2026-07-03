package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.entity.OntRevisionEntity;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.ontology.persistence.support.PostgresOntologyTestProfile;
import com.plantops.ontology.persistence.support.PostgresTestSupport;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AC-PERS-03: confirm promotes DRAFT → COMMITTED and updates WORKSPACE / PLAN HEAD pointers.
 */
@QuarkusTest
@TestProfile(PostgresOntologyTestProfile.class)
@EnabledIf("com.plantops.ontology.persistence.support.PostgresTestSupport#isAvailable")
class OntologyConfirmIntegrationTest {

    private static final String WS = "ont-pers-p3";

    @Inject
    OntologyPersistenceService persistence;

    @Inject
    OntologyRestorer restorer;

    @Inject
    OntologyRevisionService revisionService;

    @Test
    @TestTransaction
    void promoteDraftToCommittedUpdatesHeadAndPlanVersion() {
        String sessionId = "SES-PERS-03";
        String planVersionId = "PV-PERS-03";
        OntologyGraph graph = OntologyPersistenceTestFixtures.sampleP0Graph();
        String baseRevisionId = persistence.importCommittedP0(WS, graph);

        var draft = persistence.createDraftSession(
                WS,
                sessionId,
                baseRevisionId,
                graph,
                LocalDateTime.now().plusHours(8),
                null);

        String draftRevisionId = draft.draftRevisionId();

        ProductInStockingPointPeriod pispp = graph.pispPeriodsById().values().iterator().next();
        pispp.setOnHand(42);
        pispp.recalculatePlanningFields();
        persistence.persistSimulateChange(
                WS, sessionId, graph, "PISPP", pispp.getId(), "onHand", 42.0);

        var outcome = persistence.promoteDraftToCommitted(WS, sessionId, planVersionId);
        assertEquals(draftRevisionId, outcome.revisionId());
        assertEquals(planVersionId, outcome.planVersionId());

        OntRevisionEntity committed = revisionService.requireRevision(WS, draftRevisionId);
        assertEquals("COMMITTED", committed.status);
        assertEquals(planVersionId, committed.planVersionId);
        assertNotNull(committed.committedAt);

        String workspaceHead = OntRevisionHeadEntity.findHead(WS, OntologyRevisionService.WORKSPACE_SCOPE)
                .map(h -> h.revisionId)
                .orElseThrow();
        assertEquals(draftRevisionId, workspaceHead);

        String planHead = OntRevisionHeadEntity.findHead(WS, "PLAN:" + planVersionId)
                .map(h -> h.revisionId)
                .orElseThrow();
        assertEquals(draftRevisionId, planHead);

        OntologyGraph restored = restorer.loadRevision(WS, draftRevisionId);
        OntologyPersistenceTestFixtures.assertP0Parity(graph, restored);
    }
}
