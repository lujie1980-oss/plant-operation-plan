import type { BlockedSalesOrderLineMd, ValidationIssueMd } from '../types/masterData';
import { ruleCategoryId } from '../pages/businessRuleCategories';
import type { MasterDataFocusPage, MasterDataTableFocus } from './masterDataFocus';

export type HealthCategoryId =
  | 'materials'
  | 'boms'
  | 'routing'
  | 'resources'
  | 'lines'
  | 'calendar'
  | 'inventory'
  | 'sales-orders'
  | 'changeover'
  | 'parallel-ops'
  | 'operation-transfer-time'
  | 'continuous-production';

export interface HealthCategoryDef {
  id: HealthCategoryId;
  label: string;
  entityTypes: string[];
}

/** 左侧分类顺序与展示名称 */
export const HEALTH_CATEGORIES: HealthCategoryDef[] = [
  { id: 'materials', label: '物料主数据', entityTypes: ['Material'] },
  { id: 'boms', label: '物料 BOM', entityTypes: ['BomComponent'] },
  { id: 'routing', label: '工艺路径', entityTypes: ['ProductResource'] },
  { id: 'resources', label: '生产资源', entityTypes: ['ProductionResource'] },
  { id: 'lines', label: '产线', entityTypes: ['ProductionLine'] },
  { id: 'calendar', label: '资源日历', entityTypes: ['ResourceCalendar'] },
  { id: 'inventory', label: '库存', entityTypes: ['Inventory'] },
  { id: 'sales-orders', label: '销售订单', entityTypes: ['SalesOrderLine'] },
  { id: 'changeover', label: '换型矩阵', entityTypes: ['ChangeoverMatrix'] },
  { id: 'parallel-ops', label: '并行工序', entityTypes: ['ParallelOperationRule'] },
  { id: 'operation-transfer-time', label: '工序流转时间', entityTypes: ['OperationTransferTimeRule'] },
  { id: 'continuous-production', label: '连续生产', entityTypes: ['ContinuousProductionRule'] },
];

const ENTITY_TO_CATEGORY = new Map<string, HealthCategoryId>();
for (const cat of HEALTH_CATEGORIES) {
  for (const et of cat.entityTypes) {
    ENTITY_TO_CATEGORY.set(et, cat.id);
  }
}

export function categoryForEntityType(entityType: string): HealthCategoryId | null {
  return ENTITY_TO_CATEGORY.get(entityType) ?? null;
}

export const RULE_LABELS: Record<string, string> = {
  SO_LINE_DUP: '销售订单行重复',
  SO_PRODUCT_EMPTY: '产品编码为空',
  SO_QTY_NONPOSITIVE: '订单数量无效',
  SO_DUEDATE_EMPTY: '交期为空',
  PRODUCT_NO_ROUTING: '产品无工艺路线',
  PR_DUP: '工艺路线重复',
  PR_RESOURCE_MISSING: '工艺资源不存在',
  PR_PROCESS_TIME_NONPOSITIVE: '制造 CT 无效',
  RES_RATE_NONPOSITIVE: '产能速率无效',
  BOM_CRITICAL_CHILD_NO_ROUTING: '关键子件无工艺',
  BOM_SELF_REF: 'BOM 自引用',
  BOM_QTY_NONPOSITIVE: 'BOM 用量无效',
  BOM_CYCLE: 'BOM 循环引用',
  CALENDAR_MISSING: '资源无日历',
  CALENDAR_CAPACITY_NEGATIVE: '日历产能为负',
  INVENTORY_NEGATIVE: '在库数量为负',
  INVENTORY_RESERVED_GT_ONHAND: '占用超过在库',
  LINE_RESOURCE_MISSING: '产线资源不存在',
  LINE_RESOURCE_MULTI_LINE: '一资源多产线',
  CHANGEOVER_NEGATIVE: '换型时间为负',
  CHANGEOVER_SELF: '换型属性键无效',
  PARALLEL_OP_LINE_MISSING: '并行规则产线缺失',
  PARALLEL_OP_PRODUCT_NO_ROUTING: '并行料号无工艺',
  PARALLEL_OP_SAME_PRODUCT: '并行配对料号相同',
  OP_TRANSFER_NEGATIVE: '流转时间为负',
  OP_TRANSFER_MIN_GT_TRANSFER: '最小流转大于流转时间',
  OP_TRANSFER_SAME_OPERATION: '前后工序相同',
  OP_TRANSFER_PRODUCT_NO_ROUTING: '产品无工艺路线',
  OP_TRANSFER_OP_NOT_ON_ROUTING: '工序不在工艺路线中',
  CP_LINE_MISSING: '连续生产机台未维护',
  CP_NO_PRODUCT: '连续生产缺少料号',
  CP_PRODUCT_NO_ROUTING: '连续生产料号无工艺',
  PLAN_BLOCKED: '计划运算阻断',
};

