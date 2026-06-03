import type { DetailScheduleOperation } from '../types/api';
import {
  GANTT_CHANGEOVER_STYLE,
  GANTT_TASK_STYLE,
  type GanttTaskDisplayPhase,
} from './ganttTaskDisplay';

export interface MachineScheduleTask {
  operationId: string;
  workOrderNo: string;
  batchNo?: string | null;
  productCode: string;
  resourceId: string;
  sequenceIndex: number;
  startMinute: number;
  endMinute: number;
  pinned: boolean;
  displayPhase: GanttTaskDisplayPhase;
  changeoverMinutesBefore: number;
}

export interface MachineScheduleChangeover {
  startMinute: number;
  endMinute: number;
  /** 换型所服务的下一道工序 */
  operationId: string;
}

export interface MachineScheduleRow {
  lineId: string;
  tasks: MachineScheduleTask[];
  changeovers: MachineScheduleChangeover[];
}

export interface MachineScheduleModel {
  horizonStart: Date;
  rows: MachineScheduleRow[];
  minMinute: number;
  maxMinute: number;
  ticks: { minute: number; label: string }[];
}

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

export interface BuildMachineScheduleModelOptions {
  /** 主数据全部产线；与 includeEmptyLines 配合展示无任务产线 */
  allLineIds?: string[];
  includeEmptyLines?: boolean;
}

export function buildMachineScheduleModel(
  operations: DetailScheduleOperation[],
  horizonStart = defaultHorizonStart(),
  options: BuildMachineScheduleModelOptions = {},
): MachineScheduleModel | null {
  const { allLineIds, includeEmptyLines = false } = options;

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

  if (byLine.size === 0 && !(includeEmptyLines && allLineIds && allLineIds.length > 0)) {
    return null;
  }

  let minMinute = Infinity;
  let maxMinute = 0;

  const rows: MachineScheduleRow[] = [];
  let lineIds: string[];
  if (includeEmptyLines && allLineIds && allLineIds.length > 0) {
    const merged = new Set(allLineIds.map((id) => id.trim()).filter(Boolean));
    for (const id of byLine.keys()) {
      merged.add(id);
    }
    lineIds = [...merged].sort((a, b) => a.localeCompare(b, 'zh-CN'));
  } else {
    lineIds = [...byLine.keys()].sort((a, b) => a.localeCompare(b, 'zh-CN'));
  }

  for (const lineId of lineIds) {
    const ops = [...(byLine.get(lineId) ?? [])].sort(
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
        batchNo: op.batchNo,
        productCode: op.productCode,
        resourceId: op.resourceId ?? '',
        sequenceIndex: seq,
        startMinute: start,
        endMinute: end,
        pinned: op.pinned,
        displayPhase: op.displayPhase ?? 'scheduled',
        changeoverMinutesBefore: Math.max(0, op.changeoverMinutesBefore ?? 0),
      };
    });

    const changeovers: MachineScheduleChangeover[] = [];
    for (let i = 0; i < tasks.length; i++) {
      const task = tasks[i];
      const coMin = task.changeoverMinutesBefore;
      if (coMin <= 0) {
        continue;
      }
      const coStart = i > 0 ? tasks[i - 1].endMinute : Math.max(0, task.startMinute - coMin);
      changeovers.push({
        startMinute: coStart,
        endMinute: coStart + coMin,
        operationId: task.operationId,
      });
      minMinute = Math.min(minMinute, coStart);
      maxMinute = Math.max(maxMinute, coStart + coMin);
    }

    rows.push({ lineId, tasks, changeovers });
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

export function taskBarStyle(task: MachineScheduleTask): {
  backgroundColor: string;
  borderColor: string;
} {
  const style = GANTT_TASK_STYLE[task.displayPhase];
  return { backgroundColor: style.fill, borderColor: style.border };
}

export function changeoverBarStyle(): { backgroundColor: string; borderColor: string } {
  return {
    backgroundColor: GANTT_CHANGEOVER_STYLE.fill,
    borderColor: GANTT_CHANGEOVER_STYLE.border,
  };
}

/** @deprecated use taskBarStyle */
export function taskBarColor(task: MachineScheduleTask): string {
  return taskBarStyle(task).backgroundColor;
}

export const MACHINE_SCHEDULE_LABEL_W = 176;
export const MACHINE_SCHEDULE_MINUTE_W = 2.5;
export const MACHINE_SCHEDULE_ROW_H = 41;
export const MACHINE_SCHEDULE_HEADER_H = 29;

export const MACHINE_SCHEDULE_ZOOM_MIN = 0.25;
export const MACHINE_SCHEDULE_ZOOM_MAX = 5;
export const MACHINE_SCHEDULE_ZOOM_STEP = 0.25;
export const MACHINE_SCHEDULE_ZOOM_DEFAULT = 1;

export const MINUTES_PER_DAY = 24 * 60;

export const TIME_SCALE_PRESETS = {
  '1d': MINUTES_PER_DAY,
  '2d': 2 * MINUTES_PER_DAY,
  '4d': 4 * MINUTES_PER_DAY,
  '1w': 7 * MINUTES_PER_DAY,
} as const;

export type TimeScalePreset = keyof typeof TIME_SCALE_PRESETS | 'fit';

export const TIME_SCALE_LABELS: Record<TimeScalePreset, string> = {
  fit: '全部',
  '1d': '1日',
  '2d': '2日',
  '4d': '4日',
  '1w': '周',
};

/** 按时间尺度预设计算「每分钟像素宽」，使 N 日正好铺满可视轨道宽度。 */
export function minuteWidthForTimeScale(
  preset: TimeScalePreset,
  viewportTrackWidth: number,
  zoom: number,
): number {
  if (preset === 'fit') {
    return MACHINE_SCHEDULE_MINUTE_W * zoom;
  }
  const spanMinutes = TIME_SCALE_PRESETS[preset];
  return (viewportTrackWidth / spanMinutes) * zoom;
}

/** 根据时间尺度预设计算时间轴刻度间隔（分钟） */
export function tickStepForTimeScale(
  spanMinutes: number,
  preset: TimeScalePreset,
  zoom: number,
): number {
  switch (preset) {
    case '1d':
      return 60;
    case '2d':
      return 120;
    case '4d':
      return 240;
    case '1w':
      return MINUTES_PER_DAY;
    default:
      return tickStepForZoom(spanMinutes, zoom);
  }
}

function fmtTickLabel(horizonStart: Date, minute: number, preset: TimeScalePreset): string {
  const d = new Date(horizonStart.getTime() + minute * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  if (preset === '1w') {
    return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
  }
  if (preset === '4d') {
    return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:00`;
  }
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function buildTicksForTimeScale(
  horizonStart: Date,
  minMinute: number,
  maxMinute: number,
  preset: TimeScalePreset,
  zoom: number,
): { minute: number; label: string }[] {
  const span = maxMinute - minMinute;
  const step = tickStepForTimeScale(span, preset, zoom);
  const ticks: { minute: number; label: string }[] = [];
  for (let m = minMinute; m <= maxMinute; m += step) {
    ticks.push({ minute: m, label: fmtTickLabel(horizonStart, m, preset) });
  }
  if (ticks.length === 0 || ticks[ticks.length - 1].minute < maxMinute) {
    ticks.push({ minute: maxMinute, label: fmtTickLabel(horizonStart, maxMinute, preset) });
  }
  return ticks;
}

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
