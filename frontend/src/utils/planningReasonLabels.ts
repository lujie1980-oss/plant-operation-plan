/** 本体满足链 / 求解器信号 reasonCode 的人类可读标签 */
export const REASON_CODE_LABELS: Record<string, string> = {
  WO_NOT_SCHEDULABLE: '工单不可排程',
  WO_FROZEN_THROUGH_CUTOFF: '反馈冻结窗口',
  WO_NO_ROUTING: '无工艺路由',
  WO_NO_ALLOCATIONS: '无有效分配',
  ALLOC_NO_RESOURCE_SLOTS: '无可用槽位',
  ALLOC_TIMING_FALLBACK: '最早可行时窗回退',
  ALLOC_PARALLEL_NO_COMMON_SLOT: '并行槽无交集',
  WO_KITTING_SHORT: '齐套不足',
  OP_MP_CONTRACT: '主计划工序契约',
  OP_MP_TARGET_FALLBACK: '主计划末槽回退',
};

export function reasonLabel(code: string): string {
  return REASON_CODE_LABELS[code] ?? code;
}