export function ruleLabel(ruleId: string): string {
  return RULE_LABELS[ruleId] ?? ruleId;
}

/** 说明文案（后端 reason 乱码时回退） */
export const REASON_LABELS: Record<string, string> = {
  SO_LINE_DUP: '销售订单行 (salesOrderNo, salesOrderLineNo) 重复',
  SO_PRODUCT_EMPTY: '订单行 productCode 为空',
  SO_QTY_NONPOSITIVE: '订单行 orderQty <= 0',
  SO_DUEDATE_EMPTY: '订单行 dueDate 为空',
  PRODUCT_NO_ROUTING: '产品无工艺路线，无法生成工单',
  PR_DUP: '工艺路线 (productCode, resourceId) 重复',
  PR_RESOURCE_MISSING: '工艺路线引用的 resourceId 在生产资源中不存在',
  PR_PROCESS_TIME_NONPOSITIVE: '工艺 processTimeSeconds <= 0，主计划工时计算可能异常',
  RES_RATE_NONPOSITIVE: '生产资源 runRatePerHour <= 0，产能分析可能异常',
  BOM_CRITICAL_CHILD_NO_ROUTING: '关键子件无工艺路线，齐套/MRP 可能误判',
  BOM_SELF_REF: 'BOM 存在自引用',
  BOM_QTY_NONPOSITIVE: 'BOM componentQty <= 0，MRP/齐套无效',
  BOM_CYCLE: 'BOM 关键件存在循环引用，影响 MRP/工单展开',
  CALENDAR_MISSING: '生产资源未维护日历（多产线时请按产线 ID 维护，主计划将汇总）',
  CALENDAR_CAPACITY_NEGATIVE: '资源日历可用/不可用产能为负',
  INVENTORY_NEGATIVE: '库存 onhandQty 为负，MRP/齐套计算可能异常',
  INVENTORY_RESERVED_GT_ONHAND: '占用数量 reservedQty 超过 onhandQty，可用量将按 0 处理',
  LINE_RESOURCE_MISSING: '产线引用的生产资源不存在',
  LINE_RESOURCE_MULTI_LINE: '同一生产资源绑定多条产线，开线决策可能歧义',
  CHANGEOVER_NEGATIVE: '换型 setupMinutes 为负',
  CHANGEOVER_SELF: '未知属性键，支持: 线材/关键物料/分支/料号',
  PARALLEL_OP_LINE_MISSING: '产线未在产线主数据中维护（机台=产线ID）',
  PARALLEL_OP_PRODUCT_NO_ROUTING: '配对料号无工艺路线，排程将无法识别工序',
  PARALLEL_OP_SAME_PRODUCT: '第一头与第二头料号相同，请确认是否为有效配对',
  OP_TRANSFER_NEGATIVE: '流转时间或最小流转时间为负',
  OP_TRANSFER_MIN_GT_TRANSFER: '最小流转时间大于流转时间',
  OP_TRANSFER_SAME_OPERATION: '前工序与后工序相同',
  OP_TRANSFER_PRODUCT_NO_ROUTING: '产品无工艺路线，流转时间规则可能无法生效',
  OP_TRANSFER_OP_NOT_ON_ROUTING: '规则中的工序不在产品工艺路线中',
  CP_LINE_MISSING: '机台未在产线主数据中维护',
  CP_NO_PRODUCT: '连续生产规则至少需要一个料号',
  CP_PRODUCT_NO_ROUTING: '连续生产料号无工艺路线，排程可能无法识别',
};

