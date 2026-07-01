package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AC-PERS-05 on H2 (legacy + V66/V67 ont_*). */
@QuarkusTest
class OntologyPartialPersistenceH2IntegrationTest {

    @Inject
    OntologyPersistenceService persistence;

    @Test
    @TestTransaction
    void partialForkDerivesPisppMatchingFullRestore() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph source = OntologyPersistenceTestFixtures.sampleP0Graph();
        String fullRevisionId = persistence.importCommittedP0(workspaceId, source);

        String partialRevisionId = persistence.importPartialP0Fork(workspaceId, source, fullRevisionId);
        assertEquals(0, OntPisppEntity.forRevision(workspaceId, partialRevisionId).size());

        OntologyGraph fullRestored = persistence.loadRevision(workspaceId, fullRevisionId);
        OntologyGraph partialRestored = persistence.loadRevision(workspaceId, partialRevisionId);
        OntologyPersistenceTestFixtures.assertP0Parity(fullRestored, partialRestored);
    }

    @Test
    @TestTransaction
    void importAndRestoreResourceCapacityAssignments() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph source = OntologyPersistenceTestFixtures.sampleP0Graph();
        String revisionId = persistence.importCommittedP0(workspaceId, source);
        OntologyGraph restored = persistence.loadRevision(workspaceId, revisionId);
        OntologyPersistenceTestFixtures.assertP0Parity(source, restored);
    }
}
