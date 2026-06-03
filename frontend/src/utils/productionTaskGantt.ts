import type { DetailScheduleOperation } from '../types/api';
import type { ProductionTask } from '../types/scheduleSession';

const EXECUTION_STATE_LABEL: Record<string, string> = {
  UNPLANNED: '未计划',
  RELEASED: '已发布',
  RUNNING: '进行中',
  COMPLETED: '已完工',
  ARCHIVED: '已归档',
};

export function executionStateLabel(state: string): string {
  return EXECUTION_STATE_LABEL[state] ?? state;
}

function parseTs(ts: string | null | undefined): Date | null {
  if (!ts) return null;
  const d = new Date(ts);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** 将已发布生产任务转为甘特用的相对分钟工序（锚点为最早计划开始日 0 点）。 */
export function productionTasksToGanttOperations(tasks: ProductionTask[]): {
  operations: DetailScheduleOperation[];
  planningAnchor: Date | null;
} {
  const scheduled = tasks.filter(
    (t) =>
      t.lineId &&
      t.plannedStartTs &&
      t.plannedEndTs &&
      t.executionState !== 'ARCHIVED' &&
      t.executionState !== 'UNPLANNED',
  );
  if (scheduled.length === 0) {
    return { operations: [], planningAnchor: null };
  }

  let anchorMs = Infinity;
  for (const t of scheduled) {
    const start = parseTs(t.plannedStartTs);
    if (start) {
      const dayStart = new Date(start);
      dayStart.setHours(0, 0, 0, 0);
      anchorMs = Math.min(anchorMs, dayStart.getTime());
    }
  }
  if (!Number.isFinite(anchorMs)) {
    return { operations: [], planningAnchor: null };
  }
  const planningAnchor = new Date(anchorMs);

  const toMinute = (ts: string) => {
    const d = parseTs(ts);
    if (!d) return 0;
    return Math.round((d.getTime() - planningAnchor.getTime()) / 60_000);
  };

  const byLine = new Map<string, ProductionTask[]>();
  for (const t of scheduled) {
    const lineId = t.lineId!.trim();
    const list = byLine.get(lineId) ?? [];
    list.push(t);
    byLine.set(lineId, list);
  }

  const operations: DetailScheduleOperation[] = [];
  const lineIds = [...byLine.keys()].sort((a, b) => a.localeCompare(b, 'zh-CN'));

  for (const lineId of lineIds) {
    const lineTasks = byLine.get(lineId)!;
    lineTasks.sort((a, b) => toMinute(a.plannedStartTs!) - toMinute(b.plannedStartTs!));
    lineTasks.forEach((t, index) => {
      const startMinute = toMinute(t.plannedStartTs!);
      const endMinute = toMinute(t.plannedEndTs!);
      operations.push({
        operationId: t.stepId,
        workOrderNo: t.workOrderNo,
        lineId,
        resourceId: t.resourceId ?? lineId,
        sequenceIndex: index + 1,
        startMinute,
        endMinute: Math.max(endMinute, startMinute + 1),
        productCode: t.productCode ?? '',
        pinned: t.executionState === 'RUNNING',
        batchNo: t.batchNo,
        operationSeq: t.operationSeq,
        operationName: t.operationName ?? '',
      });
    });
  }

  return { operations, planningAnchor };
}

export function formatDateTime(ts: string | null | undefined): string {
  const d = parseTs(ts);
  if (!d) return '—';
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