function strField(fields: Record<string, unknown> | null | undefined, key: string): string | undefined {
  const v = fields?.[key];
  if (v == null) return undefined;
  return String(v);
}

export function issueReason(item: Pick<HealthListItem, 'ruleId' | 'reason' | 'fields'>): string {
  if (item.reason && !/[\uFFFD?]{3,}/.test(item.reason) && !item.reason.includes('????')) {
    return item.reason;
  }
  if (item.ruleId === 'LINE_RESOURCE_MULTI_LINE') {
    const lineA = strField(item.fields, 'lineA');
    const lineB = strField(item.fields, 'lineB');
    const resourceId = strField(item.fields, 'resourceId');
    if (lineA && lineB) {
      return `同一生产资源「${resourceId ?? ''}」绑定多条产线（${lineA}、${lineB}），开线决策可能歧义`;
    }
  }
  return REASON_LABELS[item.ruleId] ?? item.reason ?? ruleLabel(item.ruleId);
}

export interface HealthListItem {
  id: string;
  severity: 'ERROR' | 'WARNING';
  ruleId: string;
  entityType: string;
  entityKey: string;
  reason: string;
  fields: Record<string, unknown> | null;
  navigable: boolean;
  focus: MasterDataTableFocus | null;
}

function buildFocus(
  page: MasterDataFocusPage,
  tabId: string,
  searchQuery: string,
  highlightRowKey?: string,
): MasterDataTableFocus {
  return { page, tabId, searchQuery, highlightRowKey };
}

/** 由校验条目解析跳转目标 */
export function resolveIssueNavigation(issue: ValidationIssueMd): MasterDataTableFocus | null {
  const f = issue.fields ?? {};
  switch (issue.entityType) {
    case 'BomComponent': {
      const parent = strField(f, 'parentProductCode') ?? issue.entityKey.split('->')[0];
      const component = strField(f, 'componentProductCode') ?? issue.entityKey.split('->')[1];
      const finished = strField(f, 'finishedProductCode') ?? '';
      const search = [finished, parent, component].filter(Boolean).join(' ');
      const rowKey = `${finished}|${parent}->${component}`;
      return buildFocus('master-data', 'boms', search || issue.entityKey, rowKey);
    }
    case 'ProductResource': {
      const product = strField(f, 'productCode') ?? issue.entityKey.split('->')[0];
      const resource = strField(f, 'resourceId') ?? issue.entityKey.split('->')[1];
      return buildFocus('master-data', 'product-resources', `${product} ${resource}`.trim(), `${product}->${resource}`);
    }
    case 'ProductionResource': {
      const rid = strField(f, 'resourceId') ?? issue.entityKey;
      return buildFocus('master-data', 'resources', rid, rid);
    }
    case 'ProductionLine': {
      const lineId = strField(f, 'lineId') ?? issue.entityKey;
      return buildFocus('master-data', 'lines', lineId, lineId);
    }
    case 'ResourceCalendar': {
      const rid = strField(f, 'resourceId') ?? issue.entityKey;
      return buildFocus('master-data', 'calendar', rid, rid);
    }
    case 'Inventory': {
      const product = strField(f, 'productCode') ?? issue.entityKey.split('|')[1] ?? issue.entityKey;
      const sp = strField(f, 'stockingPointCode') ?? issue.entityKey.split('|')[0] ?? '';
      const rowKey = `${sp}|${product}`;
      return buildFocus('business-data', 'inventory', `${sp} ${product}`.trim(), rowKey);
    }
    case 'SalesOrderLine': {
      const so = strField(f, 'salesOrderNo');
      const line = f?.salesOrderLineNo;
      if (so && line != null) {
        const lineNo = Number(line);
        return buildFocus(
          'business-data',
          'sales-orders',
          `${so} ${lineNo}`,
          `${so}#${lineNo}`,
        );
      }
      return buildFocus('business-data', 'sales-orders', issue.entityKey, issue.entityKey.replace(':', '#'));
    }
    case 'ChangeoverMatrix':
      return buildFocus('business-rules', 'changeover', issue.entityKey.replace(/\|/g, ' '));
    case 'ParallelOperationRule': {
      const lineId = strField(f, 'lineId');
      const first = strField(f, 'firstProductCode');
      const second = strField(f, 'secondProductCode');
      if (lineId && first && second) {
        const rowKey = `${lineId}|${first}+${second}`;
        return buildFocus('business-rules', 'parallel-operations', `${lineId} ${first} ${second}`, rowKey);
      }
      const product = strField(f, 'productCode');
      return buildFocus('business-rules', 'parallel-operations', product ?? issue.entityKey);
    }
    case 'OperationTransferTimeRule': {
      const product = strField(f, 'productCode') ?? issue.entityKey.split('|')[0];
      const rowKey = issue.entityKey.includes('|') ? issue.entityKey : undefined;
      return buildFocus('business-rules', 'operation-transfer-time', product ?? issue.entityKey, rowKey);
    }
    case 'ContinuousProductionRule': {
      const lineId = strField(f, 'lineId');
      const rowKey = issue.entityKey.includes('|') ? issue.entityKey : undefined;
      return buildFocus('business-rules', 'continuous-production', lineId ?? issue.entityKey, rowKey);
    }
    default:
      return null;
  }
}

