package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.persistence.entity.OntChangeLogEntity;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.ontology.persistence.support.PostgresOntologyTestProfile;
import com.plantops.ontology.persistence.support.PostgresTestSupport;
import com.plantops.ontology.period.StandardResourcePeriod;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AC-PERS-02: simulate persists DRAFT revision + WAL; reload session ≡ last successful write.
 */
@QuarkusTest
@TestProfile(PostgresOntologyTestProfile.class)
@EnabledIf("com.plantops.ontology.persistence.support.PostgresTestSupport#isAvailable")
class OntologyDraftPersistenceIntegrationTest {

    private static final String WS = "ont-pers-p2";

    @Inject
    OntologyPersistenceService persistence;

    @Inject
    OntologySessionPersistenceService sessionPersistence;

    @Test
    @TestTransaction
    void simulatePersistAndReloadDraftSession() {
        String sessionId = "SES-PERS-02";
        OntologyGraph graph = OntologyPersistenceTestFixtures.sampleP0Graph();
        String baseRevisionId = persistence.importCommittedP0(WS, graph);

        persistence.createDraftSession(
                WS,
                sessionId,
                baseRevisionId,
                graph,
                LocalDateTime.now().plusHours(8),
                null);

        ProductInStockingPointPeriod pispp = graph.pispPeriodsById().values().iterator().next();
        pispp.setOnHand(35);
        pispp.setPlannedSupplyTotal(65);
        pispp.recalculatePlanningFields();

        long changeSeq = persistence.persistSimulateChange(
                WS, sessionId, graph, "PISPP", pispp.getId(), "onHand", 35.0);
        assertEquals(1, changeSeq);

        OntologyGraph reloaded = persistence.loadDraftSession(WS, sessionId);
        OntologyPersistenceTestFixtures.assertP0Parity(graph, reloaded);

        // Simulate process restart: load again from DB only.
        OntologyGraph afterRestart = sessionPersistence.loadDraftSession(WS, sessionId);
        OntologyPersistenceTestFixtures.assertP0Parity(graph, afterRestart);

        assertEquals(1, OntChangeLogEntity.count(
                "workspaceId = ?1 and revisionId = ?2",
                WS,
                sessionPersistence.findDraftSession(WS, sessionId).orElseThrow().draftRevisionId()));
    }

    @Test
    @TestTransaction
    void optimizePersistUpdatesSrpAndWal() {
        String sessionId = "SES-PERS-OPT";
        OntologyGraph graph = OntologyPersistenceTestFixtures.sampleP0Graph();
        String baseRevisionId = persistence.importCommittedP0(WS, graph);

        persistence.createDraftSession(
                WS, sessionId, baseRevisionId, graph,
                LocalDateTime.now().plusHours(8), null);

        StandardResourcePeriod srp = graph.srpById().values().iterator().next();
        srp.setReservedCapacity(200);
        srp.recalculateCapacityFields();

        long changeSeq = persistence.persistOptimizeResult(
                WS, sessionId, graph, Map.of("score", "0hard/-100soft"));
        assertEquals(1, changeSeq);

        StandardResourcePeriod restored = persistence.loadDraftSession(WS, sessionId).srp(srp.getId());
        assertNotNull(restored);
        assertEquals(200, restored.getReservedCapacity(), 1e-9);
    }
}
