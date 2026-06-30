package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.ontology.persistence.support.PostgresOntologyTestProfile;
import com.plantops.ontology.persistence.support.PostgresTestSupport;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * AC-PERS-01 (P0 subset): import in-memory P0 entities → ont_* → restore ≡ source on key fields.
 */
@QuarkusTest
@TestProfile(PostgresOntologyTestProfile.class)
@EnabledIf("com.plantops.ontology.persistence.support.PostgresTestSupport#isAvailable")
class OntologyRestorerIntegrationTest {

    private static final String WS = "ont-pers-test";

    @Inject
    OntologyPersistenceService persistence;

    @Test
    @TestTransaction
    void importAndRestoreP0EntitiesMatch() {
        OntologyGraph source = OntologyPersistenceTestFixtures.sampleP0Graph();
        String revisionId = persistence.importCommittedP0(WS, source);
        OntologyGraph restored = persistence.loadRevision(WS, revisionId);

        OntologyPersistenceTestFixtures.assertP0Parity(source, restored);
    }
}
