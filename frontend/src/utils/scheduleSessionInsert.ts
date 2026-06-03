import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';
import type { SessionStepPatch } from '../types/scheduleSession';
import { sequenceOnLineFromDropMinute, type GanttDragCommit } from './ganttDragDrop';
import type { MachineScheduleTask } from './machineScheduleModel';

export const BATCH_DRAG_MIME = 'application/x-plantops-batch';

export interface BatchDragPayload {
  batchNo: string;
}

export function parseBatchDragPayload(dataTransfer: DataTransfer): BatchDragPayload | null {
  const raw = dataTransfer.getData(BATCH_DRAG_MIME);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as BatchDragPayload;
    if (parsed?.batchNo) return parsed;
  } catch {
    /* ignore */
  }
  return null;
}

export function sortLineTasks(lineTasks: MachineScheduleTask[]): MachineScheduleTask[] {
  return [...lineTasks].sort(
    (a, b) => a.startMinute - b.startMinute || a.sequenceIndex - b.sequenceIndex,
  );
}

/** 按 earliestStartMinute 在同线队列中找 1-based 插入位置。 */
/** 启发式：插入到最早可排位置时，估算开工分钟（用于多产线择优）。 */
export function estimateEarliestInsertMinute(
  lineTasks: MachineScheduleTask[],
  earliestStartMinute: number,
): number {
  const sorted = sortLineTasks(lineTasks);
  const seq = sequenceForEarliestStart(lineTasks, earliestStartMinute);
  if (sorted.length === 0 || seq <= 1) {
    return earliestStartMinute;
  }
  if (seq > sorted.length) {
    const last = sorted[sorted.length - 1];
    return Math.max(earliestStartMinute, last.endMinute);
  }
  const prev = sorted[seq - 2];
  return Math.max(earliestStartMinute, prev.endMinute);
}

/** 在候选产线中选估算开工最早的一条；优先 preferredLineId（如 resourceId）。 */
export function pickBestLineForEarliest(
  candidateLineIds: string[],
  earliestStartMinute: number,
  lineTasksByLine: (lineId: string) => MachineScheduleTask[],
  preferredLineId?: string | null,
): string | null {
  if (candidateLineIds.length === 0) return null;
  if (preferredLineId && candidateLineIds.includes(preferredLineId)) {
    return preferredLineId;
  }
  let bestLine = candidateLineIds[0];
  let bestEst = Infinity;
  for (const lineId of candidateLineIds) {
    const est = estimateEarliestInsertMinute(
      lineTasksByLine(lineId),
      earliestStartMinute,
    );
    if (
      est < bestEst ||
      (est === bestEst && lineId.localeCompare(bestLine, 'zh-CN') < 0)
    ) {
      bestEst = est;
      bestLine = lineId;
    }
  }
  return bestLine;
}

export function sequenceForEarliestStart(
  lineTasks: MachineScheduleTask[],
  earliestStartMinute: number,
): number {
  const sorted = [...lineTasks].sort(
    (a, b) => a.startMinute - b.startMinute || a.sequenceIndex - b.sequenceIndex,
  );
  for (let i = 0; i < sorted.length; i++) {
    if (sorted[i].startMinute >= earliestStartMinute) {
      return i + 1;
    }
  }
  return sorted.length + 1;
}

function virtualInsert(
  lineTasks: MachineScheduleTask[],
  op: DetailSchedulePlanningPreviewOperation,
  sequenceOnLine: number,
): MachineScheduleTask[] {
  const start = op.startMinute ?? op.earliestStartMinute ?? 0;
  const end = op.endMinute ?? start + 60;
  const others = lineTasks.filter((t) => t.operationId !== op.operationId);
  const sorted = [...others].sort(
    (a, b) => a.startMinute - b.startMinute || a.sequenceIndex - b.sequenceIndex,
  );
  const placeholder: MachineScheduleTask = {
    operationId: op.operationId,
    workOrderNo: op.workOrderNo,
    batchNo: op.batchNo,
    productCode: op.productCode,
    resourceId: op.resourceId,
    sequenceIndex: sequenceOnLine,
    startMinute: start,
    endMinute: end,
    pinned: op.pinned,
    displayPhase: 'scheduled',
    changeoverMinutesBefore: 0,
  };
  const index = Math.max(0, Math.min(sequenceOnLine - 1, sorted.length));
  sorted.splice(index, 0, placeholder);
  return sorted;
}

export function buildOperationPatch(
  op: DetailSchedulePlanningPreviewOperation,
  lineId: string,
  lineTasks: MachineScheduleTask[],
): SessionStepPatch {
  const earliest = op.earliestStartMinute ?? 0;
  const sequenceOnLine = sequenceForEarliestStart(lineTasks, earliest);
  return {
    stepId: op.operationId,
    lineId,
    sequenceOnLine,
  };
}

export function buildBatchPatches(
  operations: DetailSchedulePlanningPreviewOperation[],
  lineId: string,
  lineTasks: MachineScheduleTask[],
  dropMinute?: number,
): SessionStepPatch[] {
  const sorted = [...operations].sort((a, b) => a.operationSeq - b.operationSeq);
  const patches: SessionStepPatch[] = [];
  let virtual = [...lineTasks];

  for (let i = 0; i < sorted.length; i++) {
    const op = sorted[i];
    if (!op.operationId) continue;

    let sequenceOnLine: number;
    if (dropMinute != null) {
      const minute = dropMinute + i;
      sequenceOnLine = sequenceOnLineFromDropMinute(virtual, minute, op.operationId);
    } else {
      sequenceOnLine = sequenceForEarliestStart(virtual, op.earliestStartMinute ?? 0);
    }

    patches.push({
      stepId: op.operationId,
      lineId,
      sequenceOnLine,
    });
    virtual = virtualInsert(virtual, op, sequenceOnLine);
  }

  return patches;
}

export function batchNeedsLinePick(
  operations: DetailSchedulePlanningPreviewOperation[],
): boolean {
  return operations.some((op) => !op.lineId || !op.scheduled);
}

export function resolveLineForBatchSchedule(
  operations: DetailSchedulePlanningPreviewOperation[],
  preferredLineId?: string | null,
): string | null {
  if (preferredLineId) return preferredLineId;
  const assigned = operations.find((op) => op.lineId)?.lineId;
  return assigned ?? null;
}

export function ganttCommitToPatch(commit: GanttDragCommit): SessionStepPatch {
  return {
    stepId: commit.operationId,
    lineId: commit.lineId,
    sequenceOnLine: commit.sequenceOnLine,
  };
}

/** 从所有产线队列移除（取消计划）。 */
export function buildUnassignPatches(
  operations: DetailSchedulePlanningPreviewOperation[],
): SessionStepPatch[] {
  return operations
    .filter((op) => op.operationId && (op.scheduled || op.lineId))
    .map((op) => ({
      stepId: op.operationId,
      lineId: '',
    }));
}
