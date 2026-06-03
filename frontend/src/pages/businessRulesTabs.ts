import { api } from '../api/client';
import type { TabConfig } from '../components/MasterDataTabBody';
import type {
  ChangeoverMd,
  ContinuousProductionMd,
  MasterDataRecord,
  OperationPostProcessingMd,
  OperationTransferTimeMd,
  ParallelOperationMd,
  SystemParameterMd,
} from '../types/masterData';

const ATTRIBUTE_OPTIONS = [
  { value: 'wireMaterial', label: '线材' },
  { value: 'keyMaterial', label: '关键物料' },
  { value: 'maleFemaleEnd', label: '公母端' },
  { value: 'totalBranch', label: '分支' },
  { value: 'productCode', label: '料号' },
];

export const changeoverTab: TabConfig<ChangeoverMd> = {
  id: 'changeover',
  label: '换型矩阵',
  description:
    '按工序与工艺属性维护换型规则（泰科蓝图 KTPrefixDuration 格式）：前/后属性值支持 * 通配；同属性 *→* 表示属性值变化时生效',
  api: api.masterData.changeover,
  rowKey: (r) =>
    `${r.operationName}|${r.attributeKey}|${r.fromAttributeValue}->${r.toAttributeValue}`,
  search: (r) =>
    `${r.operationName} ${r.attributeKey} ${r.fromAttributeValue} ${r.toAttributeValue}`,
  emptyRow: () => ({
    id: null,
    operationName: '',
    attributeKey: 'wireMaterial',
    fromAttributeValue: '*',
    toAttributeValue: '*',
    setupMinutes: 15,
  }),
  columns: [
    { key: 'operationName', label: '工序', type: 'text', required: true, width: 120 },
    {
      key: 'attributeKey',
      label: '属性',
      type: 'select',
      required: true,
      width: 120,
      options: ATTRIBUTE_OPTIONS,
    },
    { key: 'fromAttributeValue', label: '前属性值', type: 'text', required: true, width: 120 },
    { key: 'toAttributeValue', label: '后属性值', type: 'text', required: true, width: 120 },
    { key: 'setupMinutes', label: '换型(分钟)', type: 'integer', required: true, width: 110 },
  ],
};

export const parallelOperationTab: TabConfig<ParallelOperationMd> = {
  id: 'parallel-operations',
  label: '并行工序',
  description:
    'U型线并行生产配对（U型线清单.xlsx）：两个半品料号在指定机台（产线ID）上需同时加工；两头齐全则同产线同起同止，缺一头则单排至其它可生产产线。',
  api: api.masterData.parallelOperations,
  rowKey: (r) => `${r.lineId}|${r.firstProductCode}+${r.secondProductCode}`,
  search: (r) => `${r.lineId} ${r.firstProductCode} ${r.secondProductCode}`,
  emptyRow: () => ({
    id: null,
    lineId: '',
    firstProductCode: '',
    secondProductCode: '',
  }),
  columns: [
    { key: 'lineId', label: '产线ID(机台)', type: 'text', required: true, width: 120 },
    { key: 'firstProductCode', label: '料号A', type: 'text', required: true, width: 150 },
    { key: 'secondProductCode', label: '料号B', type: 'text', required: true, width: 150 },
  ],
};

export const operationTransferTimeTab: TabConfig<OperationTransferTimeMd> = {
  id: 'operation-transfer-time',
  label: '工序衔接规则',
  description:
    '相邻工序衔接：最小/最大流转时间（分钟）与衔接模式（标准顺序、同时开始、延后开始、同时结束）；详细排程 Hard 约束与主计划甘特均生效。',
  api: api.masterData.operationTransferTime,
  rowKey: (r) => `${r.productCode}|${r.fromOperationName}->${r.toOperationName}`,
  search: (r) => `${r.productCode} ${r.fromOperationName} ${r.toOperationName} ${r.linkMode}`,
  emptyRow: () => ({
    id: null,
    productCode: '',
    fromOperationName: '',
    toOperationName: '',
    transferMinutes: 120,
    minTransferMinutes: 15,
    maxTransferMinutes: 120,
    linkMode: 'STANDARD',
    delayStartMinutes: 0,
  }),
  columns: [
    { key: 'productCode', label: '产品', type: 'text', required: true, width: 140 },
    { key: 'fromOperationName', label: '前工序', type: 'text', required: true, width: 120 },
    { key: 'toOperationName', label: '后工序', type: 'text', required: true, width: 120 },
    { key: 'minTransferMinutes', label: '最小流转(分)', type: 'integer', required: true, width: 110 },
    { key: 'maxTransferMinutes', label: '最大流转(分)', type: 'integer', required: true, width: 110 },
    {
      key: 'linkMode',
      label: '衔接模式',
      type: 'select',
      required: true,
      width: 130,
      options: [
        { value: 'STANDARD', label: '标准顺序' },
        { value: 'SIMULTANEOUS_START', label: '同时开始' },
        { value: 'DELAYED_START', label: '延后开始' },
        { value: 'SIMULTANEOUS_END', label: '同时结束' },
      ],
    },
    { key: 'delayStartMinutes', label: '延后窗口(分)', type: 'integer', width: 110 },
  ],
};

