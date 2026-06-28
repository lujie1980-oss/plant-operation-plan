package com.plantops.masterdata;

import java.util.List;

/** 业务规则页「规则项目」标识，与前端 tab id / Excel kind path 一致。 */
public final class BusinessRuleTypeIds {

    public static final String CHANGEOVER = "changeover";
    public static final String PARALLEL_OPERATIONS = "parallel-operations";
    public static final String OPERATION_TRANSFER_TIME = "operation-transfer-time";
    public static final String CONTINUOUS_PRODUCTION = "continuous-production";
    public static final String OPERATION_POST_PROCESSING = "operation-post-processing";
    public static final String BOM_RULES = "bom-rules";
    public static final String MATERIAL_LEAD_TIME = "material-lead-time";
    public static final String SHIFT_HEADCOUNT_RULES = "shift-headcount-rules";
    public static final String DEMAND_PRIORITY_RULES = "demand-priority-rules";
    /** Phase 3：班次日历赋时（跳过非开工窗口）。 */
    public static final String FACTORY_CALENDAR = "factory-calendar";
    /** Phase 3：排程反馈冻结窗口内工序保持计划时间。 */
    public static final String FEEDBACK_FREEZE = "feedback-freeze";
    /** Phase 3：批次内工序增量闭包扩展。 */
    public static final String BATCH_CONTINUOUS = "batch-continuous";

    public static final List<String> ALL = List.of(
            CHANGEOVER,
            PARALLEL_OPERATIONS,
            OPERATION_TRANSFER_TIME,
            CONTINUOUS_PRODUCTION,
            OPERATION_POST_PROCESSING,
            BOM_RULES,
            MATERIAL_LEAD_TIME,
            SHIFT_HEADCOUNT_RULES,
            DEMAND_PRIORITY_RULES,
            FACTORY_CALENDAR,
            FEEDBACK_FREEZE,
            BATCH_CONTINUOUS);

    private BusinessRuleTypeIds() {
    }

    public static String labelOf(String ruleTypeId) {
        return switch (ruleTypeId) {
            case CHANGEOVER -> "换型矩阵";
            case PARALLEL_OPERATIONS -> "并行工序";
            case OPERATION_TRANSFER_TIME -> "工序衔接规则";
            case CONTINUOUS_PRODUCTION -> "连续生产";
            case OPERATION_POST_PROCESSING -> "工序后处理时间";
            case BOM_RULES -> "BOM 关键件";
            case MATERIAL_LEAD_TIME -> "最长采购周期";
            case SHIFT_HEADCOUNT_RULES -> "班次人员";
            case DEMAND_PRIORITY_RULES -> "订单优先级";
            case FACTORY_CALENDAR -> "班次日历赋时";
            case FEEDBACK_FREEZE -> "反馈冻结窗口";
            case BATCH_CONTINUOUS -> "批次连续排产";
            default -> ruleTypeId;
        };
    }

    public static String defaultDescriptionOf(String ruleTypeId) {
        return switch (ruleTypeId) {
            case CHANGEOVER ->
                    "按工序与工艺属性维护换型规则（泰科蓝图 KTPrefixDuration 格式）：前/后属性值支持 * 通配；同属性 *→* 表示属性值变化时生效";
            case PARALLEL_OPERATIONS ->
                    "U型线并行生产配对（U型线清单.xlsx）：两个半品料号在指定机台（产线ID）上需同时加工；两头齐全则同产线同起同止，缺一头则单排至其它可生产产线。";
            case OPERATION_TRANSFER_TIME ->
                    "按产品维护相邻工序之间的流转时间与最小流转时间（分钟）；主计划产能甘特与详细排程工序间隔均生效。";
            case CONTINUOUS_PRODUCTION ->
                    "连续生产料号清单：指定机台上关联料号须连续排产，中间不得停留或插入其它料号；详细排程以硬约束保证同组工序不被隔开。";
            case OPERATION_POST_PROCESSING ->
                    "末工序结束到工单可交付之间的后处理时间（分钟）；工序名填 * 表示该产品默认末工序后处理。";
            case BOM_RULES -> "物料规则：关键件标记影响齐套与 MRP 可行性判定";
            case MATERIAL_LEAD_TIME ->
                    "最长采购周期（天）：物料短缺时推算 Supply 最晚可用日（RULE-MRP-04）。物料填 * 的一行表示「默认最长采购周期」；优先精确物料，其次 * 行，最后系统参数 default_procurement_lead_time_days。";
            case SHIFT_HEADCOUNT_RULES -> "各区域/班次的可用人员数，影响排程人力约束。";
            case DEMAND_PRIORITY_RULES -> "需求规则：优先级、加急等级与排程锁定";
            case FACTORY_CALENDAR ->
                    "按资源日历与工厂班次策略，将工序开工时间对齐到可用班次窗口（非 24h 连续分钟）。";
            case FEEDBACK_FREEZE ->
                    "排程反馈 cutoff 及之前已冻结工序在 Session 推演中保持计划开工时间不变。";
            case BATCH_CONTINUOUS ->
                    "增量推演时，种子工序所在批次的同线工序一并纳入波及闭包。";
            default -> "";
        };
    }
}
