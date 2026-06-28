package com.plantops.workspace;

import com.plantops.api.dto.WorkspaceCreateRequest;
import com.plantops.api.dto.WorkspaceDto;
import com.plantops.config.ParameterRegistry;
import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.entity.WorkspaceEnabledAdapterEntity;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.masterdata.MasterFieldDefinitionService;
import com.plantops.persistence.entity.*;
import com.plantops.scenario.PlanningScenarioService;
import com.plantops.scenario.RuleSetVersionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class WorkspaceService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    ParameterRegistry parameterRegistry;

    @Inject
    WorkspaceRegistry workspaceRegistry;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    MasterFieldDefinitionService masterFieldDefinitionService;

    @Inject
    SecurityContext securityContext;

    public List<WorkspaceDto> list() {
        return WorkspaceEntity.listAllOrdered().stream().map(this::toDto).toList();
    }

    public WorkspaceDto get(String workspaceId) {
        WorkspaceEntity e = findRequired(workspaceId);
        return toDto(e);
    }

    @Transactional
    public WorkspaceDto create(WorkspaceCreateRequest req) {
        String id = req.id().trim();
        if (!SLUG.matcher(id).matches()) {
            throw new BadRequestException("workspace id 须为小写字母、数字与连字符，且不能以连字符开头或结尾");
        }
        if (WorkspaceConstants.DEFAULT_ID.equals(id)) {
            throw new BadRequestException("不能使用保留 id: default");
        }
        if (WorkspaceEntity.existsById(id)) {
            throw new BadRequestException("workspace 已存在: " + id);
        }
        WorkspaceEntity e = new WorkspaceEntity();
        e.workspaceId = id;
        e.name = req.name().trim();
        e.description = req.description() != null ? req.description().trim() : null;
        e.createdAt = LocalDateTime.now();
        e.isDefault = false;
        e.persist();
        // IAM M1: 三合一创建 — member OWNER + 默认模块 + 默认适配器
        String userId = securityContext.getCurrentUserId();
        if (userId != null) {
            WorkspaceMemberEntity member = new WorkspaceMemberEntity();
            member.workspaceId = id;
            member.userId = userId;
            member.role = "OWNER";
            member.persist();
            ensureDefaultModulesForWorkspace(id);
        }
        String prev = workspaceContext.getWorkspaceId();
        try {
            workspaceContext.setWorkspaceId(id);
            parameterRegistry.ensureDefaults();
            ruleSetVersionService.ensureDefaults();
            planningScenarioService.ensureDefaults();
            masterFieldDefinitionService.cloneDefaultsFromDefaultWorkspace(id);
        } finally {
            workspaceContext.setWorkspaceId(prev);
        }
        return toDto(e);
    }

    @Transactional
    public void delete(String workspaceId) {
        if (WorkspaceConstants.DEFAULT_ID.equals(workspaceId)) {
            throw new BadRequestException("不能删除默认 workspace");
        }
        WorkspaceEntity e = findRequired(workspaceId);
        deleteWorkspaceData(workspaceId);
        e.delete();
        workspaceRegistry.unregister(workspaceId);
    }

    @Transactional
    void deleteWorkspaceData(String workspaceId) {
        // IAM 表
        WorkspaceEnabledModuleEntity.delete("workspaceId", workspaceId);
        WorkspaceEnabledAdapterEntity.delete("workspaceId", workspaceId);
        WorkspaceMemberEntity.delete("workspaceId", workspaceId);
        // 业务表
        DetailScheduleOperationEntity.delete("workspaceId", workspaceId);
        ProductionBatchEntity.delete("workspaceId", workspaceId);
        MasterPlanAllocationEntity.delete("workspaceId", workspaceId);
        LineOpeningDecisionEntity.delete("workspaceId", workspaceId);
        ShortageRecommendationEntity.delete("workspaceId", workspaceId);
        PlanDispatchEntity.delete("workspaceId", workspaceId);
        PlanningEventEntity.delete("workspaceId", workspaceId);
        PlanVersionEntity.delete("workspaceId", workspaceId);
        KittingResultEntity.delete("workspaceId", workspaceId);
        WorkOrderEntity.delete("workspaceId", workspaceId);
        ChangeoverMatrixEntity.delete("workspaceId", workspaceId);
        ParallelOperationRuleEntity.delete("workspaceId", workspaceId);
        OperationTransferTimeRuleEntity.delete("workspaceId", workspaceId);
        ContinuousProductionRuleEntity.delete("workspaceId", workspaceId);
        ProductResourceEntity.delete("workspaceId", workspaceId);
        ProductionLineEntity.delete("workspaceId", workspaceId);
        ResourceCalendarEntity.delete("workspaceId", workspaceId);
        ShiftHeadcountEntity.delete("workspaceId", workspaceId);
        InventoryEntity.delete("workspaceId", workspaceId);
        BomComponentEntity.delete("workspaceId", workspaceId);
        SalesOrderLineEntity.delete("workspaceId", workspaceId);
        ProductionResourceEntity.delete("workspaceId", workspaceId);
        PlanningPipelineRunEntity.delete("workspaceId", workspaceId);
        SystemParameterEntity.delete("workspaceId", workspaceId);
        MasterFieldDefinitionEntity.delete("workspaceId", workspaceId);
        PlanningScenarioEntity.delete("workspaceId", workspaceId);
        RuleSetVersionEntity.delete("workspaceId", workspaceId);
    }

    private WorkspaceEntity findRequired(String workspaceId) {
        WorkspaceEntity e = WorkspaceEntity.findByWorkspaceId(workspaceId);
        if (e == null) {
            throw new NotFoundException("workspace 不存在: " + workspaceId);
        }
        return e;
    }

    private WorkspaceDto toDto(WorkspaceEntity e) {
        return new WorkspaceDto(e.workspaceId, e.name, e.description, e.createdAt, e.isDefault,
                e.ownerUserId, e.workspaceType);
    }

    private void ensureDefaultModulesForWorkspace(String workspaceId) {
        String[][] defaults = {
                {"MOD-DI", "true"}, {"MOD-OCP", "true"}, {"MOD-SCH", "true"},
                {"MOD-SLT", "false"}, {"MOD-CAL", "true"}
        };
        for (String[] pair : defaults) {
            WorkspaceEnabledModuleEntity mod = new WorkspaceEnabledModuleEntity();
            mod.workspaceId = workspaceId;
            mod.moduleId = pair[0];
            mod.enabled = Boolean.parseBoolean(pair[1]);
            mod.persist();
        }
        WorkspaceEnabledAdapterEntity adp = new WorkspaceEnabledAdapterEntity();
        adp.workspaceId = workspaceId;
        adp.adapterId = "ADP-EXCEL";
        adp.enabled = true;
        adp.persist();
    }
}
