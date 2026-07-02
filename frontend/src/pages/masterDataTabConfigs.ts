import { api } from '../api/client';
import type {
  BomMd,
  DeliveryDateStrategyMd,
  InventoryMd,
  MasterDataRecord,
  MaterialMd,
  MaterialLeadTimeMd,
  ProductResourceMd,
  ProductionLineMd,
  ResourceCalendarMd,
  ResourceEfficiencyMd,
  ResourceMd,
  SalesOrderMd,
  ShiftHeadcountMd,
  SupplyQuantityRuleMd,
} from '../types/masterData';
import type { TabConfig } from '../components/MasterDataTabBody';

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'OPEN' },
  { value: 'PLANNED', label: 'PLANNED' },
  { value: 'IN_PROGRESS', label: 'IN_PROGRESS' },
  { value: 'COMPLETED', label: 'COMPLETED' },
  { value: 'CANCELLED', label: 'CANCELLED' },
];

const SHIFT_OPTIONS = [
  { value: 'DAY', label: 'DAY (白班)' },
  { value: 'S1', label: 'S1 (早班)' },
  { value: 'S2', label: 'S2 (晚班)' },
  { value: 'NIGHT', label: 'NIGHT (夜班)' },
];

async function loadBomParentProductCodes(): Promise<Set<string>> {
  const boms = await api.masterData.boms.list();
  const parents = new Set<string>();
  for (const b of boms) {
    const p = b.parentProductCode?.trim();
    if (p) parents.add(p);
  }
  return parents;
}

function salesOrderBomWarning(row: SalesOrderMd, context: unknown): string | null {
  const productCode = row.productCode?.trim();
  if (!productCode) return null;
  const parents = context as Set<string>;
  if (!parents.has(productCode)) {
    return '物料BOM不存在';
  }
  return null;
}

export const salesOrderTab: TabConfig<SalesOrderMd> = {
  id: 'sales-orders',
  validationEntityType: 'SalesOrderLine',
  label: '销售订单',
  description: '销售订单行（驱动需求满足与主计划求解）；无 BOM 的物料将显示预警',
  api: api.masterData.salesOrders,
  rowKey: (r) => `${r.salesOrderNo}#${r.salesOrderLineNo}`,
  validationEntityKey: (r) => `${r.salesOrderNo}:${r.salesOrderLineNo}`,
  search: (r) => `${r.salesOrderNo} ${r.productCode} ${r.customerCode ?? ''}`,
  warningContext: loadBomParentProductCodes,
  rowWarning: salesOrderBomWarning,
  emptyRow: () => ({
    id: null,
    salesOrderNo: '',
    salesOrderLineNo: 10,
    customerCode: '',
    productCode: '',
    orderQty: 0,
    uom: '',
    promiseDate: null,
    dueDate: new Date().toISOString().substring(0, 10),
    priority: 5,
    expediteLevel: 0,
    status: 'OPEN',
    scheduleLockFlag: false,
  }),
  columns: [
    { key: 'salesOrderNo', label: '订单号', type: 'text', required: true },
    { key: 'salesOrderLineNo', label: '行号', type: 'integer', required: true, width: 70 },
    { key: 'customerCode', label: '客户', type: 'text' },
    { key: 'productCode', label: '产品', type: 'text', required: true },
    { key: 'orderQty', label: '数量', type: 'number', required: true, width: 100 },
    { key: 'uom', label: '单位', type: 'text', width: 70 },
    { key: 'dueDate', label: '交期', type: 'date', required: true, width: 130 },
    { key: 'promiseDate', label: '承诺日期', type: 'date', width: 130 },
    { key: 'priority', label: '优先级', type: 'integer', width: 80 },
    { key: 'expediteLevel', label: '加急', type: 'integer', width: 70 },
    { key: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 120 },
    { key: 'scheduleLockFlag', label: '锁定', type: 'boolean', width: 60 },
  ],
};

