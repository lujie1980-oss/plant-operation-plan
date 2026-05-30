package com.plantops.masterdata;

import jakarta.ws.rs.BadRequestException;

/**
 * 业务规则页 Excel 导入导出类型，与前端规则 tab id 一致。
 */
public enum BusinessRuleExcelKind {
    CHANGEOVER("changeover", "换型矩阵"),
    PARALLEL_OPERATIONS("parallel-operations", "并行工序"),
    OPERATION_TRANSFER_TIME("operation-transfer-time", "工序流转时间"),
    CONTINUOUS_PRODUCTION("continuous-production", "连续生产"),
    BOM_RULES("bom-rules", "BOM关键件"),
    SHIFT_HEADCOUNT_RULES("shift-headcount-rules", "班次人员"),
    DEMAND_PRIORITY_RULES("demand-priority-rules", "订单优先级");

    public final String pathSegment;
    public final String label;

    BusinessRuleExcelKind(String pathSegment, String label) {
        this.pathSegment = pathSegment;
        this.label = label;
    }

    public static BusinessRuleExcelKind fromPath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("请指定规则类型");
        }
        for (BusinessRuleExcelKind kind : values()) {
            if (kind.pathSegment.equals(path)) {
                return kind;
            }
        }
        throw new BadRequestException("未知规则类型: " + path);
    }
}
