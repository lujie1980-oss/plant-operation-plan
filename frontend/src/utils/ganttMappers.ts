import type { Task } from 'gantt-task-react';
import type { DetailScheduleOperation, MasterPlanAllocation } from '../types/api';

const SHIFT_START_HOUR: Record<string, number> = {
  S1: 8,
  S2: 16,
  DAY: 8,
};

function parseDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function addMinutes(base: Date, minutes: number): Date {
  return new Date(base.getTime() + minutes * 60_000);
}

/** 主计划：按资源分组，槽位映射为甘特条 */
export function masterPlanToGanttTasks(allocations: MasterPlanAllocation[]): Task[] {
  const byResource = new Map<string, MasterPlanAllocation[]>();
  for (const a of allocations) {
    const list = byResource.get(a.resourceId) ?? [];
    list.push(a);
    byResource.set(a.resourceId, list);
  }

  const tasks: Task[] = [];
  let colorIdx = 0;
  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899'];

  for (const [resourceId, rows] of byResource) {
    const projectId = `res-${resourceId}`;
    tasks.push({
      id: projectId,
      name: resourceId,
      start: new Date(),
      end: new Date(),
      type: 'project',
      progress: 0,
      hideChildren: false,
      styles: { backgroundColor: '#e2e8f0', progressColor: '#94a3b8' },
    });

    rows.sort((a, b) => a.slotDate.localeCompare(b.slotDate) || a.slotIndex - b.slotIndex);

    for (const row of rows) {
      let startDt: Date;
      let endDt: Date;
      if (row.plannedStartTs && row.plannedEndTs) {
        startDt = new Date(row.plannedStartTs);
        endDt = new Date(row.plannedEndTs);
      } else {
        const day = parseDate(row.slotDate);
        const hour = SHIFT_START_HOUR[row.shiftId] ?? 8;
        const start = new Date(day);
        start.setHours(hour, 0, 0, 0);
        const slotMinutes = row.durationMinutes && row.durationMinutes > 0 ? row.durationMinutes : 480;
        startDt = start;
        endDt = addMinutes(start, slotMinutes);
      }
      const color = colors[colorIdx % colors.length];
      colorIdx++;

      const segmentSuffix = row.segmentIndex > 0 ? `·段${row.segmentIndex + 1}` : '';
      tasks.push({
        id: `${projectId}-${row.allocationId ?? row.workOrderNo}-${row.slotIndex}`,
        name: row.productCode
          ? `${row.productCode} · ${row.workOrderNo}${segmentSuffix}`
          : `${row.salesOrderNo}-${row.salesOrderLineNo}${segmentSuffix}`,
        start: startDt,
        end: endDt,
        type: 'task',
        progress: 100,
        project: projectId,
        styles: { backgroundColor: color, progressColor: color },
      });
    }
  }

  if (tasks.length === 0) {
    tasks.push({
      id: 'empty',
      name: '暂无分配数据',
      start: new Date(),
      end: addMinutes(new Date(), 60),
      type: 'task',
      progress: 0,
    });
  }

  return tasks;
}

/** 详细排程：按产线分组，左侧列表为产线，子项为产线上的任务顺序 */
export function detailScheduleToGanttTasks(
  operations: DetailScheduleOperation[],
  horizonStart?: Date,
): Task[] {
  const base = horizonStart ?? (() => {
    const d = new Date();
    d.setHours(8, 0, 0, 0);
    return d;
  })();

  const byLine = new Map<string, DetailScheduleOperation[]>();
  for (const op of operations) {
    const lineId = op.lineId?.trim();
    if (!lineId) {
      continue;
    }
    const list = byLine.get(lineId) ?? [];
    list.push(op);
    byLine.set(lineId, list);
  }

  const tasks: Task[] = [];
  const colors = ['#0ea5e9', '#14b8a6', '#eab308', '#a855f7', '#f43f5e'];
  let ci = 0;

  const lineIds = [...byLine.keys()].sort((a, b) => a.localeCompare(b, 'zh-CN'));

  for (const lineId of lineIds) {
    const ops = byLine.get(lineId) ?? [];
    const projectId = `line-${lineId}`;
    ops.sort(
      (a, b) =>
        (a.startMinute ?? 0) - (b.startMinute ?? 0) ||
        (a.sequenceIndex ?? 0) - (b.sequenceIndex ?? 0),
    );

    let lineStart = base;
    let lineEnd = addMinutes(base, 480);
    if (ops.length > 0) {
      const firstStart = ops[0].startMinute ?? 0;
      const lastEnd = ops[ops.length - 1].endMinute ?? firstStart + 30;
      lineStart = addMinutes(base, firstStart);
      lineEnd = addMinutes(base, lastEnd);
    }

    tasks.push({
      id: projectId,
      name: lineId,
      start: lineStart,
      end: lineEnd,
      type: 'project',
      progress: 0,
      hideChildren: false,
      styles: { backgroundColor: '#e2e8f0', progressColor: '#94a3b8' },
    });

    ops.forEach((op, index) => {
      const start = addMinutes(base, op.startMinute ?? 0);
      const end = addMinutes(base, op.endMinute ?? (op.startMinute ?? 0) + 30);
      const color = colors[ci % colors.length];
      ci++;
      const seq = op.sequenceIndex > 0 ? op.sequenceIndex : index + 1;
      tasks.push({
        id: op.operationId,
        name: `#${seq} ${op.workOrderNo} · ${op.productCode}`,
        start,
        end,
        type: 'task',
        progress: op.pinned ? 100 : 80,
        project: projectId,
        styles: {
          backgroundColor: op.pinned ? '#64748b' : color,
          progressColor: op.pinned ? '#475569' : color,
        },
      });
    });
  }

  if (tasks.length === 0) {
    tasks.push({
      id: 'empty-ds',
      name: '暂无排程工序',
      start: base,
      end: addMinutes(base, 60),
      type: 'task',
      progress: 0,
    });
  }

  return tasks;
}