export const materialTab: TabConfig<MaterialMd> = {
  id: 'materials',
  validationEntityType: 'Material',
  label: '物料主数据',
  description: '物料编码、名称、单位与类型；扩展属性由当前 workspace 字段目录配置',
  fieldSchemaEntityType: 'MATERIAL',
  customFieldsUseExtensions: true,
  api: api.masterData.materials,
  rowKey: (r) => r.materialCode,
  search: (r) => `${r.materialCode} ${r.materialName ?? ''} ${r.siteCode ?? ''} ${r.materialType ?? ''}`,
  emptyRow: () => ({
    id: null,
    siteCode: '',
    materialCode: '',
    materialName: '',
    uomCode: '',
    materialType: '',
    extensions: {},
  }),
  columns: [
    { key: 'siteCode', label: '基地代码', type: 'text', width: 100 },
    { key: 'materialCode', label: '产品代码', type: 'text', required: true, width: 140 },
    { key: 'materialName', label: '产品名称', type: 'text', width: 180 },
    { key: 'uomCode', label: '主计量单位', type: 'text', width: 110 },
    { key: 'materialType', label: '物料类型', type: 'text', width: 110 },
  ],
};

export const bomTab: TabConfig<BomMd> = {
  id: 'boms',
  validationEntityType: 'BomComponent',
  label: '物料清单 BOM',
  description: '成品料号、父项/组件结构、生效日期与损耗率（用于齐套与多级工单展开）',
  api: api.masterData.boms,
  rowKey: (r) => `${r.finishedProductCode ?? ''}|${r.parentProductCode}->${r.componentProductCode}`,
  validationEntityKey: (r) => `${r.parentProductCode}->${r.componentProductCode}`,
  search: (r) =>
    `${r.finishedProductCode ?? ''} ${r.parentProductCode} ${r.componentProductCode} ${r.bomId}`,
  emptyRow: () => ({
    id: null,
    finishedProductCode: '',
    bomId: 'BOM-DEMO',
    bomVersion: 'V1',
    parentProductCode: '',
    componentProductCode: '',
    componentQty: 1,
    isCriticalComponent: true,
    bomEffectiveFrom: null,
    bomEffectiveTo: null,
    componentEffectiveFrom: null,
    componentEffectiveTo: null,
    scrapRate: null,
  }),
  columns: [
    { key: 'finishedProductCode', label: '成品料号', type: 'text', width: 130 },
    { key: 'bomId', label: 'BOM ID', type: 'text', required: true, width: 110 },
    { key: 'bomVersion', label: '版本', type: 'text', width: 80 },
    { key: 'parentProductCode', label: '产品代码(父项)', type: 'text', required: true, width: 140 },
    { key: 'componentProductCode', label: '组件代码', type: 'text', required: true, width: 140 },
    { key: 'componentQty', label: '组件数量', type: 'number', required: true, width: 100 },
    { key: 'isCriticalComponent', label: '关键件', type: 'boolean', width: 80 },
    { key: 'scrapRate', label: '组件损耗率', type: 'number', width: 110 },
    { key: 'bomEffectiveFrom', label: 'BOM生效', type: 'date', width: 120 },
    { key: 'bomEffectiveTo', label: 'BOM失效', type: 'date', width: 120 },
    { key: 'componentEffectiveFrom', label: '组件生效', type: 'date', width: 120 },
    { key: 'componentEffectiveTo', label: '组件失效', type: 'date', width: 120 },
  ],
};

export const inventoryTab: TabConfig<InventoryMd> = {
  id: 'inventory',
  validationEntityType: 'Inventory',
  validationEntityKey: (r) => `${r.stockingPointCode}|${r.productCode}`,
  label: '库存',
  description: '在库 / 在途 / 占用 / 质量持有 数量',
  api: api.masterData.inventory,
  rowKey: (r) => `${r.stockingPointCode}|${r.productCode}|${r.id ?? 'new'}`,
  search: (r) => `${r.productCode} ${r.stockingPointCode}`,
  emptyRow: () => ({
    id: null,
    stockingPointCode: 'WH-01',
    productCode: '',
    onhandQty: 0,
    reservedQty: 0,
    qualityHoldQty: 0,
    inTransitQty: 0,
  }),
  columns: [
    { key: 'stockingPointCode', label: '库存点', type: 'text', required: true, width: 120 },
    { key: 'productCode', label: '产品', type: 'text', required: true },
    { key: 'onhandQty', label: '在库数量', type: 'number', required: true, width: 110 },
    { key: 'reservedQty', label: '占用', type: 'number', width: 100 },
    { key: 'qualityHoldQty', label: '质量持有', type: 'number', width: 110 },
    { key: 'inTransitQty', label: '在途', type: 'number', width: 100 },
  ],
};

