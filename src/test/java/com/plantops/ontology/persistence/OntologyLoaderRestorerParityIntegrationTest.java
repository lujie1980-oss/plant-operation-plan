package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * AC-PERS-01 (H2): OntologyLoader workspace build → import → OntologyRestorer P0 parity.
 */
@QuarkusTest
class OntologyLoaderRestorerParityIntegrationTest {

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistenceService persistence;

    @Test
    @TestTransaction
    void loaderWorkspaceGraphP0SubsetRoundTripsThroughOnt() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph loaded = ontologyLoader.loadForWorkspace(LocalDate.of(2026, 6, 7));
        String revisionId = persistence.importCommittedP0(workspaceId, loaded);
        OntologyGraph restored = persistence.loadRevision(workspaceId, revisionId);

        OntologyPersistenceTestFixtures.assertP0Parity(loaded, restored);
    }
}
