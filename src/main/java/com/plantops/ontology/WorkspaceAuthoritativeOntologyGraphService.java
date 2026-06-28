package com.plantops.ontology;

import com.plantops.rol.RolEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-07：每个 Workspace + planVersion 键维护<strong>一张</strong>权威 {@link OntologyGraph}，
 * 供 ENT-SES、ENT-SBX 与只读 API 共享（simulate / optimize / confirm 禁止并行投影图 SoT）。
 */
@ApplicationScoped
public class WorkspaceAuthoritativeOntologyGraphService {

    @Inject
    OntologyLoader ontologyLoader;

    private final ConcurrentHashMap<String, OntologyGraph> graphsByKey = new ConcurrentHashMap<>();

    public OntologyGraph getOrLoad(String workspaceId, String planVersionId) {
        String key = graphKey(workspaceId, planVersionId);
        return graphsByKey.computeIfAbsent(key, ignored -> loadFresh(planVersionId));
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

    private OntologyGraph loadFresh(String planVersionId) {
        if (planVersionId != null && !planVersionId.isBlank()) {
            return ontologyLoader.loadForPlanVersion(planVersionId);
        }
        return ontologyLoader.loadForWorkspace(LocalDate.now());
    }
}
