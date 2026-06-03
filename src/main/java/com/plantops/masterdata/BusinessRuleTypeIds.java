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

    public static final List<String> ALL = List.of(
            CHANGEOVER,
            PARALLEL_OPERATIONS,
            OPERATION_TRANSFER_TIME,
            CONTINUOUS_PRODUCTION,
            OPERATION_POST_PROCESSING,
            BOM_RULES,
            MATERIAL_LEAD_TIME,
            SHIFT_HEADCOUNT_RULES,
            DEMAND_PRIORITY_RULES);

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
            case MATERIAL_LEAD_TIME -> "采购提前期";
            case SHIFT_HEADCOUNT_RULES -> "班次人员";
            case DEMAND_PRIORITY_RULES -> "订单优先级";
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
                    "物料采购提前期（天）：缺料时按该提前期推算可到货日。物料填 * 表示所有物料的默认提前期；优先取精确物料规则，其次 * 规则，最后系统默认参数。最早可行开始对多个缺料件取“最迟到货”（并行备料）。";
            case SHIFT_HEADCOUNT_RULES -> "各区域/班次的可用人员数，影响排程人力约束。";
            case DEMAND_PRIORITY_RULES -> "需求规则：优先级、加急等级与排程锁定";
            default -> "";
        };
    }
}