export const resourceTab: TabConfig<ResourceMd> = {
  id: 'resources',
  validationEntityType: 'ProductionResource',
  validationEntityKey: (r) => r.resourceId,
  label: '生产资源',
  description: '机台/工位的产能与瓶颈属性',
  api: api.masterData.resources,
  rowKey: (r) => r.resourceId,
  search: (r) => `${r.resourceId} ${r.areaId} ${r.resourceGroup ?? ''}`,
  emptyRow: () => ({
    id: null,
    resourceId: '',
    resourceGroup: '',
    areaId: 'AREA-1',
    bottleneck: false,
    runRatePerHour: 60,
  }),
  columns: [
    { key: 'resourceId', label: '资源 ID', type: 'text', required: true, width: 140 },
    { key: 'resourceGroup', label: '资源组', type: 'text', width: 120 },
    { key: 'areaId', label: '区域', type: 'text', required: true, width: 110 },
    { key: 'bottleneck', label: '瓶颈', type: 'boolean', width: 70 },
    { key: 'runRatePerHour', label: '小时产能', type: 'number', required: true, width: 120 },
  ],
};

export const productResourceTab: TabConfig<ProductResourceMd> = {
  id: 'product-resources',
  validationEntityType: 'ProductResource',
  validationEntityKey: (r) => `${r.productCode}->${r.resourceId}`,
  label: '产品工艺',
  description: '工序、设备组、制造 CT（基础字段）及 workspace 扩展属性（线材/关键物料等，来自字段目录）',
  fieldSchemaEntityType: 'PRODUCT_RESOURCE',
  api: api.masterData.productResources,
  rowKey: (r) => `${r.productCode}@${r.sequenceNo ?? 0}@${r.resourceId}`,
  search: (r) =>
    `${r.productCode} ${r.resourceId} ${r.operationName ?? ''} ${r.wireMaterial ?? ''} ${r.keyMaterial ?? ''}`,
  emptyRow: () => ({
    id: null,
    productCode: '',
    resourceId: '',
    setupTimeMinutes: 30,
    sequenceNo: 1,
    resourcePriority: 1,
    operationName: '',
    processTimeSeconds: null,
    bomLevel: '',
    wireMaterial: '',
    keyMaterial: '',
    maleFemaleEnd: '',
    totalBranch: '',
    standardLabor: null,
    extensions: {},
  }),
  columns: [
    { key: 'productCode', label: '料号', type: 'text', required: true, width: 130 },
    { key: 'sequenceNo', label: '工序编号', type: 'integer', width: 90 },
    { key: 'resourcePriority', label: '资源优先级', type: 'integer', width: 100 },
    { key: 'operationName', label: '工序名称', type: 'text', width: 120 },
    { key: 'resourceId', label: '设备组', type: 'text', required: true, width: 100 },
    { key: 'processTimeSeconds', label: '制造CT(秒)', type: 'number', width: 110 },
    { key: 'setupTimeMinutes', label: '换型(分钟)', type: 'integer', required: true, width: 100 },
  ],
};

export const lineTab: TabConfig<ProductionLineMd> = {
  id: 'lines',
  validationEntityType: 'ProductionLine',
  validationEntityKey: (r) => r.lineId,
  label: '产线',
  description: '产线编制与每班次产能',
  api: api.masterData.lines,
  rowKey: (r) => r.lineId,
  search: (r) => `${r.lineId} ${r.areaId} ${r.resourceId}`,
  emptyRow: () => ({
    id: null,
    lineId: '',
    areaId: 'AREA-1',
    resourceId: '',
    lineMinHeadcount: 2,
    lineCapacityPerShift: 480,
  }),
  columns: [
    { key: 'lineId', label: '产线 ID', type: 'text', required: true, width: 140 },
    { key: 'areaId', label: '区域', type: 'text', required: true, width: 110 },
    { key: 'resourceId', label: '关联资源', type: 'text', required: true },
    { key: 'lineMinHeadcount', label: '最小人数', type: 'integer', required: true, width: 110 },
    { key: 'lineCapacityPerShift', label: '每班产能(分钟)', type: 'integer', required: true, width: 150 },
  ],
};

