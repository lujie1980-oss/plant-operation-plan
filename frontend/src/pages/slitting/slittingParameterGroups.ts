import type { ParamGroupDef } from '../planParameterGroups';

/** 分切排样 · 优化参数（系统参数 slitting_*） */
export const SLITTING_PARAM_GROUPS: ParamGroupDef[] = [
  {
    id: 'all',
    label: '分切优化参数',
    description:
      '共 4 项可配置参数：2 项求解时限、2 项需求导入默认尺寸。切边余量取自母卷主数据；几何硬约束与软目标权重由求解器内置。',
    paramIds: [
      'slitting_solver_seconds',
      'slitting_session_solver_seconds',
      'slitting_default_child_width_mm',
      'slitting_default_child_length_mm',
    ],
  },
];

export const ALL_SLITTING_PARAM_IDS = new Set(SLITTING_PARAM_GROUPS.flatMap((g) => g.paramIds));

/** 参数 ID → 表格行内说明（补充 system_parameter.description） */
export const SLITTING_PARAM_LABELS: Record<string, string> = {
  slitting_solver_seconds: '整方案求解时限（秒）',
  slitting_session_solver_seconds: '工作台会话优化时限（秒）',
  slitting_default_child_width_mm: '导入默认子卷宽度（mm）',
  slitting_default_child_length_mm: '导入默认子卷长度（mm）',
};

/** 库中尚无记录时用于展示/创建占位行（与 ParameterRegistry 默认一致） */
export const SLITTING_PARAM_DEFAULTS: Record<string, { paramValue: string; description: string }> = {
  slitting_solver_seconds: {
    paramValue: '30',
    description: '整方案分切（方案级 solve）Timefold 求解最长运行秒数',
  },
  slitting_session_solver_seconds: {
    paramValue: '10',
    description: '母卷分切工作台会话层优化（自动分切、优化未锁定）最长运行秒数',
  },
  slitting_default_child_width_mm: {
    paramValue: '200',
    description: '从销售需求导入子分切订单时的默认宽度（mm）',
  },
  slitting_default_child_length_mm: {
    paramValue: '1000',
    description: '从销售需求导入子分切订单时的默认长度（mm）',
  },
};
