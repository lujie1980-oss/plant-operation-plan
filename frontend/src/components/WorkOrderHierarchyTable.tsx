import { useEffect, useMemo, useState } from 'react';
import type { WorkOrder } from '../types/api';
import { fmtShortTs } from '../utils/formatTiming';
import { isFinishedGoodsSource, WORK_ORDER_SOURCE_LABEL } from '../utils/workOrderSourceLabels';
import {
  buildWorkOrderForest,
  defaultCollapsedRootIds,
  defaultExpandedRootIds,
  subtreeVisible,
  workOrderMatchesTextFilter,
  type WorkOrderTreeNode,
} from '../utils/workOrderTree';
import { TableHead } from './table/TableHead';
import type { TableHeadColumn } from './table/types';
import { useTableLayout } from './table/useTableLayout';
import './WorkOrderHierarchyTable.css';
import './table/FilterableTable.css';

const DISPATCH_LABEL: Record<string, string> = {
  PENDING: '待下发',
  DISPATCHED: '已下发',
};

const TIMING_COLUMNS: TableHeadColumn[] = [
  { key: 'latestStart', header: '最晚开始', width: 108, defaultWidth: 108, filterable: false },
  { key: 'latestEnd', header: '最晚结束', width: 108, defaultWidth: 108, filterable: false },
  { key: 'latestDelivery', header: '最晚交付', width: 108, defaultWidth: 108, filterable: false },
  { key: 'feasibleStart', header: '可行开始', width: 108, defaultWidth: 108, filterable: false },
  { key: 'feasibleEnd', header: '可行结束', width: 108, defaultWidth: 108, filterable: false },
  { key: 'feasibleDelivery', header: '可行交付', width: 108, defaultWidth: 108, filterable: false },
  { key: 'ownStart', header: '自身开始', width: 108, defaultWidth: 108, filterable: false },
  { key: 'ownEnd', header: '自身结束', width: 108, defaultWidth: 108, filterable: false },
  { key: 'ownDelivery', header: '自身交付', width: 108, defaultWidth: 108, filterable: false },
  {
    key: 'productionMin',
    header: '生产(分)',
    width: 72,
    defaultWidth: 72,
    filterable: false,
    align: 'right',
  },
  {
    key: 'postProcessingMin',
    header: '后处理(分)',
    width: 80,
    defaultWidth: 80,
    filterable: false,
    align: 'right',
  },
];

const BASE_COLUMNS: TableHeadColumn[] = [
  { key: 'check', header: '', width: 40, defaultWidth: 40, filterable: false },
  { key: 'workOrderNo', header: '工单号', width: 200, defaultWidth: 200 },
  { key: 'source', header: '来源', width: 88, defaultWidth: 88, filterable: false },
  { key: 'productCode', header: '产品', width: 120, defaultWidth: 120 },
  { key: 'quantity', header: '数量', width: 72, defaultWidth: 72, align: 'right' },
  { key: 'resourceId', header: '资源', width: 88, defaultWidth: 88 },
  { key: 'plannedSlot', header: '计划槽位', width: 120, defaultWidth: 120, filterable: false },
  { key: 'scheduleFeedback', header: '排程反馈', width: 88, defaultWidth: 88, filterable: false },
  { key: 'salesOrder', header: '销售订单', width: 120, defaultWidth: 120 },
  { key: 'dispatchStatus', header: '下发状态', width: 88, defaultWidth: 88 },
];

const TOGGLE_COLUMN: TableHeadColumn = {
  key: 'toggle',
  header: '',
  width: 28,
  defaultWidth: 28,
  filterable: false,
};

function columnsForLayout(useTree: boolean): TableHeadColumn[] {
  const head = useTree ? [TOGGLE_COLUMN, ...BASE_COLUMNS] : BASE_COLUMNS;
  return [...head, ...TIMING_COLUMNS];
}

function WorkOrderTimingCells({ row }: { row: WorkOrder }) {
  const tw = row.timingWindow;
  return (
    <>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.latestDesiredStart)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.latestDesiredEnd)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.latestDesiredDelivery)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleStart)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleEnd)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleDelivery)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleStartOwn)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleEndOwn)}</td>
      <td className="wo-tree-td mono wo-tree-td-timing">{fmtShortTs(tw?.earliestPossibleDeliveryOwn)}</td>
      <td className="wo-tree-td wo-tree-td-num">
        {tw?.productionDurationMinutes != null ? tw.productionDurationMinutes : '—'}
      </td>
      <td className="wo-tree-td wo-tree-td-num">
        {tw?.postProcessingMinutes != null ? tw.postProcessingMinutes : '—'}
      </td>
    </>
  );
}

