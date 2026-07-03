package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.entity.OntChangeLogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;

/** Append-only WAL for DRAFT revisions (§5.14.3 · P2 write path). */
@ApplicationScoped
public class OntologyChangeLogService {

    @Transactional
    public void append(
            String workspaceId,
            String revisionId,
            long changeSeq,
            String changeType,
            String entityType,
            String entityId,
            Map<String, Object> payload) {
        OntChangeLogEntity row = new OntChangeLogEntity();
        row.workspaceId = workspaceId;
        row.revisionId = revisionId;
        row.changeSeq = changeSeq;
        row.changeType = changeType;
        row.entityType = entityType;
        row.entityId = entityId;
        row.payloadJson = payload != null ? payload : Map.of();
        row.persist();
    }
}
