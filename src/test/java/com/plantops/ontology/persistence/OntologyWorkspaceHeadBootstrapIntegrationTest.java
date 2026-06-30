package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** P4: legacy loader seeds WORKSPACE HEAD when absent. */
@QuarkusTest
class OntologyWorkspaceHeadBootstrapIntegrationTest {

    @Inject
    OntologyWorkspaceHeadBootstrapService bootstrapService;

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    WorkspaceContext workspaceContext;

    @Test
    @TestTransaction
    void ensureWorkspaceHeadCreatesCommittedHead() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        if (WorkspaceEntity.find("workspaceId", workspaceId).firstResultOptional().isEmpty()) {
            WorkspaceEntity ws = new WorkspaceEntity();
            ws.workspaceId = workspaceId;
            ws.name = "bootstrap-test";
            ws.isDefault = true;
            ws.persist();
        }

        String revisionId = bootstrapService.ensureWorkspaceHead(workspaceId);
        assertNotNull(revisionId);

        String head = OntRevisionHeadEntity.findHead(
                        workspaceId, OntologyRevisionService.WORKSPACE_SCOPE)
                .map(h -> h.revisionId)
                .orElse(null);
        assertNotNull(head);
        assertNotNull(revisionService.requireRevision(workspaceId, head).committedAt);
    }
}
