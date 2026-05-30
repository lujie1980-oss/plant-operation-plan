package com.plantops.solver.masterplan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主计划可配置的软优化目标定义（元数据 + 默认权重）。
 */
public final class MasterPlanObjectiveCatalog {

    public record Definition(
            String id,
            String name,
            String description,
            String penaltyUnit,
            int defaultWeight) {
    }

    public static final String MINIMIZE_LATENESS = "minimize_lateness";
    public static final String PRIORITIZE_HIGH_PRIORITY = "prioritize_high_priority";
    public static final String LOCKED_ORDERS_PREFER_EARLIER = "locked_orders_prefer_earlier";
    public static final String BALANCE_ADJACENT_SLOT_LOADING = "balance_adjacent_slot_loading";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(
                    MINIMIZE_LATENESS,
                    "最小化延期",
                    "工单完成时间晚于交期时按延期天数惩罚，促使订单准时交付。",
                    "每延期 1 天",
                    10),
            new Definition(
                    PRIORITIZE_HIGH_PRIORITY,
                    "高优先级靠前",
                    "优先级数值越大，越倾向分配在靠前的时栅槽位。",
                    "槽位序号 × 优先级",
                    1),
            new Definition(
                    LOCKED_ORDERS_PREFER_EARLIER,
                    "锁定订单靠前",
                    "已锁定/冻结窗内订单尽量排在规划窗前段（软约束，避免与产能硬约束冲突）。",
                    "槽位序号",
                    1),
            new Definition(
                    BALANCE_ADJACENT_SLOT_LOADING,
                    "产能均衡",
                    "同一资源上，相邻时间槽的负荷（已分配工时）尽量接近，避免负荷陡增或陡降。",
                    "相邻槽位负荷差（分钟）",
                    1));

    private MasterPlanObjectiveCatalog() {
    }

    public static List<Definition> all() {
        return DEFINITIONS;
    }

    public static Definition find(String id) {
        return DEFINITIONS.stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, Integer> defaults() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Definition d : DEFINITIONS) {
            map.put(d.id(), d.defaultWeight());
        }
        return map;
    }
}