export const calendarTab: TabConfig<ResourceCalendarMd> = {
  id: 'calendar',
  validationEntityType: 'ResourceCalendar',
  validationEntityKey: (r) => `${r.resourceId}|${r.shiftId}|${r.calendarDate}`,
  label: '资源日历',
  description:
    '资源在日期/班次的可用产能。同一生产资源下有多条产线时，请用产线 ID 作为「资源」列维护各产线日历，主计划自动按产线日历之和汇总到该生产资源。',
  api: api.masterData.calendar,
  rowKey: (r) => `${r.resourceId}|${r.calendarDate}|${r.shiftId}|${r.id ?? 'new'}`,
  search: (r) => `${r.resourceId} ${r.shiftId} ${r.calendarDate}`,
  emptyRow: () => ({
    id: null,
    resourceId: '',
    shiftId: 'DAY',
    calendarDate: new Date().toISOString().substring(0, 10),
    availableCapacityMinutes: 480,
    unavailableCapacityMinutes: 0,
  }),
  columns: [
    { key: 'resourceId', label: '资源/产线ID', type: 'text', required: true },
    { key: 'calendarDate', label: '日期', type: 'date', required: true, width: 140 },
    { key: 'shiftId', label: '班次', type: 'select', options: SHIFT_OPTIONS, required: true, width: 130 },
    { key: 'availableCapacityMinutes', label: '可用(分钟)', type: 'integer', required: true, width: 120 },
    { key: 'unavailableCapacityMinutes', label: '不可用(分钟)', type: 'integer', width: 140 },
  ],
};

export const headcountTab: TabConfig<ShiftHeadcountMd> = {
  id: 'shift-headcount',
  label: '班次人员',
  description: '各区域/班次的可用人员数',
  api: api.masterData.shiftHeadcount,
  rowKey: (r) => `${r.areaId}|${r.calendarDate}|${r.shiftId}|${r.id ?? 'new'}`,
  search: (r) => `${r.areaId} ${r.shiftId} ${r.calendarDate}`,
  emptyRow: () => ({
    id: null,
    areaId: 'AREA-1',
    shiftId: 'DAY',
    calendarDate: new Date().toISOString().substring(0, 10),
    availableHeadcount: 8,
  }),
  columns: [
    { key: 'areaId', label: '区域', type: 'text', required: true, width: 130 },
    { key: 'calendarDate', label: '日期', type: 'date', required: true, width: 140 },
    { key: 'shiftId', label: '班次', type: 'select', options: SHIFT_OPTIONS, required: true, width: 130 },
    { key: 'availableHeadcount', label: '可用人数', type: 'integer', required: true, width: 110 },
  ],
};

export const MASTER_DATA_TABS: TabConfig<MasterDataRecord>[] = [
  materialTab,
  bomTab,
  resourceTab,
  productResourceTab,
  lineTab,
  calendarTab,
  headcountTab,
] as unknown as TabConfig<MasterDataRecord>[];

export const BUSINESS_DATA_TABS: TabConfig<MasterDataRecord>[] = [
  salesOrderTab,
  inventoryTab,
] as unknown as TabConfig<MasterDataRecord>[];

export const LABOR_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  {
    ...headcountTab,
    id: 'shift-headcount-rules',
    label: '班次人员',
    description: '各区域/班次的可用人员数，影响排程人力约束。',
  } as unknown as TabConfig<MasterDataRecord>,
];

