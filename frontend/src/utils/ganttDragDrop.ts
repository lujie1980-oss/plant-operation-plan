import type { MachineScheduleTask } from './machineScheduleModel';

export interface GanttDragCommit {
  operationId: string;
  lineId: string;
  sequenceOnLine: number;
}

/** 根据轨道内 X 坐标推算插入顺序（1-based）。 */
export function sequenceOnLineFromDropMinute(
  tasks: MachineScheduleTask[],
  dropMinute: number,
  draggedId: string,
): number {
  const others = tasks
    .filter((t) => t.operationId !== draggedId)
    .sort((a, b) => a.startMinute - b.startMinute || a.sequenceIndex - b.sequenceIndex);
  if (others.length === 0) {
    return 1;
  }
  for (let i = 0; i < others.length; i++) {
    const mid = (others[i].startMinute + others[i].endMinute) / 2;
    if (dropMinute < mid) {
      return i + 1;
    }
  }
  return others.length + 1;
}

export function minuteFromTrackX(
  clientX: number,
  trackRect: DOMRect,
  minMinute: number,
  span: number,
): number {
  const ratio = Math.max(0, Math.min(1, (clientX - trackRect.left) / trackRect.width));
  return minMinute + ratio * span;
}
