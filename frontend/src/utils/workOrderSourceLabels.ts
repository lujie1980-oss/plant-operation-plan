import type { WorkOrderSource } from '../types/api';

/** 工单来源展示名（API 仍用 EXTERNAL / REPLENISH） */
export const WORK_ORDER_SOURCE_LABEL: Record<WorkOrderSource, string> = {
  EXTERNAL: '成品工单',
  REPLENISH: '组件工单',
};

export function isFinishedGoodsSource(source: WorkOrderSource): boolean {
  return source === 'EXTERNAL';
}
