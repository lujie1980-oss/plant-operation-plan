import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';
import type { ProductionTask } from '../types/scheduleSession';

export type BatchSchedulePhase = 'UNPLANNED' | 'PLANNED' | 'RELEASED' | 'EXECUTING';

export const BATCH_SCHEDULE_PHASE_LABEL: Record<BatchSchedulePhase, string> = {
  UNPLANNED: '未排产',
  PLANNED: '已排产',
  RELEASED: '已发布',
  EXECUTING: '已执行',
};

export function resolveBatchSchedulePhase(
  batchNo: string,
  previewOps: DetailSchedulePlanningPreviewOperation[] | undefined,
  productionTasks: ProductionTask[] | undefined,
): BatchSchedulePhase {
  const tasks = (productionTasks ?? []).filter((t) => t.batchNo === batchNo);
  if (
    tasks.some(
      (t) => t.executionState === 'RUNNING' || t.executionState === 'COMPLETED',
    )
  ) {
    return 'EXECUTING';
  }
  if (tasks.some((t) => t.executionState === 'RELEASED')) {
    return 'RELEASED';
  }
  const ops = (previewOps ?? []).filter((o) => o.batchNo === batchNo);
  if (ops.length === 0) {
    return 'UNPLANNED';
  }
  if (ops.some((o) => o.scheduled || o.lineId)) {
    return 'PLANNED';
  }
  return 'UNPLANNED';
}
