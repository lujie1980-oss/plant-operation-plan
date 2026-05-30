package com.plantops.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.MasterPlanObjectiveDto;
import com.plantops.api.dto.MasterPlanObjectiveUpdateDto;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.solver.masterplan.MasterPlanObjectiveCatalog;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MasterPlanObjectiveConfigService {

    public static final String PARAM_ID = "master_plan_objective_weights";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> WEIGHT_MAP_TYPE = new TypeReference<>() {
    };

    @Inject
    ParameterRegistry parameters;

    @Inject
    MasterPlanStrategyConfigService strategyConfig;

    public List<MasterPlanObjectiveDto> listObjectives() {
        Map<String, Integer> weights = loadWeights();
        return MasterPlanObjectiveCatalog.all().stream()
                .map(def -> {
                    int weight = weights.getOrDefault(def.id(), def.defaultWeight());
                    return new MasterPlanObjectiveDto(
                            def.id(),
                            def.name(),
                            def.description(),
                            def.penaltyUnit(),
                            weight > 0,
                            weight,
                            def.defaultWeight());
                })
                .toList();
    }

    public MasterPlanObjectiveSettings solverSettings() {
        return strategyConfig.resolveDefault().objectiveSettings();
    }

    @Transactional
    public List<MasterPlanObjectiveDto> saveObjectives(List<MasterPlanObjectiveUpdateDto> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("至少提交一条目标配置");
        }
        Map<String, Integer> weights = new LinkedHashMap<>(MasterPlanObjectiveCatalog.defaults());
        for (MasterPlanObjectiveUpdateDto u : updates) {
            if (u.id() == null || MasterPlanObjectiveCatalog.find(u.id()) == null) {
                throw new IllegalArgumentException("未知优化目标: " + u.id());
            }
            if (u.weight() < 0) {
                throw new IllegalArgumentException("惩罚系数不能为负数: " + u.id());
            }
            weights.put(u.id(), u.enabled() ? u.weight() : 0);
        }
        persistWeights(weights);
        parameters.invalidate(PARAM_ID);
        return listObjectives();
    }

    @Transactional
    public List<MasterPlanObjectiveDto> resetToDefaults() {
        persistWeights(MasterPlanObjectiveCatalog.defaults());
        parameters.invalidate(PARAM_ID);
        return listObjectives();
    }

    private Map<String, Integer> loadWeights() {
        Map<String, Integer> defaults = MasterPlanObjectiveCatalog.defaults();
        String raw = parameters.get(PARAM_ID);
        if (raw == null || raw.isBlank()) {
            return defaults;
        }
        try {
            Map<String, Integer> parsed = MAPPER.readValue(raw, WEIGHT_MAP_TYPE);
            Map<String, Integer> merged = new LinkedHashMap<>(defaults);
            parsed.forEach((id, w) -> {
                if (MasterPlanObjectiveCatalog.find(id) != null && w != null) {
                    merged.put(id, Math.max(0, w));
                }
            });
            return merged;
        } catch (Exception e) {
            return defaults;
        }
    }

    private void persistWeights(Map<String, Integer> weights) {
        try {
            String json = MAPPER.writeValueAsString(weights);
            SystemParameterEntity existing = SystemParameterEntity.findByParamId(PARAM_ID);
            if (existing == null) {
                SystemParameterEntity row = new SystemParameterEntity();
                row.stampWorkspace();
                row.paramId = PARAM_ID;
                row.paramValue = json;
                row.description = "主计划软优化目标惩罚系数（JSON）";
                row.persist();
            } else {
                existing.paramValue = json;
                existing.persist();
            }
        } catch (Exception e) {
            throw new IllegalStateException("保存优化目标配置失败", e);
        }
    }
}