export function toHealthListItem(issue: ValidationIssueMd): HealthListItem {
  const focus = resolveIssueNavigation(issue);
  return {
    id: `${issue.severity}:${issue.ruleId}:${issue.entityType}:${issue.entityKey}`,
    severity: issue.severity,
    ruleId: issue.ruleId,
    entityType: issue.entityType,
    entityKey: issue.entityKey,
    reason: issue.reason,
    fields: issue.fields,
    navigable: focus != null,
    focus,
  };
}

export function blockedToHealthItems(lines: BlockedSalesOrderLineMd[]): HealthListItem[] {
  return lines.map((line) => {
    const entityKey = `${line.salesOrderNo}:${line.salesOrderLineNo}`;
    const focus = buildFocus(
      'business-data',
      'sales-orders',
      `${line.salesOrderNo} ${line.salesOrderLineNo}`,
      `${line.salesOrderNo}#${line.salesOrderLineNo}`,
    );
    return {
      id: `BLOCKED:${line.salesOrderNo}:${line.salesOrderLineNo}:${line.ruleId}`,
      severity: 'ERROR' as const,
      ruleId: line.ruleId,
      entityType: 'SalesOrderLine',
      entityKey,
      reason: line.reason,
      fields: {
        salesOrderNo: line.salesOrderNo,
        salesOrderLineNo: line.salesOrderLineNo,
      },
      navigable: true,
      focus,
    };
  });
}

export interface CategoryCounts {
  errors: number;
  warnings: number;
}

export function buildCategoryCounts(
  items: HealthListItem[],
): Map<HealthCategoryId, CategoryCounts> {
  const map = new Map<HealthCategoryId, CategoryCounts>();
  for (const cat of HEALTH_CATEGORIES) {
    map.set(cat.id, { errors: 0, warnings: 0 });
  }
  for (const item of items) {
    const catId = categoryForEntityType(item.entityType);
    if (!catId) continue;
    const c = map.get(catId)!;
    if (item.severity === 'ERROR') c.errors += 1;
    else c.warnings += 1;
  }
  return map;
}

export const MASTER_DATA_ROUTE = '/master-data';
export const BUSINESS_DATA_ROUTE = '/business-data';

export function routeForFocusPage(page: MasterDataFocusPage, tabId?: string): string {
  switch (page) {
    case 'master-data':
      return MASTER_DATA_ROUTE;
    case 'business-data':
      return BUSINESS_DATA_ROUTE;
    case 'business-rules':
      if (tabId) {
        return `/business-rules/${ruleCategoryId(tabId)}`;
      }
      return '/business-rules/capacity';
  }
}