export const operationPostProcessingTab: TabConfig<OperationPostProcessingMd> = {
  id: 'operation-post-processing',
  label: '工序后处理时间',
  description:
    '末工序结束到工单可交付之间的后处理时间（分钟）；工序名填 * 表示该产品默认末工序后处理。',
  api: api.masterData.operationPostProcessing,
  rowKey: (r) => `${r.productCode}|${r.operationName}`,
  search: (r) => `${r.productCode} ${r.operationName}`,
  emptyRow: () => ({
    id: null,
    productCode: '',
    operationName: '*',
    postProcessingMinutes: 0,
  }),
  columns: [
    { key: 'productCode', label: '产品', type: 'text', required: true, width: 140 },
    { key: 'operationName', label: '工序', type: 'text', required: true, width: 120 },
    { key: 'postProcessingMinutes', label: '后处理(分)', type: 'integer', required: true, width: 110 },
  ],
};

export const continuousProductionTab: TabConfig<ContinuousProductionMd> = {
  id: 'continuous-production',
  label: '连续生产',
  description:
    '连续生产料号清单：指定机台上关联料号须连续排产，中间不得停留或插入其它料号；详细排程以硬约束保证同组工序不被隔开。',
  api: api.masterData.continuousProduction,
  rowKey: (r) => `${r.lineId}|${r.firstProductCode}+${r.secondProductCode}+${r.finishedProductCode}`,
  search: (r) => `${r.lineId} ${r.firstProductCode} ${r.secondProductCode} ${r.finishedProductCode}`,
  emptyRow: () => ({
    id: null,
    lineId: '',
    firstProductCode: '',
    secondProductCode: '',
    finishedProductCode: '',
  }),
  columns: [
    { key: 'firstProductCode', label: '半品第一头PN', type: 'text', width: 150 },
    { key: 'secondProductCode', label: '半品第二头PN', type: 'text', width: 150 },
    { key: 'finishedProductCode', label: '成品', type: 'text', width: 140 },
    { key: 'lineId', label: '机台', type: 'text', required: true, width: 100 },
  ],
};

/** 生产规则：工序 lead time、换型、流转、后处理等 */
export const PRODUCTION_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  changeoverTab as unknown as TabConfig<MasterDataRecord>,
  parallelOperationTab as unknown as TabConfig<MasterDataRecord>,
  operationTransferTimeTab as unknown as TabConfig<MasterDataRecord>,
  operationPostProcessingTab as unknown as TabConfig<MasterDataRecord>,
  continuousProductionTab as unknown as TabConfig<MasterDataRecord>,
];

/** @deprecated 使用 PRODUCTION_RULE_TABS；保留别名兼容旧引用 */
export const CAPACITY_RULE_TABS: TabConfig<MasterDataRecord>[] = PRODUCTION_RULE_TABS;

export const parameterTab: TabConfig<SystemParameterMd> = {
  id: 'parameters',
  label: '系统参数',
  api: api.masterData.parameters,
  rowKey: (r) => r.paramId,
  search: (r) => `${r.paramId} ${r.description ?? ''}`,
  emptyRow: () => ({
    id: null,
    paramId: '',
    paramValue: '',
    description: '',
  }),
  columns: [
    { key: 'paramId', label: '参数 ID', type: 'text', required: true, width: 240 },
    { key: 'paramValue', label: '参数值', type: 'text', required: true },
    { key: 'description', label: '说明', type: 'text' },
  ],
};
