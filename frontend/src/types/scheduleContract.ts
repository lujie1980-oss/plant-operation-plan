export type ScheduleContractPenaltyMode = 'LINEAR' | 'QUADRATIC' | 'CAPPED';

export type ScheduleContractConfig = {
  weightDue: number;
  enableMpTarget: boolean;
  /** 为 true 时不得早于主计划契约开始日开工 */
  enableMpContractStartWait: boolean;
  weightMpLate: number;
  weightMpEarly: number;
  mpLateMode: ScheduleContractPenaltyMode;
  mpEarlyMode: ScheduleContractPenaltyMode;
  mpEarlyCapDays: number;
};

export const SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS: {
  value: ScheduleContractPenaltyMode;
  label: string;
  hint: string;
}[] = [
  { value: 'LINEAR', label: '线性', hint: '惩罚 = 权重 × 天数' },
  { value: 'QUADRATIC', label: '二次方', hint: '惩罚 = 权重 × 天数²' },
  { value: 'CAPPED', label: '封顶', hint: '线性惩罚，天数不超过上限（仅偏早）' },
];

export const DEFAULT_SCHEDULE_CONTRACT: ScheduleContractConfig = {
  weightDue: 100,
  enableMpTarget: false,
  enableMpContractStartWait: true,
  weightMpLate: 20,
  weightMpEarly: 60,
  mpLateMode: 'LINEAR',
  mpEarlyMode: 'QUADRATIC',
  mpEarlyCapDays: 0,
};
