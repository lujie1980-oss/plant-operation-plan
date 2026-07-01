package com.plantops.config;

import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ParameterRegistry {

    private static final String OLD_DETAIL_SCHEDULE_CONTRACT =
            "{\"weight_due\":100,\"weight_mp_late\":20,\"weight_mp_early\":8,"
                    + "\"mp_late_mode\":\"LINEAR\",\"mp_early_mode\":\"CAPPED\",\"mp_early_cap_days\":3}";

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("kitting_lock_t_hours", "24"),
            Map.entry("freeze_window_days", "2"),
            Map.entry("planning_horizon_days", "70"),
            Map.entry("timeslot_granularity_mode", "DAILY_THEN_WEEKLY"),
            Map.entry("timeslot_daily_days", "28"),
            Map.entry("timeslot_weekly_buckets", "6"),
            Map.entry("master_plan_solver_seconds", "30"),
            Map.entry("detail_schedule_solver_seconds", "30"),
  Map.entry("slitting_solver_seconds", "30"),
            Map.entry("slitting_session_solver_seconds", "10"),
            Map.entry("slitting_default_child_width_mm", "200"),
            Map.entry("slitting_default_child_length_mm", "1000"),
            Map.entry("capacity_overload_threshold_pct", "110"),
            Map.entry("master_plan_material_constraint_enabled", "false"),
            Map.entry("master_plan_objective_weights",
                    "{\"minimize_lateness\":10,\"prioritize_high_priority\":1,\"locked_orders_prefer_earlier\":1,\"balance_adjacent_slot_loading\":1,\"concentrate_capacity\":1,\"minimize_slot_changeover\":1}"),
            Map.entry("detail_schedule_contract",
                    ScheduleContractConfigService.DEFAULT_CONTRACT_JSON),
            Map.entry("default_procurement_lead_time_days", "7"),
            Map.entry("batch_split_mode", "NONE"),
            Map.entry("batch_fixed_qty", "100"),
            Map.entry("batch_remainder_mode", "SEPARATE_TAIL"),
            Map.entry("batch_auto_on_dispatch", "false"),
            Map.entry("batch_kitting_create_short_batch", "true"),
            Map.entry("batch_min_qty", "10"),
            Map.entry("batch_max_qty", "200"),
            Map.entry("planning_optimizer_engine", "ortools"),
            Map.entry("master_plan_multi_resource_split", "false"),
            Map.entry("master_plan_jit_warm_start", "true"),
            Map.entry("master_plan_demand_scale", "0.01"));

    private static final Map<String, String> PARAM_DESCRIPTIONS = Map.of(
            "slitting_solver_seconds",
            "整方案分切（方案级 solve）Timefold 求解最长运行秒数",
            "slitting_session_solver_seconds",
            "母卷分切工作台会话层优化（自动分切、优化未锁定）最长运行秒数",
            "slitting_default_child_width_mm",
            "从销售需求导入子分切订单时的默认宽度（mm）",
            "slitting_default_child_length_mm",
            "从销售需求导入子分切订单时的默认长度（mm）",
            "master_plan_material_constraint_enabled",
            "主计划 Timefold 硬约束：排产日 BOM/库存物料必须可满足；false 时求解不因缺料扣分",
            "planning_optimizer_engine",
            "主计划选优引擎：ortools（默认）或 timefold；由 PlanningOptimizerRegistry 路由",
            "master_plan_multi_resource_split",
            "true 时工序可按分钟拆到多台候选设备，主计划走 OR-Tools CP-SAT；false 时使用 planning_optimizer_engine 配置的引擎单机台落槽",
            "master_plan_jit_warm_start",
            "多机台拆分时启用 JIT 倒排预排作为 OR-Tools 初始解/hint；false 时纯 CP 从零求解",
            "master_plan_demand_scale",
            "主计划工序工时缩放系数（0.01=降为 1/100）；仅影响主计划展开与求解，不改工单数量");

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Inject
    WorkspaceContext workspaceContext;

    public boolean getBoolean(String paramId, boolean defaultValue) {
        String v = get(paramId);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    public int getInt(String paramId, int defaultValue) {
        String v = get(paramId);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String paramId, double defaultValue) {
        String v = get(paramId);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String get(String paramId) {
        String cacheKey = cacheKey(paramId);
        return cache.computeIfAbsent(cacheKey, key -> {
            SystemParameterEntity e = SystemParameterEntity.findByParamId(paramId);
            if (e != null) {
                return e.paramValue;
            }
            return DEFAULTS.get(paramId);
        });
    }

    @Transactional
    public void ensureDefaults() {
        DEFAULTS.forEach((id, value) -> {
            SystemParameterEntity existing = SystemParameterEntity.findByParamId(id);
            if (existing == null) {
                SystemParameterEntity p = new SystemParameterEntity();
                p.paramId = id;
                p.paramValue = value;
                p.description = PARAM_DESCRIPTIONS.getOrDefault(id, "Default from scenario card");
                p.stampWorkspace();
                p.persist();
            } else if ("planning_horizon_days".equals(id) && "14".equals(existing.paramValue)) {
                existing.paramValue = value;
                existing.persist();
            } else if ("detail_schedule_contract".equals(id)
                    && OLD_DETAIL_SCHEDULE_CONTRACT.equals(existing.paramValue)) {
                existing.paramValue = value;
                existing.persist();
            }
        });
        cache.clear();
    }

    public void invalidate(String paramId) {
        if (paramId == null) {
            cache.clear();
            return;
        }
        String prefix = workspaceContext.getWorkspaceId() + "|";
        cache.remove(prefix + paramId);
    }

    private String cacheKey(String paramId) {
        return workspaceContext.getWorkspaceId() + "|" + paramId;
    }
}
