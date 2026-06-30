package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** Default {@link OntologyPersistencePort} (P1 read + P2 DRAFT write path). */
@ApplicationScoped
public class OntologyPersistenceService implements OntologyPersistencePort {

    @Inject
    OntologyLegacyImporter legacyImporter;

    @Inject
    OntologyRestorer restorer;

    @Inject
    OntologySessionPersistenceService sessionPersistence;

    @Override
    @Transactional
    public String importCommittedP0(String workspaceId, OntologyGraph graph) {
        return legacyImporter.importCommittedP0(workspaceId, graph);
    }

    @Override
    public OntologyGraph loadRevision(String workspaceId, String revisionId) {
        return restorer.loadRevision(workspaceId, revisionId);
    }

    @Override
    @Transactional
    public OntologySessionPersistenceService.DraftSessionHandle createDraftSession(
            String workspaceId,
            String sessionId,
            String baseRevisionId,
            OntologyGraph graph,
            LocalDateTime expiresAt,
            String deliveryId) {
        return sessionPersistence.createDraftSession(
                workspaceId, sessionId, baseRevisionId, graph, expiresAt, deliveryId);
    }

    @Override
    @Transactional
    public long persistSimulateChange(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            String targetType,
            String targetId,
            String property,
            Object value) {
        return sessionPersistence.persistSimulateChange(
                workspaceId, sessionId, graph, targetType, targetId, property, value);
    }

    @Override
    @Transactional
    public long persistOptimizeResult(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            Map<String, Object> optimizerResultJson) {
        return sessionPersistence.persistOptimizeResult(
                workspaceId, sessionId, graph, optimizerResultJson);
    }

    @Override
    public OntologyGraph loadDraftSession(String workspaceId, String sessionId) {
        return sessionPersistence.loadDraftSession(workspaceId, sessionId);
    }
}
