package com.plantops.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.MasterPlanObjectiveDto;
import com.plantops.api.dto.MasterPlanObjectiveUpdateDto;
import com.plantops.api.dto.MasterPlanStrategyCreateRequest;
import com.plantops.api.dto.MasterPlanStrategyDetailDto;
import com.plantops.api.dto.MasterPlanStrategySummaryDto;
import com.plantops.api.dto.MasterPlanStrategyUpdateRequest;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanObjectiveCatalog;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MasterPlanStrategyConfigService {

    public static final String PARAM_ID = "master_plan_strategies";

    /** 无限产能策略 id；勿使用 {@code default}（与 GET /strategies/default 保留路径冲突）。 */
    public static final String UNCONSTRAINED_STRATEGY_ID = "unconstrained";

    public record ResolvedStrategy(
            String id,
            String name,
            MasterPlanCapacityStrategy capacityStrategy,
            MasterPlanObjectiveSettings objectiveSettings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrategyRecord(
            String id,
            String name,
            String capacityStrategy,
            Map<String, Integer> objectiveWeights) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrategiesStore(String defaultStrategyId, List<StrategyRecord> strategies) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> WEIGHT_MAP_TYPE = new TypeReference<>() {
    };

    @Inject
    ParameterRegistry parameters;

    public List<MasterPlanStrategySummaryDto> listSummaries() {
        StrategiesStore store = loadStore();
        return store.strategies().stream()
                .map(s -> new MasterPlanStrategySummaryDto(
                        s.id(),
                        s.name(),
                        s.capacityStrategy(),
                        s.id().equals(store.defaultStrategyId())))
                .toList();
    }

    public MasterPlanStrategyDetailDto getDetail(String strategyId) {
        StrategiesStore store = loadStore();
        StrategyRecord record = findRecord(store, normalizeStrategyId(strategyId));
        if (record == null) {
            throw new NotFoundException("策略不存在: " + strategyId);
        }
        return toDetail(record, store.defaultStrategyId());
    }

    public MasterPlanStrategyDetailDto getDefaultDetail() {
        StrategiesStore store = loadStore();
        StrategyRecord record = findRecord(store, store.defaultStrategyId());
        if (record == null) {
            throw new IllegalStateException("未配置默认策略");
        }
        return toDetail(record, store.defaultStrategyId());
    }

    public ResolvedStrategy resolve(String strategyId) {
        StrategiesStore store = loadStore();
        String effectiveId = strategyId != null && !strategyId.isBlank()
                ? normalizeStrategyId(strategyId)
                : store.defaultStrategyId();
        StrategyRecord record = findRecord(store, effectiveId);
        if (record == null) {
            throw new NotFoundException("策略不存在: " + effectiveId);
        }
        return toResolved(record);
    }

    public ResolvedStrategy resolveDefault() {
        StrategiesStore store = loadStore();
        StrategyRecord record = findRecord(store, store.defaultStrategyId());
        if (record == null) {
            throw new IllegalStateException("未配置默认策略");
        }
        return toResolved(record);
    }

    /**
     * 解析运行请求：优先 strategyId；否则按 capacityStrategy 覆盖默认策略的产能模式（兼容旧 API）。
     */
    public ResolvedStrategy resolveFromRequest(String strategyId, String capacityStrategy) {
        if (strategyId != null && !strategyId.isBlank()) {
            return resolve(strategyId);
        }
        ResolvedStrategy def = resolveDefault();
        if (capacityStrategy == null || capacityStrategy.isBlank()) {
            return def;
        }
        MasterPlanCapacityStrategy cap = MasterPlanCapacityStrategy.fromString(capacityStrategy);
        if (cap == def.capacityStrategy()) {
            return def;
        }
        return new ResolvedStrategy(def.id(), def.name(), cap, def.objectiveSettings());
    }

    @Transactional
    public MasterPlanStrategyDetailDto create(MasterPlanStrategyCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("策略名称不能为空");
        }
        StrategiesStore store = loadStore();
        String id = "strategy-" + UUID.randomUUID().toString().substring(0, 8);
        StrategyRecord record = new StrategyRecord(
                id,
                request.name().trim(),
                normalizeCapacity(request.capacityStrategy()),
                normalizeWeights(request.objectives()));
        List<StrategyRecord> strategies = new ArrayList<>(store.strategies());
        strategies.add(record);
        persistStore(new StrategiesStore(store.defaultStrategyId(), strategies));
        return toDetail(record, store.defaultStrategyId());
    }

    @Transactional
    public MasterPlanStrategyDetailDto update(String strategyId, MasterPlanStrategyUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        StrategiesStore store = loadStore();
        String normalizedId = normalizeStrategyId(strategyId);
        StrategyRecord existing = findRecord(store, normalizedId);
        if (existing == null) {
            throw new NotFoundException("策略不存在: " + strategyId);
        }
        String name = request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : existing.name();
        String capacity = request.capacityStrategy() != null && !request.capacityStrategy().isBlank()
                ? normalizeCapacity(request.capacityStrategy())
                : existing.capacityStrategy();
        Map<String, Integer> weights = request.objectives() != null
                ? normalizeWeights(request.objectives())
                : existing.objectiveWeights();
        StrategyRecord updated = new StrategyRecord(normalizedId, name, capacity, weights);
        List<StrategyRecord> strategies = new ArrayList<>();
        for (StrategyRecord s : store.strategies()) {
            strategies.add(s.id().equals(normalizedId) ? updated : s);
        }
        String defaultId = Boolean.TRUE.equals(request.setAsDefault()) ? normalizedId : store.defaultStrategyId();
        persistStore(new StrategiesStore(defaultId, strategies));
        return toDetail(updated, defaultId);
    }

    @Transactional
    public void delete(String strategyId) {
        String normalizedId = normalizeStrategyId(strategyId);
        StrategiesStore store = loadStore();
        if (store.strategies().size() <= 1) {
            throw new IllegalArgumentException("至少保留一个策略");
        }
        StrategyRecord existing = findRecord(store, normalizedId);
        if (existing == null) {
            throw new NotFoundException("策略不存在: " + strategyId);
        }
        List<StrategyRecord> strategies = store.strategies().stream()
                .filter(s -> !s.id().equals(normalizedId))
                .toList();
        String defaultId = store.defaultStrategyId();
        if (normalizedId.equals(defaultId)) {
            defaultId = strategies.get(0).id();
        }
        persistStore(new StrategiesStore(defaultId, strategies));
    }

    @Transactional
    public MasterPlanStrategyDetailDto duplicate(String strategyId, String newName) {
        StrategiesStore store = loadStore();
        StrategyRecord source = findRecord(store, normalizeStrategyId(strategyId));
        if (source == null) {
            throw new NotFoundException("策略不存在: " + strategyId);
        }
        String name = newName != null && !newName.isBlank()
                ? newName.trim()
                : source.name() + "（副本）";
        return create(new MasterPlanStrategyCreateRequest(
                name,
                source.capacityStrategy(),
                toObjectiveUpdates(source.objectiveWeights())));
    }

    private StrategiesStore loadStore() {
        String raw = parameters.get(PARAM_ID);
        if (raw != null && !raw.isBlank()) {
            try {
                StrategiesStore parsed = MAPPER.readValue(raw, StrategiesStore.class);
                if (parsed.strategies() != null && !parsed.strategies().isEmpty()) {
                    return loadNormalizedStore(parsed);
                }
            } catch (Exception ignored) {
                // migrate below
            }
        }
        StrategiesStore migrated = migrateFromLegacy();
        QuarkusTransaction.requiringNew().run(() -> persistStore(migrated));
        parameters.invalidate(PARAM_ID);
        return migrated;
    }

    private StrategiesStore loadNormalizedStore(StrategiesStore parsed) {
        StrategiesStore normalized = normalizeStore(parsed);
        StrategiesStore remapped = remapReservedStrategyIds(normalized);
        if (!remapped.equals(normalized)) {
            QuarkusTransaction.requiringNew().run(() -> persistStore(remapped));
            parameters.invalidate(PARAM_ID);
        }
        return remapped;
    }

    /**
     * 历史数据使用策略 id {@code default}，与 REST {@code GET .../strategies/default}（查当前默认策略）冲突。
     */
    private StrategiesStore remapReservedStrategyIds(StrategiesStore store) {
        boolean usesReservedId = "default".equals(store.defaultStrategyId())
                || store.strategies().stream().anyMatch(s -> "default".equals(s.id()));
        if (!usesReservedId) {
            return store;
        }
        String defaultId = store.defaultStrategyId();
        List<StrategyRecord> strategies = new ArrayList<>();
        for (StrategyRecord s : store.strategies()) {
            String id = "default".equals(s.id()) ? UNCONSTRAINED_STRATEGY_ID : s.id();
            strategies.add(new StrategyRecord(id, s.name(), s.capacityStrategy(), s.objectiveWeights()));
        }
        if ("default".equals(defaultId)) {
            defaultId = UNCONSTRAINED_STRATEGY_ID;
        }
        return new StrategiesStore(defaultId, strategies);
    }

    private StrategiesStore migrateFromLegacy() {
        Map<String, Integer> weights = loadLegacyWeights();
        StrategyRecord unconstrainedStrategy = new StrategyRecord(
                UNCONSTRAINED_STRATEGY_ID,
                "默认策略",
                MasterPlanCapacityStrategy.UNCONSTRAINED.name(),
                weights);
        StrategyRecord finiteStrategy = new StrategyRecord(
                "finite-capacity",
                "有限产能",
                MasterPlanCapacityStrategy.FINITE_CAPACITY.name(),
                MasterPlanObjectiveCatalog.defaults());
        return new StrategiesStore(
                UNCONSTRAINED_STRATEGY_ID,
                List.of(unconstrainedStrategy, finiteStrategy));
    }

    private Map<String, Integer> loadLegacyWeights() {
        String raw = parameters.get(MasterPlanObjectiveConfigService.PARAM_ID);
        Map<String, Integer> defaults = MasterPlanObjectiveCatalog.defaults();
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

    private StrategiesStore normalizeStore(StrategiesStore store) {
        String defaultId = store.defaultStrategyId();
        List<StrategyRecord> normalized = new ArrayList<>();
        for (StrategyRecord s : store.strategies()) {
            normalized.add(new StrategyRecord(
                    s.id(),
                    s.name(),
                    normalizeCapacity(s.capacityStrategy()),
                    mergeWithDefaults(s.objectiveWeights())));
        }
        if (defaultId == null || findRecord(new StrategiesStore(defaultId, normalized), defaultId) == null) {
            defaultId = normalized.get(0).id();
        }
        return new StrategiesStore(defaultId, normalized);
    }

    private void persistStore(StrategiesStore store) {
        try {
            String json = MAPPER.writeValueAsString(store);
            SystemParameterEntity existing = SystemParameterEntity.findByParamId(PARAM_ID);
            if (existing == null) {
                SystemParameterEntity row = new SystemParameterEntity();
                row.stampWorkspace();
                row.paramId = PARAM_ID;
                row.paramValue = json;
                row.description = "主计划运行策略（产能模式 + 优化目标权重 JSON）";
                row.persist();
            } else {
                existing.paramValue = json;
                existing.persist();
            }
            parameters.invalidate(PARAM_ID);
        } catch (Exception e) {
            throw new IllegalStateException("保存策略配置失败", e);
        }
    }

    private static String normalizeStrategyId(String strategyId) {
        if (strategyId == null) {
            return null;
        }
        return "default".equals(strategyId) ? UNCONSTRAINED_STRATEGY_ID : strategyId;
    }

    private static StrategyRecord findRecord(StrategiesStore store, String strategyId) {
        if (strategyId == null) {
            return null;
        }
        String id = normalizeStrategyId(strategyId);
        return store.strategies().stream()
                .filter(s -> id.equals(s.id()))
                .findFirst()
                .orElse(null);
    }

    private MasterPlanStrategyDetailDto toDetail(StrategyRecord record, String defaultStrategyId) {
        return new MasterPlanStrategyDetailDto(
                record.id(),
                record.name(),
                record.capacityStrategy(),
                record.id().equals(defaultStrategyId),
                objectivesFromWeights(record.objectiveWeights()));
    }

    private ResolvedStrategy toResolved(StrategyRecord record) {
        return new ResolvedStrategy(
                record.id(),
                record.name(),
                MasterPlanCapacityStrategy.fromString(record.capacityStrategy()),
                new MasterPlanObjectiveSettings(record.objectiveWeights()));
    }

    private List<MasterPlanObjectiveDto> objectivesFromWeights(Map<String, Integer> weights) {
        Map<String, Integer> merged = mergeWithDefaults(weights);
        return MasterPlanObjectiveCatalog.all().stream()
                .map(def -> {
                    int weight = merged.getOrDefault(def.id(), def.defaultWeight());
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

    private Map<String, Integer> mergeWithDefaults(Map<String, Integer> weights) {
        Map<String, Integer> merged = new LinkedHashMap<>(MasterPlanObjectiveCatalog.defaults());
        if (weights != null) {
            weights.forEach((id, w) -> {
                if (MasterPlanObjectiveCatalog.find(id) != null && w != null) {
                    merged.put(id, Math.max(0, w));
                }
            });
        }
        return merged;
    }

    private Map<String, Integer> normalizeWeights(List<MasterPlanObjectiveUpdateDto> updates) {
        Map<String, Integer> weights = new LinkedHashMap<>(MasterPlanObjectiveCatalog.defaults());
        if (updates == null) {
            return weights;
        }
        for (MasterPlanObjectiveUpdateDto u : updates) {
            if (u.id() == null || MasterPlanObjectiveCatalog.find(u.id()) == null) {
                throw new IllegalArgumentException("未知优化目标: " + u.id());
            }
            if (u.weight() < 0) {
                throw new IllegalArgumentException("惩罚系数不能为负数: " + u.id());
            }
            weights.put(u.id(), u.enabled() ? u.weight() : 0);
        }
        return weights;
    }

    private static String normalizeCapacity(String raw) {
        return MasterPlanCapacityStrategy.fromString(raw).name();
    }

    private static List<MasterPlanObjectiveUpdateDto> toObjectiveUpdates(Map<String, Integer> weights) {
        Map<String, Integer> merged = weights != null ? weights : Map.of();
        return MasterPlanObjectiveCatalog.all().stream()
                .map(def -> {
                    int weight = merged.getOrDefault(def.id(), def.defaultWeight());
                    return new MasterPlanObjectiveUpdateDto(def.id(), weight > 0, weight);
                })
                .toList();
    }
}
