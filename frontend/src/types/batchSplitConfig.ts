export type BatchSplitMode = 'NONE' | 'FIXED_QTY' | 'KITTING' | 'AUTO';

export type BatchRemainderMode = 'FLOOR' | 'CEIL' | 'SEPARATE_TAIL' | 'MERGE_TAIL';

export type BatchSplitConfig = {
  mode: BatchSplitMode;
  fixedQty: number;
  minQty: number;
  maxQty: number;
  remainderMode: BatchRemainderMode;
  kittingCreateShortBatch: boolean;
  autoOnDispatch: boolean;
};

export const DEFAULT_BATCH_SPLIT_CONFIG: BatchSplitConfig = {
  mode: 'NONE',
  fixedQty: 100,
  minQty: 10,
  maxQty: 200,
  remainderMode: 'SEPARATE_TAIL',
  kittingCreateShortBatch: true,
  autoOnDispatch: false,
};

export const BATCH_SPLIT_MODE_OPTIONS: {
  value: BatchSplitMode;
  label: string;
  hint: string;
}[] = [
  { value: 'NONE', label: '不拆批次', hint: '每工单整单一个批次；下发后自动创建默认批次' },
  {
    value: 'FIXED_QTY',
    label: '固定数量拆批',
    hint: '按给定批量将剩余量拆成多个批次，余数处理方式可配置',
  },
  {
    value: 'KITTING',
    label: '齐套拆批',
    hint: '按当前库存可齐套量拆出 KITTED 批次，剩余可建 SHORT 批次或留父工单',
  },
  {
    value: 'AUTO',
    label: '自动拆批',
    hint: '在 min/max 范围内结合交期紧迫度与产线班产能启发式确定批量，并评估齐套',
  },
];

export const BATCH_REMAINDER_MODE_OPTIONS: {
  value: BatchRemainderMode;
  label: string;
  hint: string;
}[] = [
  { value: 'FLOOR', label: '向下取整', hint: '只拆整批，余量不建批次' },
  { value: 'CEIL', label: '向上取整', hint: '不足一批也单独成批' },
  { value: 'SEPARATE_TAIL', label: '尾批单独', hint: '整批 + 余数尾批' },
  { value: 'MERGE_TAIL', label: '尾批合并', hint: '最后一批 = 批量 + 余数' },
];

export const BATCH_SPLIT_PARAM_IDS = [
  'batch_split_mode',
  'batch_fixed_qty',
  'batch_min_qty',
  'batch_max_qty',
  'batch_remainder_mode',
  'batch_kitting_create_short_batch',
  'batch_auto_on_dispatch',
] as const;
