package com.plantops.scenario;

import com.plantops.api.dto.CreatePlanningScenarioRequest;
import com.plantops.api.dto.PlanningScenarioDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.*;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PlanningScenarioService {

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    WorkspaceContext workspaceContext;

    public List<PlanningScenarioDto> list() {
        ensureDefaults();
        return PlanningScenarioEntity.listInWorkspace().stream()
                .map(this::toDto)
                .toList();
    }

    public PlanningScenarioDto get(String scenarioId) {
        return toDto(findRequired(scenarioId));
    }

    @Transactional
    public PlanningScenarioDto create(CreatePlanningScenarioRequest req) {
        ensureDefaults();
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("场景名称不能为空");
        }
        String ruleSetVersionId = req.ruleSetVersionId();
        if (ruleSetVersionId == null || ruleSetVersionId.isBlank()) {
            ruleSetVersionId = ruleSetVersionService.ensureDefaults().ruleSetVersionId;
        } else if (RuleSetVersionEntity.findById(ruleSetVersionId) == null) {
            throw new BadRequestException("规则版本不存在: " + ruleSetVersionId);
        }
        String strategyId = req.strategyId();
        if (strategyId != null && !strategyId.isBlank()) {
            strategyConfigService.resolve(strategyId);
        }
        PlanningScenarioEntity row = new PlanningScenarioEntity();
        row.scenarioId = "SCN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        row.name = req.name().trim();
        row.isDefault = false;
        row.strategyId = strategyId;
        row.ruleSetVersionId = ruleSetVersionId;
        row.createdAt = LocalDateTime.now();
        row.stampWorkspace();
        row.persist();
        return toDto(row);
    }

    /**
     * 将新的主计划版本登记到场景：仅保留当前 + 上一版，删除更早版本数据。
     */
    @Transactional
    public void recordMasterPlanVersion(String scenarioId, String planVersionId) {
        if (scenarioId == null || scenarioId.isBlank() || planVersionId == null || planVersionId.isBlank()) {
            return;
        }
        PlanningScenarioEntity scenario = findRequired(scenarioId);
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
        if (version == null) {
            return;
        }
        version.scenarioId = scenarioId;
        if (scenario.currentPlanVersionId != null
                && !scenario.currentPlanVersionId.equals(planVersionId)) {
            purgePlanVersionData(scenario.previousPlanVersionId);
            PlanVersionEntity oldPrevious = scenario.previousPlanVersionId != null
                    ? PlanVersionEntity.findByVersionId(scenario.previousPlanVersionId)
                    : null;
            if (oldPrevious != null) {
                oldPrevious.versionStatus = "ARCHIVED";
                oldPrevious.persist();
            }
            PlanVersionEntity oldCurrent = PlanVersionEntity.findByVersionId(scenario.currentPlanVersionId);
            if (oldCurrent != null) {
                oldCurrent.versionStatus = "PREVIOUS";
                oldCurrent.persist();
            }
            scenario.previousPlanVersionId = scenario.currentPlanVersionId;
        }
        version.versionStatus = "CURRENT";
        version.persist();
        scenario.currentPlanVersionId = planVersionId;
        scenario.persist();
    }

    @Transactional
    public PlanningScenarioEntity ensureDefaults() {
        ruleSetVersionService.ensureDefaults();
        PlanningScenarioEntity existing = PlanningScenarioEntity.findDefault();
        if (existing != null) {
            linkWorkspaceDefault(existing);
            backfillIfNeeded(existing);
            return existing;
        }
        RuleSetVersionEntity rule = RuleSetVersionEntity.findDefault();
        PlanningScenarioEntity row = new PlanningScenarioEntity();
        row.scenarioId = "SCN-DEFAULT";
        row.name = "默认场景";
        row.isDefault = true;
        row.ruleSetVersionId = rule != null ? rule.ruleSetVersionId : "RSV-DEFAULT";
        row.createdAt = LocalDateTime.now();
        row.stampWorkspace();
        row.persist();
        linkWorkspaceDefault(row);
        backfillIfNeeded(row);
        return row;
    }

    private void linkWorkspaceDefault(PlanningScenarioEntity scenario) {
        WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(workspaceContext.getWorkspaceId());
        if (ws != null && (ws.defaultScenarioId == null || ws.defaultScenarioId.isBlank())) {
            ws.defaultScenarioId = scenario.scenarioId;
            ws.persist();
        }
    }

    private void backfillIfNeeded(PlanningScenarioEntity scenario) {
        if (scenario.currentPlanVersionId != null) {
            return;
        }
        List<PlanVersionEntity> masters = PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> "MASTER_PLAN".equals(v.planType))
                .sorted(Comparator.comparing(
                        (PlanVersionEntity v) -> v.planGeneratedTs,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (masters.isEmpty()) {
            return;
        }
        PlanVersionEntity current = masters.get(0);
        current.scenarioId = scenario.scenarioId;
        current.versionStatus = "CURRENT";
        current.persist();
        scenario.currentPlanVersionId = current.planVersionId;
        if (masters.size() > 1) {
            PlanVersionEntity previous = masters.get(1);
            previous.scenarioId = scenario.scenarioId;
            previous.versionStatus = "PREVIOUS";
            previous.persist();
            scenario.previousPlanVersionId = previous.planVersionId;
            for (int i = 2; i < masters.size(); i++) {
                purgePlanVersionData(masters.get(i).planVersionId);
            }
        }
        scenario.persist();
    }

    private void purgePlanVersionData(String planVersionId) {
        if (planVersionId == null || planVersionId.isBlank()) {
            return;
        }
        MasterPlanAllocationEntity.delete("workspaceId = ?1 and planVersionId = ?2", PlanVersionEntity.ws(), planVersionId);
        LineOpeningDecisionEntity.delete("workspaceId = ?1 and planVersionId = ?2", PlanVersionEntity.ws(), planVersionId);
        PlanVersionEntity v = PlanVersionEntity.findByVersionId(planVersionId);
        if (v != null) {
            v.delete();
        }
    }

    private PlanningScenarioEntity findRequired(String scenarioId) {
        PlanningScenarioEntity e = PlanningScenarioEntity.findByScenarioId(scenarioId);
        if (e == null) {
            throw new NotFoundException("场景不存在: " + scenarioId);
        }
        return e;
    }

    private PlanningScenarioDto toDto(PlanningScenarioEntity s) {
        PlanVersionEntity current = s.currentPlanVersionId != null
                ? PlanVersionEntity.findByVersionId(s.currentPlanVersionId)
                : null;
        RuleSetVersionEntity rule = RuleSetVersionEntity.findById(s.ruleSetVersionId);
        String strategyName = null;
        String capacityStrategy = null;
        if (s.strategyId != null && !s.strategyId.isBlank()) {
            try {
                var resolved = strategyConfigService.resolve(s.strategyId);
                strategyName = resolved.name();
                capacityStrategy = resolved.capacityStrategy().name();
            } catch (Exception ignored) {
                // strategy may have been removed
            }
        } else if (current != null) {
            strategyName = current.strategyName;
            capacityStrategy = current.capacityStrategy;
        }
        if (strategyName == null && current != null) {
            strategyName = current.strategyName;
        }
        if (capacityStrategy == null && current != null) {
            capacityStrategy = current.capacityStrategy;
        }
        PlanningPipelineRunEntity run = current != null
                ? PlanningPipelineRunEntity
                        .find("masterPlanVersionId = ?1 order by startedTs desc", current.planVersionId)
                        .firstResult()
                : null;
        String label = s.name;
        if (current != null) {
            label = s.name + " · " + current.planVersionId;
        }
        String activeVersionId = current != null ? current.planVersionId : null;
        return new PlanningScenarioDto(
                s.scenarioId,
                s.name,
                s.isDefault,
                s.strategyId,
                strategyName,
                s.ruleSetVersionId,
                rule != null ? rule.name : null,
                activeVersionId,
                s.previousPlanVersionId,
                current != null && current.planGeneratedTs != null ? current.planGeneratedTs.toString() : null,
                current != null ? current.score : null,
                current != null ? current.solveDurationMs : null,
                activeVersionId,
                run != null ? run.runId : null,
                label,
                capacityStrategy != null ? capacityStrategy : "UNCONSTRAINED",
                current != null && current.planGeneratedTs != null ? current.planGeneratedTs.toString() : null,
                current != null ? current.score : null,
                current != null ? current.solveDurationMs : null);
    }
}
