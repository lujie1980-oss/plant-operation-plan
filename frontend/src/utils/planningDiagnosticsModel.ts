import type {
  DetailSchedulePlanningDiagnostics,
  MasterPlanPlanningDiagnostics,
  PlanningDiagnosticIssue,
  PlanningDiagnosticsLayer,
} from '../types/planningDiagnostics';

export const REASON_CODE_LABELS: Record<string, string> = {
  WO_NOT_SCHEDULABLE: '工单不可排程',
  WO_FROZEN_THROUGH_CUTOFF: '反馈冻结窗口',
  WO_NO_ROUTING: '无工艺路由',
  WO_NO_ALLOCATIONS: '无有效分配',
  ALLOC_NO_RESOURCE_SLOTS: '无可用槽位',
  ALLOC_TIMING_FALLBACK: '最早可行时窗回退',
  WO_KITTING_SHORT: '齐套不足',
  OP_MP_CONTRACT: '主计划工序契约',
  OP_MP_TARGET_FALLBACK: '主计划末槽回退',
};

export interface FunnelStep {
  key: string;
  label: string;
}

export const MASTER_PLAN_FUNNEL: FunnelStep[] = [
  { key: 'workOrdersScanned', label: '扫描工单' },
  { key: 'workOrdersWithAllocations', label: '展开分配' },
  { key: 'orderAllocationsCandidate', label: '候选分配' },
  { key: 'orderAllocationsReplannable', label: '进入求解' },
];

export const DETAIL_SCHEDULE_FUNNEL: FunnelStep[] = [
  { key: 'workOrdersScanned', label: '扫描工单' },
  { key: 'workOrdersIncluded', label: '纳入排程' },
  { key: 'operationsTotal', label: '工序总数' },
];

export const MASTER_PLAN_SKIP_COUNTERS = [
  { key: 'workOrdersSkippedNotSchedulable', label: '不可排程' },
  { key: 'workOrdersSkippedFrozen', label: '冻结跳过' },
  { key: 'workOrdersSkippedNoRouting', label: '无工艺' },
];

export const MASTER_PLAN_WARN_COUNTERS = [
  { key: 'orderAllocationsDroppedNoSlots', label: '无槽丢弃' },
  { key: 'orderAllocationsTimingFallback', label: '时窗回退' },
  { key: 'orderAllocationsLocked', label: '锁定分配' },
];

export const DETAIL_SCHEDULE_WARN_COUNTERS = [
  { key: 'operationsKittingIneligible', label: '未齐套工序' },
  { key: 'operationsMpTargetFallback', label: '契约回退' },
  { key: 'parallelOrphanOperations', label: '并行孤儿' },
];

export function reasonLabel(code: string): string {
  return REASON_CODE_LABELS[code] ?? code;
}

export function fmtDiagnosticTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function countIssues(issues: PlanningDiagnosticIssue[]): { skip: number; warn: number; info: number } {
  let skip = 0;
  let warn = 0;
  let info = 0;
  for (const item of issues) {
    if (item.severity === 'SKIP') skip += 1;
    else if (item.severity === 'WARN') warn += 1;
    else info += 1;
  }
  return { skip, warn, info };
}

export function summaryLevel(skip: number, warn: number): 'ok' | 'warn' | 'danger' {
  if (skip > 0) return 'danger';
  if (warn > 0) return 'warn';
  return 'ok';
}

export function funnelSteps(layer: PlanningDiagnosticsLayer): FunnelStep[] {
  return layer === 'master-plan' ? MASTER_PLAN_FUNNEL : DETAIL_SCHEDULE_FUNNEL;
}

export function counterValue(
  data: MasterPlanPlanningDiagnostics | DetailSchedulePlanningDiagnostics,
  key: string,
): number {
  return data.counters[key] ?? 0;
}

export function metaLines(
  layer: PlanningDiagnosticsLayer,
  data: MasterPlanPlanningDiagnostics | DetailSchedulePlanningDiagnostics,
): string[] {
  if (layer === 'master-plan') {
    const mp = data as MasterPlanPlanningDiagnostics;
    const lines = [`产能策略：${mp.capacityStrategy ?? '—'}`];
    if (mp.overlayActive) lines.push('反馈 overlay：已启用');
    if (mp.inventorySnapshotId) lines.push(`库存快照：${mp.inventorySnapshotId}`);
    lines.push(`期初物料种类：${mp.counters.inventoryProductCount ?? 0}`);
    lines.push(`BOM 依赖边：${mp.counters.bomDependencyEdgeCount ?? 0}`);
    lines.push(`时隙：${mp.counters.timeSlotCount ?? 0}`);
    return lines;
  }
  const ds = data as DetailSchedulePlanningDiagnostics;
  return [
    `主计划版本：${ds.masterPlanVersionId ?? '—'}`,
    ...(ds.inventorySnapshotId ? [`库存快照：${ds.inventorySnapshotId}`] : []),
    `期初物料种类：${ds.counters.inventoryProductCount ?? 0}`,
    `开线：${ds.counters.scheduleLinesOpened ?? 0} / ${ds.counters.scheduleLinesTotal ?? 0}`,
    `主计划契约：${ds.counters.masterPlanContractsLoaded ?? 0} 条`,
    `并行配对：${ds.counters.parallelPairedOperations ?? 0}，连续生产：${ds.counters.continuousProductionOperations ?? 0}`,
  ];
}
