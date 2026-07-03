package com.plantops.workspace;

import com.plantops.config.LegacySchemaSupport;
import com.plantops.persistence.entity.WorkspaceEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存已知 workspace id，避免每个 HTTP 请求在过滤器中查库（减轻连接池与 H2 锁竞争）。
 */
@ApplicationScoped
public class WorkspaceRegistry {

    private final Set<String> knownIds = ConcurrentHashMap.newKeySet();

    @Inject
    LegacySchemaSupport legacySchemaSupport;

    void onStart(@Observes StartupEvent event) {
        if (!legacySchemaSupport.isLegacySchemaEnabled()) {
            knownIds.add(WorkspaceConstants.DEFAULT_ID);
            return;
        }
        reloadFromDatabase();
    }

    public boolean exists(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return false;
        }
        if (WorkspaceConstants.DEFAULT_ID.equals(workspaceId)) {
            return true;
        }
        return knownIds.contains(workspaceId);
    }

    public void register(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            knownIds.add(workspaceId);
        }
    }

    public void unregister(String workspaceId) {
        if (workspaceId != null) {
            knownIds.remove(workspaceId);
        }
    }

    public void reloadFromDatabase() {
        knownIds.clear();
        WorkspaceEntity.<WorkspaceEntity>listAll().forEach(row -> knownIds.add(row.workspaceId));
        knownIds.add(WorkspaceConstants.DEFAULT_ID);
    }
}
