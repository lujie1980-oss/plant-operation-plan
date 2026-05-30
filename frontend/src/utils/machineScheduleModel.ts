import type { DetailScheduleOperation } from '../types/api';

export interface MachineScheduleTask {
  operationId: string;
  workOrderNo: string;
  productCode: string;
  sequenceIndex: number;
  startMinute: number;
  endMinute: number;
  pinned: boolean;
  colorIndex: number;
}

export interface MachineScheduleRow {
  machineId: string;
  tasks: MachineScheduleTask[];
}

export interface MachineScheduleModel {
  horizonStart: Date;
  rows: MachineScheduleRow[];
  minMinute: number;
  maxMinute: number;
  ticks: { minute: number; label: string }[];
}

const COLORS = ['#0ea5e9', '#14b8a6', '#eab308', '#a855f7', '#f43f5e', '#f97316'];

function defaultHorizonStart(): Date {
  const d = new Date();
  d.setHours(8, 0, 0, 0);
  return d;
}

function fmtTick(horizonStart: Date, minute: number): string {
  const d = new Date(horizonStart.getTime() + minute * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function buildMachineScheduleModel(
  operations: DetailScheduleOperation[],
  horizonStart = defaultHorizonStart(),
): MachineScheduleModel | null {
  if (operations.length === 0) {
    return null;
  }

  const byMachine = new Map<string, DetailScheduleOperation[]>();
  for (const op of operations) {
    const machineId = op.resourceId || op.lineId;
    const list = byMachine.get(machineId) ?? [];
    list.push(op);
    byMachine.set(machineId, list);
  }

  let minMinute = Infinity;
  let maxMinute = 0;
  let colorIdx = 0;

  const rows: MachineScheduleRow[] = [];
  const machineIds = [...byMachine.keys()].sort((a, b) => a.localeCompare(b, 'zh-CN'));

  for (const machineId of machineIds) {
    const ops = [...(byMachine.get(machineId) ?? [])].sort(
      (a, b) =>
        (a.startMinute ?? 0) - (b.startMinute ?? 0) ||
        (a.sequenceIndex ?? 0) - (b.sequenceIndex ?? 0),
    );

    const tasks: MachineScheduleTask[] = ops.map((op, index) => {
      const start = op.startMinute ?? 0;
      const end = op.endMinute ?? start + 30;
      minMinute = Math.min(minMinute, start);
      maxMinute = Math.max(maxMinute, end);
      const seq = op.sequenceIndex > 0 ? op.sequenceIndex : index + 1;
      return {
        operationId: op.operationId,
        workOrderNo: op.workOrderNo,
        productCode: op.productCode,
        sequenceIndex: seq,
        startMinute: start,
        endMinute: end,
        pinned: op.pinned,
        colorIndex: colorIdx++,
      };
    });

    rows.push({ machineId, tasks });
  }

  if (!Number.isFinite(minMinute)) {
    minMinute = 0;
  }
  const pad = 30;
  minMinute = Math.max(0, minMinute - pad);
  maxMinute = maxMinute + pad;
  if (maxMinute <= minMinute) {
    maxMinute = minMinute + 480;
  }

  const span = maxMinute - minMinute;
  const step = tickStepForZoom(span, MACHINE_SCHEDULE_ZOOM_DEFAULT);
  const ticks = buildTicksForRange(horizonStart, minMinute, maxMinute, step);

  return { horizonStart, rows, minMinute, maxMinute, ticks };
}

export function taskBarColor(task: MachineScheduleTask): string {
  if (task.pinned) {
    return '#64748b';
  }
  return COLORS[task.colorIndex % COLORS.length];
}

export const MACHINE_SCHEDULE_LABEL_W = 176;
export const MACHINE_SCHEDULE_MINUTE_W = 2.5;
export const MACHINE_SCHEDULE_ROW_H = 41;
export const MACHINE_SCHEDULE_HEADER_H = 29;

export const MACHINE_SCHEDULE_ZOOM_MIN = 0.25;
export const MACHINE_SCHEDULE_ZOOM_MAX = 5;
export const MACHINE_SCHEDULE_ZOOM_STEP = 0.25;
export const MACHINE_SCHEDULE_ZOOM_DEFAULT = 1;

/** 根据缩放系数计算时间轴刻度间隔（分钟） */
export function tickStepForZoom(spanMinutes: number, zoom: number): number {
  const pxPerMin = MACHINE_SCHEDULE_MINUTE_W * zoom;
  if (pxPerMin >= 8) return 15;
  if (pxPerMin >= 4) return 30;
  if (pxPerMin >= 2) return 60;
  if (spanMinutes > 960) return 240;
  if (spanMinutes > 480) return 120;
  return 60;
}

export function buildTicksForRange(
  horizonStart: Date,
  minMinute: number,
  maxMinute: number,
  step: number,
): { minute: number; label: string }[] {
  const ticks: { minute: number; label: string }[] = [];
  for (let m = minMinute; m <= maxMinute; m += step) {
    ticks.push({ minute: m, label: fmtTick(horizonStart, m) });
  }
  if (ticks.length === 0 || ticks[ticks.length - 1].minute < maxMinute) {
    ticks.push({ minute: maxMinute, label: fmtTick(horizonStart, maxMinute) });
  }
  return ticks;
}

export function clampZoom(zoom: number): number {
  return Math.min(MACHINE_SCHEDULE_ZOOM_MAX, Math.max(MACHINE_SCHEDULE_ZOOM_MIN, zoom));
}
