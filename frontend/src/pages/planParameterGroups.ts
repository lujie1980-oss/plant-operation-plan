export type ParamGroupDef = {
  id: string;
  /** 页签与区块标题 */
  label: string;
  description: string;
  paramIds: string[];
};

/** 在「优化目标」等专用页面维护，不在计划参数页展示 */
export const PARAMS_MANAGED_ELSEWHERE = new Set([
  'master_plan_objective_weights',
  'master_plan_strategies',
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
  {
    id: 'shift',
    label: '班次产能',
    description: '单班次可用产能（分钟），用于资源日历与负荷换算。',
    paramIds: ['shift_capacity_minutes'],
  },
];

/** 生产排程 · 计划参数 */
export const SCHEDULING_PARAM_GROUPS: ParamGroupDef[] = [
  {
    id: 'material',
    label: '物料约束',
    description: '齐套锁定、物料可行性相关参数。',
    paramIds: ['kitting_lock_t_hours'],
  },
  {
    id: 'schedule',
    label: '排程求解',
    description: '详细排程求解器时限；保存后下次排程生效。',
    paramIds: ['detail_schedule_solver_seconds'],
  },
  {
    id: 'schedule-contract',
    label: '主计划衔接',
    description:
      '排程相对主计划日期的软约束权重（JSON）。字段含义：weight_due / weight_mp_late / weight_mp_early 等；保存后下次排程生效。',
    paramIds: ['detail_schedule_contract'],
  },
];

export const ALL_PLAN_PARAM_IDS = new Set([
  ...MASTER_PLAN_PARAM_GROUPS.flatMap((g) => g.paramIds),
  ...SCHEDULING_PARAM_GROUPS.flatMap((g) => g.paramIds),
  ...PARAMS_MANAGED_ELSEWHERE,
]);