type Props = {
  rows: WorkOrder[];
  activePlanVersionId: string | null | undefined;
  selected: Set<string>;
  activeWorkOrderNo: string | null;
  onToggleSelect: (workOrderNo: string) => void;
  onRowClick: (row: WorkOrder) => void;
  emptyText?: string;
  /** flat：平铺全部工单；tree：按 BOM 父子树展示 */
  layout?: 'flat' | 'tree';
  /** orderLine：按 pegging+BOM 依赖拼订单链；mrp：全场景合并树 */
  treeMode?: 'mrp' | 'orderLine';
  getParentWorkOrderNo?: (wo: WorkOrder) => string | null | undefined;
  showPeggedQty?: boolean;
};

function WorkOrderTreeRow({
  node,
  depth,
  collapsed,
  filters,
  activePlanVersionId,
  selected,
  activeWorkOrderNo,
  onToggleCollapse,
  onToggleSelect,
  onRowClick,
  showPeggedQty = false,
  showToggle = true,
}: {
  node: WorkOrderTreeNode;
  depth: number;
  collapsed: Set<string>;
  filters: Record<string, string>;
  activePlanVersionId: string | null | undefined;
  selected: Set<string>;
  activeWorkOrderNo: string | null;
  onToggleCollapse: (workOrderNo: string) => void;
  onToggleSelect: (workOrderNo: string) => void;
  onRowClick: (row: WorkOrder) => void;
  showPeggedQty?: boolean;
  showToggle?: boolean;
}) {
  if (!subtreeVisible(node, filters)) {
    return null;
  }

  const row = node.workOrder;
  const hasChildren = node.children.length > 0;
  const isCollapsed = collapsed.has(row.workOrderNo);
  const isActive = activeWorkOrderNo === row.workOrderNo;
  const isChecked = selected.has(row.workOrderNo);
  const rowClass = [isChecked ? 'checked' : '', isActive ? 'active' : ''].filter(Boolean).join(' ');

  return (
    <>
      <tr className={rowClass} onClick={() => onRowClick(row)}>
        {showToggle && (
          <td className="wo-tree-td wo-tree-td-toggle">
            {hasChildren ? (
              <button
                type="button"
                className="wo-tree-toggle"
                aria-expanded={!isCollapsed}
                aria-label={isCollapsed ? '展开子工单' : '折叠子工单'}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleCollapse(row.workOrderNo);
                }}
              >
                {isCollapsed ? '▸' : '▾'}
              </button>
            ) : (
              <span className="wo-tree-toggle spacer" aria-hidden />
            )}
          </td>
        )}
        <td className="wo-tree-td wo-tree-td-check" onClick={(e) => e.stopPropagation()}>
          <input
            type="checkbox"
            checked={isChecked}
            disabled={row.dispatchStatus === 'DISPATCHED'}
            onChange={() => onToggleSelect(row.workOrderNo)}
            aria-label={`选择 ${row.workOrderNo}`}
          />
        </td>
        <td className="wo-tree-td mono">
          <span className="wo-tree-label" style={{ paddingLeft: `${depth * 14}px` }}>
            {row.workOrderNo}
          </span>
        </td>
        <td className="wo-tree-td">
          <span
            className={`tag ${isFinishedGoodsSource(row.workOrderSource) ? 'tag-external' : 'tag-replenish'}`}
          >
            {WORK_ORDER_SOURCE_LABEL[row.workOrderSource] ?? row.workOrderSource}
          </span>
        </td>
        <td className="wo-tree-td mono">{row.productCode}</td>
        <td className="wo-tree-td wo-tree-td-num">
          {showPeggedQty && row.orderLinePeggedQty != null ? (
            <span title={`工单总量 ${row.quantity}`}>{row.orderLinePeggedQty}</span>
          ) : (
            row.quantity
          )}
        </td>
        <td className="wo-tree-td">{row.resourceId}</td>
        <td className="wo-tree-td mono">
          {row.plannedSlotDate
            ? `${row.plannedSlotDate}${row.plannedShiftId ? ` · ${row.plannedShiftId}` : ''}`
            : row.inScenarioPlan === false && activePlanVersionId
              ? '未排入'
              : '—'}
        </td>
        <td className="wo-tree-td">
          {row.hasScheduleFeedback ? (
            <span
              className={`tag ${row.hasFrozenScheduleFeedback ? 'tag-feedback-frozen' : 'tag-feedback'}`}
              title={
                row.scheduleFeedbackOperationCount
                  ? `${row.scheduleFeedbackOperationCount} 道工序已反馈`
                  : undefined
              }
            >
              {row.hasFrozenScheduleFeedback ? '已冻结' : '已反馈'}
            </span>
          ) : (
            <span className="muted-text">—</span>
          )}
        </td>
        <td className="wo-tree-td mono">
          {row.peggingCount && row.peggingCount > 0 && !row.salesOrderNo ? (
            <span title={`${row.peggingCount} 条订单 pegging`}>合并 ×{row.peggingCount}</span>
          ) : row.salesOrderNo ? (
            `${row.salesOrderNo}-${row.salesOrderLineNo}`
          ) : (
            '—'
          )}
        </td>
        <td className="wo-tree-td">
          <span className={row.dispatchStatus === 'DISPATCHED' ? 'badge ok' : 'badge muted'}>
            {DISPATCH_LABEL[row.dispatchStatus] ?? row.dispatchStatus}
          </span>
        </td>
        <WorkOrderTimingCells row={row} />
      </tr>
      {hasChildren &&
        !isCollapsed &&
        node.children.map((child) => (
          <WorkOrderTreeRow
            key={child.workOrder.workOrderNo}
            node={child}
            depth={depth + 1}
            collapsed={collapsed}
            filters={filters}
            activePlanVersionId={activePlanVersionId}
            selected={selected}
            activeWorkOrderNo={activeWorkOrderNo}
            onToggleCollapse={onToggleCollapse}
            onToggleSelect={onToggleSelect}
            onRowClick={onRowClick}
            showPeggedQty={showPeggedQty}
            showToggle={showToggle}
          />
        ))}
    </>
  );
}

