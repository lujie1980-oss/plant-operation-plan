package com.plantops.ontology;

import com.plantops.config.OntologyRestorerReadFeature;
import com.plantops.ontology.persistence.OntologyP0Overlay;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.ontology.persistence.OntologyRevisionService;
import com.plantops.ontology.persistence.OntologyWorkspaceHeadBootstrapService;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import com.plantops.rol.RolEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-07：每个 Workspace + planVersion 键维护<strong>一张</strong>权威 {@link OntologyGraph}，
 * 供 ENT-SES、ENT-SBX 与只读 API 共享（simulate / optimize / confirm 禁止并行投影图 SoT）。
 */
@ApplicationScoped
public class WorkspaceAuthoritativeOntologyGraphService {

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyRestorerReadFeature restorerReadFeature;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    OntologyWorkspaceHeadBootstrapService workspaceHeadBootstrap;

    @Inject
    com.plantops.config.OntologyWorkspaceHeadBootstrapFeature workspaceHeadBootstrapFeature;

    private final ConcurrentHashMap<String, OntologyGraph> graphsByKey = new ConcurrentHashMap<>();

    public OntologyGraph getOrLoad(String workspaceId, String planVersionId) {
        String key = graphKey(workspaceId, planVersionId);
        return graphsByKey.computeIfAbsent(key, ignored -> loadFresh(workspaceId, planVersionId));
    }

    public RolEngine newRolEngine(OntologyGraph graph) {
        return RolEngine.withMasterPlanRules(graph);
    }

    /**
     * DB 或计划结构变更后丢弃缓存，下次 {@link #getOrLoad} 重新装载。
     */
    public void invalidate(String workspaceId, String planVersionId) {
        graphsByKey.remove(graphKey(workspaceId, planVersionId));
    }

    public void invalidateWorkspace(String workspaceId) {
        String prefix = workspaceId + "|";
        graphsByKey.keySet().removeIf(key -> key.startsWith(prefix));
    }

    static String graphKey(String workspaceId, String planVersionId) {
        String normalizedWorkspace = workspaceId == null ? "" : workspaceId;
        String normalizedPlan = planVersionId == null || planVersionId.isBlank() ? "" : planVersionId.trim();
        return normalizedWorkspace + "|" + normalizedPlan;
    }

    private OntologyGraph loadFresh(String workspaceId, String planVersionId) {
        OntologyGraph loaderGraph = planVersionId != null && !planVersionId.isBlank()
                ? ontologyLoader.loadForPlanVersion(planVersionId)
                : ontologyLoader.loadForWorkspace(LocalDate.now());

        if (!restorerReadFeature.enabled()) {
            return loaderGraph;
        }

        if (workspaceHeadBootstrapFeature.enabled()) {
            workspaceHeadBootstrap.ensureWorkspaceHead(workspaceId);
        }

        String revisionId = resolveCommittedRevisionId(workspaceId, planVersionId);
        if (revisionId == null) {
            return loaderGraph;
        }

        OntologyGraph restoredP0 = ontologyPersistence.loadRevision(workspaceId, revisionId);
        return OntologyP0Overlay.apply(loaderGraph, restoredP0);
    }

    private String resolveCommittedRevisionId(String workspaceId, String planVersionId) {
        if (planVersionId != null && !planVersionId.isBlank()) {
            String planScope = "PLAN:" + planVersionId.trim();
            Optional<String> planHead = OntRevisionHeadEntity.findHead(workspaceId, planScope)
                    .map(h -> h.revisionId);
            if (planHead.isPresent()) {
                return planHead.get();
            }
        }
        return OntRevisionHeadEntity.findHead(workspaceId, OntologyRevisionService.WORKSPACE_SCOPE)
                .map(h -> h.revisionId)
                .orElse(null);
    }
}