export const materialLeadTimeTab: TabConfig<MaterialLeadTimeMd> = {
  id: 'material-lead-time',
  label: '采购提前期',
  description:
    '维护各物料最长采购周期。物料填 * 的一行表示「默认最长采购周期」，供 RULE-MRP-04 / RULE-PLAN-01 在物料短缺时推算最晚可用 Supply 日期；精确物料行优先于 * 行。',
  showDescription: false,
  api: api.masterData.materialLeadTime,
  rowKey: (r) => r.productCode,
  search: (r) => r.productCode,
  emptyRow: () => ({
    id: null,
    productCode: '',
    leadTimeDays: 7,
  }),
  warningContext: async () => {
    const list = await api.masterData.materialLeadTime.list();
    const wildcardCount = list.filter((r) => r.productCode.trim() === '*').length;
    return { wildcardCount };
  },
  rowWarning: (row, ctx) => {
    const { wildcardCount } = ctx as { wildcardCount: number };
    if (row.productCode.trim() === '*' && wildcardCount > 1) {
      return '默认最长采购周期（物料 *）应仅保留一行';
    }
    return null;
  },
  getRowClassName: (row) => (row.productCode.trim() === '*' ? 'br-mlt-default-row' : undefined),
  columns: [
    {
      key: 'productCode',
      label: '物料编码',
      type: 'text',
      required: true,
      width: 200,
    },
    {
      key: 'leadTimeDays',
      label: '最长采购周期(天)',
      type: 'integer',
      required: true,
      width: 160,
    },
  ],
};

const MIN_QTY_STRATEGY_OPTIONS = [
  { value: 'SKIP', label: '跳过（不下单）' },
  { value: 'PLAN_AT_MIN', label: '按最小量下单' },
];

const supplyQuantityRulesTab: TabConfig<SupplyQuantityRuleMd> = {
  id: 'supply-quantity-rules',
  label: '供应批量规则',
  description: '物料规则：产品×库存点的批量、最小/最大工单量与缺量策略（RULE-SUP-01）',
  api: api.masterData.supplyQuantityRules,
  rowKey: (r) => `${r.productCode}|${r.stockingPointCode}`,
  search: (r) => `${r.productCode} ${r.stockingPointCode}`,
  emptyRow: () => ({
    id: null,
    productCode: '',
    stockingPointCode: 'DEFAULT-FG',
    lotSize: 1,
    minQuantity: 1,
    maxQuantity: 99999,
    minQtyStrategy: 'PLAN_AT_MIN',
  }),
  columns: [
    { key: 'productCode', label: '产品', type: 'text', required: true, width: 150 },
    { key: 'stockingPointCode', label: '库存点', type: 'text', required: true, width: 120 },
    { key: 'lotSize', label: '批量倍数', type: 'integer', required: true, width: 100 },
    { key: 'minQuantity', label: '最小工单量', type: 'integer', required: true, width: 110 },
    { key: 'maxQuantity', label: '最大工单量', type: 'integer', required: true, width: 110 },
    {
      key: 'minQtyStrategy',
      label: '缺量策略',
      type: 'select',
      options: MIN_QTY_STRATEGY_OPTIONS,
      required: true,
      width: 150,
    },
  ],
};

export const MATERIAL_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  {
    ...bomTab,
    id: 'bom-rules',
    label: 'BOM 关键件',
    description: '物料规则：关键件标记影响齐套与 MRP 可行性判定',
    columns: bomTab.columns.filter((c) =>
      [
        'finishedProductCode',
        'parentProductCode',
        'componentProductCode',
        'componentQty',
        'isCriticalComponent',
        'scrapRate',
      ].includes(c.key),
    ),
  } as unknown as TabConfig<MasterDataRecord>,
  materialLeadTimeTab as unknown as TabConfig<MasterDataRecord>,
  supplyQuantityRulesTab as unknown as TabConfig<MasterDataRecord>,
];

