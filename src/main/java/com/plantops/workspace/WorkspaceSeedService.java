package com.plantops.workspace;

import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.sample.SampleDataLoader;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * 启动时确保马勒 / 盾安演示 workspace 存在并已灌入对应 sample-data。
 */
@ApplicationScoped
public class WorkspaceSeedService {

    private static final Logger log = Logger.getLogger(WorkspaceSeedService.class);

    public static final String MAHLE_ID = "mahle";
    public static final String DUNAN_LITE_ID = "dunan-lite";

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    Instance<WorkspaceSeedService> self;

    @Inject
    WorkspaceRegistry workspaceRegistry;

    void onStart(@Observes StartupEvent event) {
        try {
            self.get().seedDemoWorkspaces();
        } catch (Exception e) {
            log.errorf(e, "演示 workspace 种子数据加载失败（可稍后通过管理页或 reload-sample-data 重试）");
        } finally {
            workspaceRegistry.reloadFromDatabase();
        }
    }

    @Transactional
    public void seedDemoWorkspaces() {
        ensureWorkspaceWithData(
                MAHLE_ID,
                "马勒演示",
                "马勒 factory-demo 演示数据集",
                "sample-data/factory-demo.json");
        ensureWorkspaceWithData(
                DUNAN_LITE_ID,
                "盾安 Lite",
                "盾安精简演示数据集（dunan-lite）",
                "sample-data/factory-dunan-demo-lite.json");
    }

    private void ensureWorkspaceWithData(String workspaceId, String name, String description, String resourcePath) {
        if (!WorkspaceEntity.existsById(workspaceId)) {
            WorkspaceEntity row = new WorkspaceEntity();
            row.workspaceId = workspaceId;
            row.name = name;
            row.description = description;
            row.createdAt = LocalDateTime.now();
            row.isDefault = false;
            row.persist();
            workspaceRegistry.register(workspaceId);
        }
        if (SalesOrderLineEntity.count("workspaceId", workspaceId) > 0) {
            return;
        }
        String prev = workspaceContext.getWorkspaceId();
        try {
            workspaceContext.setWorkspaceId(workspaceId);
            sampleDataLoader.reloadDemo(resourcePath);
        } finally {
            workspaceContext.setWorkspaceId(prev);
        }
    }
}
