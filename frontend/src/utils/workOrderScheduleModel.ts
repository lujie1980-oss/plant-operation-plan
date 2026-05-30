import type { WorkOrderScheduleOperation } from '../types/api';

export interface WorkOrderScheduleRow {
  resourceId: string;
  operations: WorkOrderScheduleOperation[];
}

export interface WorkOrderScheduleModel {
  horizonStart: Date;
  minMs: number;
  maxMs: number;
  rows: WorkOrderScheduleRow[];
}

export function buildWorkOrderScheduleModel(
  operations: WorkOrderScheduleOperation[],
): WorkOrderScheduleModel | null {
  if (operations.length === 0) {
    return null;
  }
  let minMs = Number.POSITIVE_INFINITY;
  let maxMs = Number.NEGATIVE_INFINITY;
  for (const op of operations) {
    const s = new Date(op.plannedStart).getTime();
    const e = new Date(op.plannedEnd).getTime();
    if (!Number.isNaN(s)) minMs = Math.min(minMs, s);
    if (!Number.isNaN(e)) maxMs = Math.max(maxMs, e);
  }
  if (!Number.isFinite(minMs) || !Number.isFinite(maxMs)) {
    return null;
  }
  if (maxMs <= minMs) {
    maxMs = minMs + 3600_000;
  }
  const byResource = new Map<string, WorkOrderScheduleOperation[]>();
  for (const op of operations) {
    const list = byResource.get(op.resourceId) ?? [];
    list.push(op);
    byResource.set(op.resourceId, list);
  }
  const rows: WorkOrderScheduleRow[] = [...byResource.entries()].map(([resourceId, ops]) => ({
    resourceId,
    operations: ops.sort((a, b) => a.operationSeq - b.operationSeq),
  }));
  const horizonStart = new Date(minMs);
  horizonStart.setHours(0, 0, 0, 0);
  return { horizonStart, minMs, maxMs, rows };
}

export function barColorForScope(scope: string): string {
  if (scope === 'FROZEN') return '#7c3aed';
  if (scope === 'SUGGESTION') return '#3b82f6';
  return '#64748b';
}

export function fmtScheduleTs(ts: string): string {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
