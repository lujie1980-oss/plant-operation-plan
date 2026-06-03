export type ParamGroupDef = {
  id: string;
  /** 页签与区块标题 */
  label: string;
  description: string;
  paramIds: string[];
  /** 使用专用表单面板，而非通用参数表格 */
  customPanel?: boolean;
};

/** 在「优化目标」等专用页面维护，不在计划参数页展示 */
export const PARAMS_MANAGED_ELSEWHERE = new Set([
  'master_plan_objective_weights',
  'master_plan_strategies',
  'detail_schedule_contract',
  'batch_split_mode',
  'batch_fixed_qty',
  'batch_min_qty',
  'batch_max_qty',
  'batch_remainder_mode',
  'batch_kitting_create_short_batch',
  'batch_auto_on_dispatch',
  'shift_capacity_minutes',
]);

/** 主计划 · 计划参数 */
export const MASTER_PLAN_PARAM_GROUPS: ParamGroupDef[] = [
  {
    id: 'horizon',
    label: '计划展望期',
    description:
      '主计划时间轴与冻结窗。总日历天数在默认模式下若大于「日栅天数 + 周桶数」，将自动扩展周桶。',
    paramIds: [
      'planning_horizon_days',
      'timeslot_granularity_mode',
      'timeslot_daily_days',
      'timeslot_weekly_buckets',
      'freeze_window_days',
    ],
  },
  {
    id: 'capacity',
    label: '产能约束',
    description: '产能超载判定与主计划求解器时限；保存后下次主计划运行生效。',
    paramIds: ['capacity_overload_threshold_pct', 'master_plan_solver_seconds'],
  },
];

/** 生产排程 · 计划参数 */
export const SCHEDULING_PARAM_GROUPS: ParamGroupDef[] = [
  {
    id: 'material',
    label: '物料约束',
    description: '未齐套工单仍可上产线，但最早开工时间相对排程锚点推后 kitting_lock_t_hours 小时。',
    paramIds: ['kitting_lock_t_hours'],
  },
  {
    id: 'schedule',
    label: '排程求解',
    description: '详细排程求解器时限；保存后下次排程生效。',
    paramIds: ['detail_schedule_solver_seconds'],
  },
  {
    id: 'batch-split',
    label: '批次拆解',
    description:
      '已下发工单的拆批策略与批量规则；保存后在批次计划页执行自动/手工拆批，或开启下发时自动拆批。',
    paramIds: [],
    customPanel: true,
  },
  {
    id: 'schedule-contract',
    label: '主计划衔接',
    description:
      '排程相对主计划目标完成日与工单交期的软约束权重与惩罚公式；保存后下次排程求解生效。',
    paramIds: [],
    customPanel: true,
  },
];

export const ALL_PLAN_PARAM_IDS = new Set([
  ...MASTER_PLAN_PARAM_GROUPS.flatMap((g) => g.paramIds),
  ...SCHEDULING_PARAM_GROUPS.flatMap((g) => g.paramIds),
  ...PARAMS_MANAGED_ELSEWHERE,
]);
