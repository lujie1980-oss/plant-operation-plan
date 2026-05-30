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
            Map.entry("capacity_overload_threshold_pct", "110"),
            Map.entry("master_plan_objective_weights",
                    "{\"minimize_lateness\":10,\"prioritize_high_priority\":1,\"locked_orders_prefer_earlier\":1,\"balance_adjacent_slot_loading\":1}"),
            Map.entry("detail_schedule_contract",
                    ScheduleContractConfigService.DEFAULT_CONTRACT_JSON),
            Map.entry("shift_capacity_minutes", "480"),
            Map.entry("default_procurement_lead_time_days", "7"));

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Inject
    WorkspaceContext workspaceContext;

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
                p.description = "Default from scenario card";
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
