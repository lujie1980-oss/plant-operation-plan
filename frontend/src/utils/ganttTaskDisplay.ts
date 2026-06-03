import type { DetailScheduleOperation } from '../types/api';
import type { ScheduleFeedback } from '../types/api';
import type { ProductionTask } from '../types/scheduleSession';

/** 甘特条主体色：已排程 / 已发布 / 已反馈 */
export type GanttTaskDisplayPhase = 'scheduled' | 'released' | 'feedback';

export const GANTT_PHASE_LABEL: Record<GanttTaskDisplayPhase, string> = {
  scheduled: '已排程',
  released: '已发布',
  feedback: '已反馈',
};

export const GANTT_TASK_STYLE: Record<
  GanttTaskDisplayPhase,
  { fill: string; border: string }
> = {
  scheduled: { fill: '#22c55e', border: '#16a34a' },
  released: { fill: '#3b82f6', border: '#2563eb' },
  feedback: { fill: '#9ca3af', border: '#6b7280' },
};

export const GANTT_CHANGEOVER_STYLE = { fill: '#fb923c', border: '#ea580c' };

export function resolveGanttTaskPhase(
  operationId: string,
  productionTasks: ProductionTask[] | undefined,
  scheduleFeedback: ScheduleFeedback[] | undefined,
): GanttTaskDisplayPhase {
  const feedbackIds = new Set(
    (scheduleFeedback ?? []).map((f) => f.operationId).filter(Boolean),
  );
  if (feedbackIds.has(operationId)) {
    return 'feedback';
  }
  const task = (productionTasks ?? []).find((t) => t.stepId === operationId);
  if (task) {
    const state = task.executionState?.toUpperCase() ?? '';
    if (
      state === 'RELEASED' ||
      state === 'RUNNING' ||
      state === 'COMPLETED'
    ) {
      return 'released';
    }
  }
  return 'scheduled';
}

export function enrichOperationsForGantt(
  operations: DetailScheduleOperation[],
  productionTasks: ProductionTask[] | undefined,
  scheduleFeedback: ScheduleFeedback[] | undefined,
): DetailScheduleOperation[] {
  return operations.map((op) => ({
    ...op,
    displayPhase: resolveGanttTaskPhase(
      op.operationId,
      productionTasks,
      scheduleFeedback,
    ),
  }));
}
