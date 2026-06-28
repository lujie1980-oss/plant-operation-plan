import type { MasterDataRecord } from '../types/masterData';
import type { RowRelationLink } from '../components/table/types';
import type { MasterDataTableFocus } from './masterDataFocus';

type RelationResolver<T = unknown> = (row: T) => RowRelationLink[];

function focus(
  page: MasterDataTableFocus['page'],
  tabId: string,
  searchQuery: string,
  highlightRowKey?: string,
): MasterDataTableFocus {
  return { page, tabId, searchQuery, highlightRowKey };
}

function salesOrderRelations(row: {
  salesOrderNo?: string;
  salesOrderLineNo?: number | string;
  productCode?: string;
}): RowRelationLink[] {
  const so = row.salesOrderNo;
  const line = row.salesOrderLineNo;
  if (!so) return [];
  const lineNo = line != null ? Number(line) : NaN;
  const rowKey = Number.isFinite(lineNo) ? `${so}#${lineNo}` : so;
  const search = Number.isFinite(lineNo) ? `${so} ${lineNo}` : so;
  const links: RowRelationLink[] = [
    {
      id: 'so-demand',
      label: '需求满足',
      to: '/master-plan/analysis/demand',
      search: `order=${encodeURIComponent(so)}`,
    },
    {
      id: 'so-md',
      label: '销售订单（主数据）',
      to: '/business-data',
      masterDataFocus: focus('business-data', 'sales-orders', search, rowKey),
    },
  ];
  if (row.productCode) {
    links.push({
      id: 'so-bom',
      label: '物料 BOM',
      to: '/master-data',
      masterDataFocus: focus('master-data', 'boms', row.productCode, `${row.productCode}|`),
    });
    links.push({
      id: 'so-routing',
      label: '工艺路径',
      to: '/master-data',
      masterDataFocus: focus('master-data', 'product-resources', row.productCode),
    });
  }
  return links;
}

function workOrderRelations(row: {
  workOrderNo?: string;
  productCode?: string;
  salesOrderNo?: string;
}): RowRelationLink[] {
  const wo = row.workOrderNo;
  if (!wo) return [];
  const links: RowRelationLink[] = [
    {
      id: 'wo-pp',
      label: '生产工单分析',
      to: '/master-plan/analysis/work-orders',
      search: `wo=${encodeURIComponent(wo)}`,
    },
    {
      id: 'wo-batch',
      label: '批次计划',
      to: '/scheduling/batch-plan',
      search: `q=${encodeURIComponent(wo)}`,
    },
    {
      id: 'wo-pending',
      label: '待排工单',
      to: '/scheduling/pending-work-orders',
      search: `q=${encodeURIComponent(wo)}`,
    },
    {
      id: 'wo-ds',
      label: '生产排程',
      to: '/scheduling/detail-schedule',
    },
  ];
  if (row.salesOrderNo) {
    links.push({
      id: 'wo-so',
      label: '销售订单',
      to: '/business-data',
      masterDataFocus: focus('business-data', 'sales-orders', row.salesOrderNo),
    });
  }
  if (row.productCode) {
    links.push({
      id: 'wo-pr',
      label: '工艺路径',
      to: '/master-data',
      masterDataFocus: focus('master-data', 'product-resources', row.productCode),
    });
  }
  return links;
}

