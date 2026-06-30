package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.entity.OntRevisionEntity;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/** Revision fork / promote / HEAD management (§5.14.1). */
@ApplicationScoped
public class OntologyRevisionService {

    public static final String WORKSPACE_SCOPE = "WORKSPACE";

    public String newRevisionId() {
        return "REV-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Transactional
    public OntRevisionEntity createRevision(
            String workspaceId,
            String revisionId,
            String status,
            String persistenceMode,
            String parentRevisionId,
            String planVersionId,
            String sessionId) {
        OntRevisionEntity rev = new OntRevisionEntity();
        rev.workspaceId = workspaceId;
        rev.revisionId = revisionId;
        rev.status = status;
        rev.persistenceMode = persistenceMode;
        rev.parentRevisionId = parentRevisionId;
        rev.planVersionId = planVersionId;
        rev.sessionId = sessionId;
        rev.changeSeq = 0;
        LocalDateTime now = LocalDateTime.now();
        rev.createdAt = now;
        rev.updatedAt = now;
        if ("COMMITTED".equals(status)) {
            rev.committedAt = now;
        }
        rev.persist();
        return rev;
    }

    @Transactional
    public void setHead(String workspaceId, String scopeKey, String revisionId) {
        OntRevisionHeadEntity head = OntRevisionHeadEntity
                .findHead(workspaceId, scopeKey)
                .orElseGet(() -> {
                    OntRevisionHeadEntity h = new OntRevisionHeadEntity();
                    h.workspaceId = workspaceId;
                    h.scopeKey = scopeKey;
                    return h;
                });
        head.revisionId = revisionId;
        head.updatedAt = LocalDateTime.now();
        head.persist();
    }

    public OntRevisionEntity requireRevision(String workspaceId, String revisionId) {
        return OntRevisionEntity.findRevision(workspaceId, revisionId)
                .orElseThrow(() -> new NotFoundException(
                        "ont_revision not found: " + workspaceId + "/" + revisionId));
    }

    public String resolveWorkspaceHeadRevisionId(String workspaceId) {
        return OntRevisionHeadEntity.findHead(workspaceId, WORKSPACE_SCOPE)
                .map(h -> h.revisionId)
                .orElse(null);
    }

    @Transactional
    public long nextChangeSeq(String workspaceId, String revisionId) {
        OntRevisionEntity rev = requireRevision(workspaceId, revisionId);
        rev.changeSeq++;
        rev.updatedAt = LocalDateTime.now();
        return rev.changeSeq;
    }
}
