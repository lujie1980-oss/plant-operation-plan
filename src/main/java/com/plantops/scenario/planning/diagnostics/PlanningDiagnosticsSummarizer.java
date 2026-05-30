package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 将推演诊断 DTO 格式化为流水线日志可读摘要。 */
public final class PlanningDiagnosticsSummarizer {

    private PlanningDiagnosticsSummarizer() {
    }

    public static String masterPlanOneLine(MasterPlanPlanningDiagnosticsDto d) {
        if (d == null) {
            return "S04 推演诊断不可用";
        }
        Map<String, Integer> c = d.counters();
        return "S04 推演：工单 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED)
                + " → 求解分配 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE)
                + "（跳过不可排程 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE)
                + "，无槽丢弃 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS)
                + "，时窗回退 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK)
                + "）";
    }

    public static List<String> masterPlanDetailLines(MasterPlanPlanningDiagnosticsDto d) {
        if (d == null) {
            return List.of();
        }
        Map<String, Integer> c = d.counters();
        List<String> lines = new ArrayList<>();
        lines.add("S04 漏斗：扫描 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED)
                + " → 有分配 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_WITH_ALLOCATIONS)
                + " → 候选 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_CANDIDATE)
                + " → 进入求解 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE));
        lines.add("S04 跳过：不可排程 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE)
                + "，冻结 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_FROZEN)
                + "，无工艺 "
                + counter(c, PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NO_ROUTING));
        lines.add("S04 预警：无槽丢弃 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS)
                + "，时窗回退 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK)
                + "，锁定 "
                + counter(c, PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_LOCKED));
        if (d.overlayActive()) {
            lines.add("S04 反馈 overlay 已启用");
        }
        if (d.inventorySnapshotId() != null && !d.inventorySnapshotId().isBlank()) {
            lines.add("S04 库存快照 " + d.inventorySnapshotId()
                    + "，物料种类 " + counter(c, PlanningDiagnosticCodes.MP_INVENTORY_PRODUCT_COUNT));
        }
        lines.add("S04 工序先后边 "
                + counter(c, PlanningDiagnosticCodes.MP_OPERATION_PRECEDENCE_EDGES)
                + "，并行组 "
                + counter(c, PlanningDiagnosticCodes.MP_PARALLEL_GROUPS)
                + "，孤儿 "
                + counter(c, PlanningDiagnosticCodes.MP_PARALLEL_ORPHANS)
                + "，槽交集 "
                + counter(c, PlanningDiagnosticCodes.MP_PARALLEL_SLOT_INTERSECTIONS)
                + "（无交集回退 "
                + counter(c, PlanningDiagnosticCodes.MP_PARALLEL_SLOT_FALLBACKS)
                + "）");
        long warnIssues = d.issues().stream().filter(i -> "WARN".equals(i.severity())).count();
        long skipIssues = d.issues().stream().filter(i -> "SKIP".equals(i.severity())).count();
        lines.add("S04 issue 样本：跳过 " + skipIssues + "，预警 " + warnIssues
                + (d.issuesTruncated() ? "（已截断）" : ""));
        return lines;
    }

    public static String detailScheduleOneLine(DetailSchedulePlanningDiagnosticsDto d) {
        if (d == null) {
            return "S05 推演诊断不可用";
        }
        Map<String, Integer> c = d.counters();
        return "S05 推演：工单 "
                + counter(c, PlanningDiagnosticCodes.DS_WORK_ORDERS_SCANNED)
                + " → 工序 "
                + counter(c, PlanningDiagnosticCodes.DS_OPERATIONS_TOTAL)
                + "（未齐套 "
                + counter(c, PlanningDiagnosticCodes.DS_OPERATIONS_KITTING_INELIGIBLE)
                + "，契约回退 "
                + counter(c, PlanningDiagnosticCodes.DS_OPERATIONS_MP_TARGET_FALLBACK)
                + "）";
    }

    public static List<String> detailScheduleDetailLines(DetailSchedulePlanningDiagnosticsDto d) {
        if (d == null) {
            return List.of();
        }
        Map<String, Integer> c = d.counters();
        List<String> lines = new ArrayList<>();
        lines.add("S05 漏斗：扫描 "
                + counter(c, PlanningDiagnosticCodes.DS_WORK_ORDERS_SCANNED)
                + " → 纳入 "
                + counter(c, PlanningDiagnosticCodes.DS_WORK_ORDERS_INCLUDED)
                + " → 工序 "
                + counter(c, PlanningDiagnosticCodes.DS_OPERATIONS_TOTAL));
        lines.add("S05 产线：开线 "
                + counter(c, PlanningDiagnosticCodes.DS_SCHEDULE_LINES_OPENED)
                + " / "
                + counter(c, PlanningDiagnosticCodes.DS_SCHEDULE_LINES_TOTAL)
                + "，主计划契约 "
                + counter(c, PlanningDiagnosticCodes.DS_MP_CONTRACTS_LOADED)
                + " 条");
        lines.add("S05 绑定：并行配对 "
                + counter(c, PlanningDiagnosticCodes.DS_PARALLEL_PAIRED_OPS)
                + "，孤儿 "
                + counter(c, PlanningDiagnosticCodes.DS_PARALLEL_ORPHAN_OPS)
                + "，连续生产 "
                + counter(c, PlanningDiagnosticCodes.DS_CONTINUOUS_PRODUCTION_OPS));
        if (d.inventorySnapshotId() != null && !d.inventorySnapshotId().isBlank()) {
            lines.add("S05 库存快照 " + d.inventorySnapshotId()
                    + "，物料种类 " + counter(c, PlanningDiagnosticCodes.DS_INVENTORY_PRODUCT_COUNT));
        }
        long warnIssues = d.issues().stream().filter(i -> "WARN".equals(i.severity())).count();
        long skipIssues = d.issues().stream().filter(i -> "SKIP".equals(i.severity())).count();
        lines.add("S05 issue 样本：跳过 " + skipIssues + "，预警 " + warnIssues
                + (d.issuesTruncated() ? "（已截断）" : ""));
        return lines;
    }

    private static int counter(Map<String, Integer> counters, String key) {
        return counters != null ? counters.getOrDefault(key, 0) : 0;
    }
}
