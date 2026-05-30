package com.plantops.api.dto.masterdata;

import java.util.List;
import java.util.Map;

/**
 * 主数据校验输出 DTO：用于导入阻断、运行前过滤、以及 UI 展示问题原因。
 */
public final class MasterDataValidationDtos {

    private MasterDataValidationDtos() {
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record ValidationIssue(
            String ruleId,
            Severity severity,
            String entityType,
            String entityKey,
            String reason,
            Map<String, Object> fields
    ) {
    }

    public record BlockedSalesOrderLine(
            String salesOrderNo,
            int salesOrderLineNo,
            String ruleId,
            String reason
    ) {
    }

    public record ValidationReport(
            List<ValidationIssue> errors,
            List<ValidationIssue> warnings,
            List<BlockedSalesOrderLine> blockedSalesOrderLines
    ) {
    }
}

