package com.plantops.ontology.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Copy P0 entity rows between revisions (fork base → DRAFT). */
@ApplicationScoped
public class OntologyRevisionCopyService {

    @Inject
    OntologyRestorer restorer;

    @Inject
    OntologyP0UpsertService upsertService;

    public void copyP0Revision(String workspaceId, String fromRevisionId, String toRevisionId) {
        upsertService.replaceP0Graph(
                workspaceId, toRevisionId, restorer.loadRevision(workspaceId, fromRevisionId));
    }
}
