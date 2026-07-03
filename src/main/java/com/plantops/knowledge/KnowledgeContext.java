package com.plantops.knowledge;

import com.plantops.persistence.entity.KnowledgeOverlayEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 运行时 KnowledgeContext：解析当前 Workspace 的 Effective Knowledge（TODO-15 K0）。 */
@ApplicationScoped
public class KnowledgeContext {

    private static final long CACHE_TTL_MS = 60_000L;

    @Inject
    KnowledgeResolver resolver;

    @Inject
    WorkspaceContext workspaceContext;

    private final ConcurrentHashMap<String, CachedEffective> cache = new ConcurrentHashMap<>();

    public EffectiveKnowledge resolve() {
        return resolve(workspaceContext.getWorkspaceId());
    }

    public EffectiveKnowledge resolve(String workspaceId) {
        long now = System.currentTimeMillis();
        CachedEffective cached = cache.get(workspaceId);
        if (cached != null && now - cached.loadedAtMs < CACHE_TTL_MS) {
            return cached.effective;
        }
        EffectiveKnowledge effective = load(workspaceId);
        cache.put(workspaceId, new CachedEffective(effective, now));
        return effective;
    }

    public String getParameter(String paramId) {
        if (paramId == null || paramId.isBlank()) {
            return null;
        }
        return resolve().getString(paramId);
    }

    public void invalidate(String workspaceId) {
        if (workspaceId == null) {
            cache.clear();
            return;
        }
        cache.remove(workspaceId);
    }

    private EffectiveKnowledge load(String workspaceId) {
        WorkspaceEntity workspace = WorkspaceEntity.findByWorkspaceId(workspaceId);
        String industryId = workspace != null ? workspace.industryId : null;
        List<KnowledgeOverlayEntity> overlays = KnowledgeOverlayEntity.listInWorkspace(workspaceId);
        Map<String, String> workspaceParameters = SystemParameterEntity.listInWorkspace(workspaceId).stream()
                .filter(p -> p.paramId != null && p.paramValue != null)
                .collect(Collectors.toMap(p -> p.paramId, p -> p.paramValue, (a, b) -> b));
        return resolver.resolve(workspaceId, industryId, overlays, workspaceParameters);
    }

    private record CachedEffective(EffectiveKnowledge effective, long loadedAtMs) {}
}
