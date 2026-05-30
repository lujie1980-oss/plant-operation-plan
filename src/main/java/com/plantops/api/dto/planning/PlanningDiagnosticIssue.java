package com.plantops.api.dto.planning;

import java.util.List;
import java.util.Map;

/**
 * 单条推演诊断：解释某工单/工序/分配为何被跳过或降级处理。
 *
 * @param severity SKIP（未进入候选）| WARN（进入候选但有风险）| INFO
 * @param reasonCode 稳定机器码，见 {@link com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes}
 */
public record PlanningDiagnosticIssue(
        String severity,
        String reasonCode,
        String workOrderNo,
        String entityId,
        String message
) {
}