export function WorkOrderHierarchyTable({
  rows,
  activePlanVersionId,
  selected,
  activeWorkOrderNo,
  onToggleSelect,
  onRowClick,
  emptyText = '暂无工单',
  layout = 'flat',
  treeMode = 'mrp',
  getParentWorkOrderNo,
  showPeggedQty = false,
}: Props) {
  const parentOf =
    treeMode === 'orderLine' && getParentWorkOrderNo
      ? getParentWorkOrderNo
      : undefined;
  const forest = buildWorkOrderForest(rows, parentOf);
  const useTree = layout === 'tree' || treeMode === 'orderLine';
  const headColumns = useMemo(() => columnsForLayout(useTree), [useTree]);
  const [collapsed, setCollapsed] = useState<Set<string>>(() =>
    !useTree || treeMode === 'orderLine'
      ? defaultExpandedRootIds(forest)
      : defaultCollapsedRootIds(forest),
  );
  const { filters, setFilter, getColumnWidth, onResizeStart } = useTableLayout(
    'production-plan-work-orders-v2',
    headColumns,
  );

  const flatRows = useMemo(() => {
    if (useTree) return [];
    return rows
      .filter((wo) => workOrderMatchesTextFilter(wo, filters))
      .slice()
      .sort(
        (a, b) =>
          (a.bomLevel ?? 0) - (b.bomLevel ?? 0) ||
          a.sequenceNo - b.sequenceNo ||
          a.workOrderNo.localeCompare(b.workOrderNo),
      );
  }, [rows, filters, useTree]);

  useEffect(() => {
    if (!useTree) return;
    const nextForest = buildWorkOrderForest(rows, parentOf);
    setCollapsed(
      treeMode === 'orderLine'
        ? defaultExpandedRootIds(nextForest)
        : defaultCollapsedRootIds(nextForest),
    );
  }, [rows, treeMode, parentOf, useTree]);

  const onToggleCollapse = (workOrderNo: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(workOrderNo)) next.delete(workOrderNo);
      else next.add(workOrderNo);
      return next;
    });
  };

  if (rows.length === 0) {
    return <p className="wo-tree-empty">{emptyText}</p>;
  }

  return (
    <div className="wo-tree-panel">
      <table className="wo-tree-table pp-table ft-table data-table" data-table-id="production-plan-work-orders-v2">
        <thead>
          <TableHead
            columns={headColumns}
            filters={filters}
            setFilter={setFilter}
            getColumnWidth={getColumnWidth}
            onResizeStart={onResizeStart}
          />
        </thead>
        <tbody>
          {useTree
            ? forest.map((root) => (
                <WorkOrderTreeRow
                  key={root.workOrder.workOrderNo}
                  node={root}
                  depth={0}
                  collapsed={collapsed}
                  filters={filters}
                  activePlanVersionId={activePlanVersionId}
                  selected={selected}
                  activeWorkOrderNo={activeWorkOrderNo}
                  onToggleCollapse={onToggleCollapse}
                  onToggleSelect={onToggleSelect}
                  onRowClick={onRowClick}
                  showPeggedQty={showPeggedQty}
                  showToggle
                />
              ))
            : flatRows.map((wo) => (
                <WorkOrderTreeRow
                  key={wo.workOrderNo}
                  node={{ workOrder: wo, children: [] }}
                  depth={0}
                  collapsed={collapsed}
                  filters={filters}
                  activePlanVersionId={activePlanVersionId}
                  selected={selected}
                  activeWorkOrderNo={activeWorkOrderNo}
                  onToggleCollapse={onToggleCollapse}
                  onToggleSelect={onToggleSelect}
                  onRowClick={onRowClick}
                  showPeggedQty={showPeggedQty}
                  showToggle={false}
                />
              ))}
        </tbody>
      </table>
    </div>
  );
}
