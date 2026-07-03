package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.config.OntologyLegacyDualWriteFeature;
import com.plantops.ontology.persistence.entity.OntSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * DRAFT Session fork + simulate/optimize write path (§5.14.3 · TODO-12 P2).
 * Writes ont_session, DRAFT revision, P0 upserts, and WAL in one transaction.
 */
@ApplicationScoped
public class OntologySessionPersistenceService {

    public static final String SOLVE_PROFILE_PLAN_VERSION_KEY = "basePlanVersionId";

    public record DraftSessionHandle(
            String sessionId,
            String draftRevisionId,
            String baseRevisionId) {}

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyP0UpsertService upsertService;

    @Inject
    OntologyRestorer restorer;

    @Inject
    OntologyChangeLogService changeLogService;

    @Inject
    OntologyLegacyDualWriteFeature dualWriteFeature;

    @Inject
    OntologyLegacyDualWriteService dualWriteService;

    @Transactional
    public DraftSessionHandle createDraftSession(
            String workspaceId,
            String sessionId,
            String baseRevisionId,
            OntologyGraph graph,
            LocalDateTime expiresAt,
            String deliveryId) {
        String parentRevisionId = baseRevisionId != null
                ? baseRevisionId
                : revisionService.resolveWorkspaceHeadRevisionId(workspaceId);

        String draftRevisionId = revisionService.newRevisionId();
        revisionService.createRevision(
                workspaceId,
                draftRevisionId,
                "DRAFT",
                "FULL",
                parentRevisionId,
                null,
                sessionId);
        upsertService.replaceP0Graph(workspaceId, draftRevisionId, graph);

        OntSessionEntity session = new OntSessionEntity();
        session.workspaceId = workspaceId;
        session.sessionId = sessionId;
        session.draftRevisionId = draftRevisionId;
        session.baseRevisionId = parentRevisionId;
        session.deliveryId = deliveryId;
        session.trialRevision = 0;
        session.expiresAt = expiresAt;
        LocalDateTime now = LocalDateTime.now();
        session.createdAt = now;
        session.updatedAt = now;
        session.persist();

        revisionService.setHead(
                workspaceId, OntSessionEntity.sessionScopeKey(sessionId), draftRevisionId);
        return new DraftSessionHandle(sessionId, draftRevisionId, parentRevisionId);
    }

    @Transactional
    public long persistDraftGraph(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            String changeType,
            String entityType,
            String entityId,
            Map<String, Object> payload) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        long changeSeq = revisionService.nextChangeSeq(workspaceId, session.draftRevisionId);
        changeLogService.append(
                workspaceId,
                session.draftRevisionId,
                changeSeq,
                changeType,
                entityType,
                entityId,
                payload);
        upsertService.upsertP0Graph(workspaceId, session.draftRevisionId, graph);
        session.updatedAt = LocalDateTime.now();
        return changeSeq;
    }

    @Transactional
    public long persistSimulateChange(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            String targetType,
            String targetId,
            String property,
            Object value) {
        return persistDraftGraph(
                workspaceId,
                sessionId,
                graph,
                "SIMULATE",
                targetType,
                targetId,
                Map.of(
                        "targetType", targetType,
                        "targetId", targetId,
                        "property", property,
                        "value", value != null ? value : ""));
    }

    @Transactional
    public long persistOptimizeResult(
            String workspaceId,
            String sessionId,
            OntologyGraph graph,
            Map<String, Object> optimizerResultJson) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        session.optimizerResultJson = optimizerResultJson;
        session.trialRevision++;
        return persistDraftGraph(
                workspaceId,
                sessionId,
                graph,
                "OPTIMIZE",
                null,
                null,
                optimizerResultJson != null ? optimizerResultJson : Map.of());
    }

    public OntologyGraph loadDraftSession(String workspaceId, String sessionId) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        return restorer.loadRevision(workspaceId, session.draftRevisionId);
    }

    @Transactional
    public void recordMasterPlanContext(String workspaceId, String sessionId, String basePlanVersionId) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        Map<String, Object> profile = session.solveProfileJson != null
                ? new HashMap<>(session.solveProfileJson)
                : new HashMap<>();
        profile.put(SOLVE_PROFILE_PLAN_VERSION_KEY, basePlanVersionId);
        session.solveProfileJson = profile;
        session.updatedAt = LocalDateTime.now();
    }

    public String resolveMasterPlanVersionId(String workspaceId, String sessionId) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        if (session.solveProfileJson == null) {
            throw new IllegalStateException("ont_session missing solve_profile_json: " + sessionId);
        }
        Object planVersionId = session.solveProfileJson.get(SOLVE_PROFILE_PLAN_VERSION_KEY);
        if (planVersionId == null || planVersionId.toString().isBlank()) {
            throw new IllegalStateException("ont_session missing basePlanVersionId: " + sessionId);
        }
        return planVersionId.toString();
    }

    public Optional<DraftSessionHandle> findDraftSession(String workspaceId, String sessionId) {
        return OntSessionEntity.findSession(workspaceId, sessionId)
                .map(s -> new DraftSessionHandle(s.sessionId, s.draftRevisionId, s.baseRevisionId));
    }

    public long currentChangeSeq(String workspaceId, String sessionId) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        return revisionService.requireRevision(workspaceId, session.draftRevisionId).changeSeq;
    }

    /**
     * P3: promote Session DRAFT revision → COMMITTED and update WORKSPACE / PLAN HEAD pointers.
     */
    @Transactional
    public ConfirmOutcome promoteDraftToCommitted(
            String workspaceId, String sessionId, String planVersionId) {
        OntSessionEntity session = requireSession(workspaceId, sessionId);
        var rev = revisionService.requireRevision(workspaceId, session.draftRevisionId);
        if (!"DRAFT".equals(rev.status)) {
            throw new IllegalStateException("revision not DRAFT: " + rev.revisionId);
        }
        LocalDateTime now = LocalDateTime.now();
        rev.status = "COMMITTED";
        rev.committedAt = now;
        rev.planVersionId = planVersionId;
        rev.updatedAt = now;

        revisionService.setHead(workspaceId, OntologyRevisionService.WORKSPACE_SCOPE, rev.revisionId);
        if (planVersionId != null && !planVersionId.isBlank()) {
            revisionService.setHead(
                    workspaceId, "PLAN:" + planVersionId, rev.revisionId);
        }
        session.updatedAt = now;
        if (dualWriteFeature.enabled()) {
            dualWriteService.syncSupplyOrdersFromWorkOrders(workspaceId, rev.revisionId);
        }
        return new ConfirmOutcome(rev.revisionId, planVersionId);
    }

    public record ConfirmOutcome(String revisionId, String planVersionId) {}

    private OntSessionEntity requireSession(String workspaceId, String sessionId) {
        return OntSessionEntity.findSession(workspaceId, sessionId)
                .orElseThrow(() -> new NotFoundException(
                        "ont_session not found: " + workspaceId + "/" + sessionId));
    }
}