const resourceEfficiencyTab: TabConfig<ResourceEfficiencyMd> = {
  id: 'resource-efficiency',
  label: '资源效率',
  description:
    '产能规则：资源效率系数与细排反馈占用（RULE-SUP-05）。有效产能 = (日历 − 停机 − 排程反馈) × 效率。',
  api: api.masterData.resourceEfficiency,
  rowKey: (r) => r.resourceId,
  search: (r) => `${r.resourceId} ${r.resourceGroupCode}`,
  emptyRow: () => ({
    id: null,
    resourceId: '',
    resourceGroupCode: '',
    resourceEfficiency: 1.0,
    schedulerFeedbackMinutes: 0,
  }),
  columns: [
    { key: 'resourceId', label: '资源 ID', type: 'text', required: true, width: 140 },
    { key: 'resourceGroupCode', label: '资源组', type: 'text', width: 120 },
    { key: 'resourceEfficiency', label: '效率系数 (0,1]', type: 'number', required: true, width: 130 },
    {
      key: 'schedulerFeedbackMinutes',
      label: '细排反馈占用(分)',
      type: 'integer',
      width: 150,
    },
  ],
};

const schedulerFeedbackTab: TabConfig<{ id: null }> = {
  id: 'scheduler-feedback',
  label: '细排反馈占用',
  description:
    '产能规则：S05 冻结细排反馈（txn schedule_feedback），占用 rollup 至 PRP/SRP（RULE-SUP-05）。本页只读。',
  api: {
    list: async () => [],
    save: async () => {
      throw new Error('细排反馈为交易数据，不可在此编辑');
    },
    delete: async () => {
      throw new Error('细排反馈为交易数据，不可删除');
    },
  },
  rowKey: () => 'scheduler-feedback',
  search: () => '',
  emptyRow: () => ({ id: null }),
  columns: [],
};

export const CAPACITY_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  resourceEfficiencyTab as unknown as TabConfig<MasterDataRecord>,
  schedulerFeedbackTab as unknown as TabConfig<MasterDataRecord>,
];

const DELIVERY_GRANULARITY_OPTIONS = [
  { value: 'DAILY', label: '日交付' },
  { value: 'WEEKLY', label: '周交付' },
];

const deliveryDateStrategyTab: TabConfig<DeliveryDateStrategyMd> = {
  id: 'delivery-date-strategy',
  label: '交期策略',
  description: '需求规则：提前/延后容忍窗口与分段惩罚系数（RULE-DEM-03 → KPI-MP-S01）',
  api: api.masterData.deliveryDateStrategy,
  rowKey: (r) => `${r.customerCode}|${r.productCode}`,
  search: (r) => `${r.customerCode} ${r.productCode}`,
  emptyRow: () => ({
    id: null,
    customerCode: '*',
    productCode: '*',
    deliveryGranularity: 'DAILY',
    earlyAllowDays: 1,
    lateAllowDays: 3,
    earlyPenaltyCoef: 1.0,
    latePenaltyCoef: 2.0,
  }),
  columns: [
    { key: 'customerCode', label: '客户', type: 'text', required: true, width: 120 },
    { key: 'productCode', label: '产品', type: 'text', required: true, width: 140 },
    {
      key: 'deliveryGranularity',
      label: '交付粒度',
      type: 'select',
      options: DELIVERY_GRANULARITY_OPTIONS,
      required: true,
      width: 110,
    },
    { key: 'earlyAllowDays', label: '允许提前(天)', type: 'integer', required: true, width: 120 },
    { key: 'lateAllowDays', label: '允许延后(天)', type: 'integer', required: true, width: 120 },
    { key: 'earlyPenaltyCoef', label: '提前惩罚系数', type: 'number', width: 120 },
    { key: 'latePenaltyCoef', label: '延后惩罚系数', type: 'number', width: 120 },
  ],
};

export const DEMAND_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  {
    ...salesOrderTab,
    id: 'demand-priority-rules',
    label: '订单优先级',
    description: '需求规则：优先级、加急等级与排程锁定',
    columns: salesOrderTab.columns.filter((c) =>
      ['salesOrderNo', 'salesOrderLineNo', 'priority', 'expediteLevel', 'scheduleLockFlag', 'dueDate'].includes(
        c.key,
      ),
    ),
  } as unknown as TabConfig<MasterDataRecord>,
  deliveryDateStrategyTab as unknown as TabConfig<MasterDataRecord>,
];
