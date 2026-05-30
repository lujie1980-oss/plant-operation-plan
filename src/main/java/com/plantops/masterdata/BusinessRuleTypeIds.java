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
            case OPERATION_TRANSFER_TIME -> "工序流转时间";
            case CONTINUOUS_PRODUCTION -> "连续生产";
            case OPERATION_POST_PROCESSING -> "工序后处理时间";
            case BOM_RULES -> "BOM 关键件";
            case MATERIAL_LEAD_TIME -> "采购提前期";
            case SHIFT_HEADCOUNT_RULES -> "班次人员";
            case DEMAND_PRIORITY_RULES -> "订单优先级";
            default -> ruleTypeId;
        };
    }
}
