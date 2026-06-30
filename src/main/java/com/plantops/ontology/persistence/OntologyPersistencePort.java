package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Sole write entry for ont_* persistence (ADR-09 · RULE-PERS-02).
 */
public interface OntologyPersistencePort {

    /** Persist P0 entity snapshot from an in-memory graph into a new COMMITTED revision. */
    String importCommittedP0(String workspaceId, OntologyGraph graph);

    /** Load a revision into memory (P0 entities). */
    OntologyGraph loadRevision(String workspaceId, String revisionId);

    /** Fork a DRAFT session revision + ont_session row from an in-memory graph. */
    OntologySessionPersistenceService.DraftSessionHandle createDraftSession(
            String workspaceId,
            String sessionId,
            String baseRevisionId,
            OntologyGraph graph,
            LocalDateTime expiresAt,
            String deliveryId);

    /** After simulate ROL: upsert P0 rows + append WAL (same transaction). */
    long persistSimulateChange(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            String targetType,
            String targetId,
            String property,
            Object value);

    /** After optimize write-back: upsert P0 rows + WAL + session optimizer_result_json. */
    long persistOptimizeResult(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            Map<String, Object> optimizerResultJson);

    /** Load DRAFT revision for a persisted session (AC-PERS-02 recovery path). */
    OntologyGraph loadDraftSession(String workspaceId, String sessionId);
}
