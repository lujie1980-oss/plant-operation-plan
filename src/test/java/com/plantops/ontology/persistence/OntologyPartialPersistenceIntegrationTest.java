package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.ontology.persistence.support.PostgresOntologyTestProfile;
import com.plantops.ontology.persistence.support.PostgresTestSupport;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-PERS-05: PARTIAL policy skips DERIVE persistence; restore + derive ≡ FULL reload.
 */
@QuarkusTest
@TestProfile(PostgresOntologyTestProfile.class)
@EnabledIf("com.plantops.ontology.persistence.support.PostgresTestSupport#isAvailable")
class OntologyPartialPersistenceIntegrationTest {

    private static final String WS = "ont-pers-p5";

    @Inject
    OntologyPersistenceService persistence;

    @Test
    @TestTransaction
    void partialForkSkipsPisppRowsAndDerivesOnLoad() {
        OntologyGraph source = OntologyPersistenceTestFixtures.sampleP0Graph();
        String fullRevisionId = persistence.importCommittedP0(WS, source);
        OntologyGraph fullRestored = persistence.loadRevision(WS, fullRevisionId);

        String partialRevisionId = persistence.importPartialP0Fork(WS, source, fullRevisionId);
        assertEquals(
                0,
                OntPisppEntity.forRevision(WS, partialRevisionId).size(),
                "PISPP must not be stored on PARTIAL revision");
        assertTrue(
                OntDemandEntityCount(WS, partialRevisionId) > 0,
                "STORE entities must be persisted");

        OntologyGraph partialRestored = persistence.loadRevision(WS, partialRevisionId);
        OntologyPersistenceTestFixtures.assertP0Parity(fullRestored, partialRestored);
    }

    private static long OntDemandEntityCount(String workspaceId, String revisionId) {
        return com.plantops.ontology.persistence.entity.OntDemandEntity.count(
                "workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