const REGISTRY: Record<string, RelationResolver> = {
  SalesOrderLine: (row) => salesOrderRelations(row as Parameters<typeof salesOrderRelations>[0]),
  Material: (row) => {
    const code = (row as { productCode?: string }).productCode;
    if (!code) return [];
    return [
      {
        id: 'mat-bom',
        label: '物料 BOM',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'boms', code),
      },
      {
        id: 'mat-pr',
        label: '工艺路径',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'product-resources', code),
      },
    ];
  },
  BomComponent: (row) => {
    const r = row as {
      finishedProductCode?: string;
      parentProductCode?: string;
      componentProductCode?: string;
    };
    const parent = r.parentProductCode ?? '';
    const comp = r.componentProductCode ?? '';
    const finished = r.finishedProductCode ?? '';
    const highlightKey = `${finished}|${parent}->${comp}`;
    return [
      {
        id: 'bom-self',
        label: 'BOM 本行',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'boms', parent || comp, highlightKey),
      },
      {
        id: 'bom-parent-pr',
        label: '父件工艺',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'product-resources', parent, `${parent}->`),
      },
      {
        id: 'bom-child-pr',
        label: '子件工艺',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'product-resources', comp, `${comp}->`),
      },
    ];
  },
  ProductResource: (row) => {
    const r = row as { productCode?: string; resourceId?: string };
    if (!r.productCode) return [];
    return [
      {
        id: 'pr-bom',
        label: '物料 BOM',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'boms', r.productCode),
      },
    ];
  },
  ProductionResource: (row) => {
    const rid = (row as { resourceId?: string }).resourceId;
    if (!rid) return [];
    return [
      {
        id: 'res-cal',
        label: '资源日历',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'calendar', rid, rid),
      },
      {
        id: 'res-lines',
        label: '产线',
        to: '/master-data',
        masterDataFocus: focus('master-data', 'lines', rid),
      },
    ];
  },
  Inventory: (row) => {
    const r = row as { productCode?: string; stockingPointCode?: string };
    const rowKey = `${r.stockingPointCode ?? ''}|${r.productCode ?? ''}`;
    return [
      {
        id: 'inv-md',
        label: '库存主数据',
        to: '/business-data',
        masterDataFocus: focus('business-data', 'inventory', `${r.stockingPointCode ?? ''} ${r.productCode ?? ''}`.trim(), rowKey),
      },
      {
        id: 'inv-kitting',
        label: '物料齐套',
        to: '/scheduling/kitting',
        search: r.productCode ? `q=${encodeURIComponent(r.productCode)}` : undefined,
      },
    ];
  },
  WorkOrder: (row) => workOrderRelations(row as Parameters<typeof workOrderRelations>[0]),
  ChildSlittingOrder: (row) => {
    const code = (row as { orderCode?: string }).orderCode;
    if (!code) return [];
    return [
      {
        id: 'co-studio',
        label: '分切工作台',
        to: '/slitting/studio',
        search: `order=${encodeURIComponent(code)}`,
      },
    ];
  },
  MasterRoll: (row) => {
    const code = (row as { rollCode?: string }).rollCode;
    if (!code) return [];
    return [
      {
        id: 'mr-studio',
        label: '分切工作台',
        to: '/slitting/studio',
        search: `master=${encodeURIComponent(code)}`,
      },
    ];
  },
};

export function getTableRelations(entityType: string | undefined, row: unknown): RowRelationLink[] {
  if (!entityType) return [];
  const resolver = REGISTRY[entityType];
  if (!resolver) return [];
  try {
    return resolver(row);
  } catch {
    return [];
  }
}

export function relationsForMasterDataRow(
  entityType: string | undefined,
  row: MasterDataRecord,
  rowKey: (row: MasterDataRecord) => string,
): RowRelationLink[] {
  const base = getTableRelations(entityType, row);
  if (base.length > 0) return base;
  if (!entityType) return [];
  const tabId = entityTypeToTabId(entityType);
  if (!tabId) return [];
  const key = rowKey(row);
  return [
    {
      id: 'md-self',
      label: '主数据 · 本表',
      to: '/master-data',
      masterDataFocus: focus('master-data', tabId, key, key),
    },
  ];
}

function entityTypeToTabId(entityType: string): string | null {
  const map: Record<string, string> = {
    Material: 'materials',
    BomComponent: 'boms',
    ProductResource: 'product-resources',
    ProductionResource: 'resources',
    ProductionLine: 'lines',
    ResourceCalendar: 'calendar',
    SalesOrderLine: 'sales-orders',
    Inventory: 'inventory',
  };
  return map[entityType] ?? null;
}
