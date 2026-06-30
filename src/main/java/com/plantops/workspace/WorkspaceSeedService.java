package com.plantops.workspace;

import com.plantops.config.LegacySchemaSupport;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.entity.WorkspaceEnabledAdapterEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.persistence.entity.MaterialEntity;
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

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * 启动时确保演示 workspace 存在并已灌入对应 sample-data。
 */
@ApplicationScoped
public class WorkspaceSeedService {

    private static final Logger log = Logger.getLogger(WorkspaceSeedService.class);

    public static final String MAHLE_ID = "mahle";
    public static final String DUNAN_LITE_ID = "dunan-lite";
    public static final String JINGHUA_ID = "jinghua";
    public static final String TE_ID = "te";
    public static final String SLITTING_DEMO_ID = "slitting-demo";

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    Instance<WorkspaceSeedService> self;

    @Inject
    WorkspaceRegistry workspaceRegistry;

    @Inject
    LegacySchemaSupport legacySchemaSupport;

    void onStart(@Observes StartupEvent event) {
        if (!legacySchemaSupport.isLegacySchemaEnabled()) {
            return;
        }
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
                JINGHUA_ID,
                "晶华新材",
                "晶华新材 MRP测试用例.xlsx 演示数据集",
                "sample-data/factory-jinghua-demo.json");
        ensureWorkspaceWithData(
                TE_ID,
                "TE",
                "TE 100成品子集 + 工艺/规则演示数据集",
                "sample-data/factory-te-demo.json");
        ensureWorkspaceWithData(
                SLITTING_DEMO_ID,
                "分切演示",
                "多场景分切优化演示：宽母卷拼排、N83 多级 BOM、锁定/重算验证",
                "sample-data/factory-slitting-demo.json");
    }

    private void ensureWorkspaceWithData(String workspaceId, String name, String description, String resourcePath) {
        if (!WorkspaceEntity.existsById(workspaceId)) {
            WorkspaceEntity row = new WorkspaceEntity();
            row.workspaceId = workspaceId;
            row.name = name;
            row.description = description;
            row.createdAt = LocalDateTime.now();
            row.isDefault = JINGHUA_ID.equals(workspaceId);
            row.ownerUserId = "dev";
            row.workspaceType = "SHARED";
            row.persist();
            workspaceRegistry.register(workspaceId);

            // IAM M1: dev 用户为 OWNER
            ensureWorkspaceMember(workspaceId, "dev", "OWNER");
            // 默认模块开关
            ensureDefaultModules(workspaceId);
        }
        if (MaterialEntity.count("workspaceId", workspaceId) > 0) {
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

    private void ensureWorkspaceMember(String workspaceId, String userId, String role) {
        if (WorkspaceMemberEntity.count("workspaceId = ?1 and userId = ?2", workspaceId, userId) == 0) {
            WorkspaceMemberEntity m = new WorkspaceMemberEntity();
            m.workspaceId = workspaceId;
            m.userId = userId;
            m.role = role;
            m.persist();
        }
    }

    private void ensureDefaultModules(String workspaceId) {
        String[][] defaults = {
                {"MOD-DI", "true"}, {"MOD-OCP", "true"}, {"MOD-SCH", "true"},
                {"MOD-SLT", "false"}, {"MOD-CAL", "true"}
        };
        for (String[] pair : defaults) {
            if (WorkspaceEnabledModuleEntity.count("workspaceId = ?1 and moduleId = ?2", workspaceId, pair[0]) == 0) {
                WorkspaceEnabledModuleEntity mod = new WorkspaceEnabledModuleEntity();
                mod.workspaceId = workspaceId;
                mod.moduleId = pair[0];
                mod.enabled = Boolean.parseBoolean(pair[1]);
                mod.persist();
            }
        }
        if (WorkspaceEnabledAdapterEntity.count("workspaceId = ?1 and adapterId = ?2", workspaceId, "ADP-EXCEL") == 0) {
            WorkspaceEnabledAdapterEntity adp = new WorkspaceEnabledAdapterEntity();
            adp.workspaceId = workspaceId;
            adp.adapterId = "ADP-EXCEL";
            adp.enabled = true;
            adp.persist();
        }
    }
}
