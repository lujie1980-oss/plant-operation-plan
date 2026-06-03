import { useMemo, type ReactNode } from 'react';
import { FilterableTable } from './table/FilterableTable';
import type { WorkOrder } from '../types/api';

function formatTs(ts: string | null | undefined) {
  if (!ts) return '—';
  return ts.replace('T', ' ').slice(0, 16);
}

function formatSlotDate(date: string | null | undefined, shiftId: string | null | undefined) {
  if (!date) return '—';
  return shiftId ? `${date} · ${shiftId}` : date;
}

export interface PendingWorkOrderTableProps {
  rows: WorkOrder[];
  activeWorkOrderNo: string | null;
  loading?: boolean;
  tableId: string;
  onSelect: (row: WorkOrder) => void;
  onPendingScheduleEligibleChange: (workOrderNo: string, eligible: boolean) => void;
  extraColumns?: Array<{
    key: string;
    header: string;
    render: (row: WorkOrder) => ReactNode;
  }>;
}

export function PendingWorkOrderTable({
  rows,
  activeWorkOrderNo,
  loading = false,
  tableId,
  onSelect,
  onPendingScheduleEligibleChange,
  extraColumns = [],
}: PendingWorkOrderTableProps) {
  const columns = useMemo(
    () => [
      {
        key: 'scheduleStatus',
        header: '排产',
        filterable: false,
        resizable: false,
        className: 'pwo-icon-col',
        render: (row: WorkOrder) => (
          <span
            className={`pwo-sched-icon ${row.detailScheduled ? 'done' : 'open'}`}
            title={
              row.detailScheduled
                ? `已排产（${row.detailScheduledOperationCount ?? 0}/${row.routingOperationCount ?? 0} 工序）`
                : `未排产（${row.detailScheduledOperationCount ?? 0}/${row.routingOperationCount ?? 0} 工序）`
            }
            aria-label={row.detailScheduled ? '已排产' : '未排产'}
          >
            {row.detailScheduled ? '●' : '○'}
          </span>
        ),
      },
      {
        key: 'pendingEligible',
        header: '待排状态',
        filterable: false,
        render: (row: WorkOrder) => {
          const schedulable = row.pendingScheduleEligible !== false;
          return (
            <select
              className={`pwo-eligible-select ${schedulable ? '' : 'blocked'}`}
              value={schedulable ? 'SCHEDULABLE' : 'NOT_SCHEDULABLE'}
              title={schedulable ? '可进入生产排程' : '已拆批时同步更新全部批次；不可排产'}
              onClick={(e) => e.stopPropagation()}
              onChange={(e) =>
                onPendingScheduleEligibleChange(row.workOrderNo, e.target.value === 'SCHEDULABLE')
              }
            >
              <option value="SCHEDULABLE">可排产</option>
              <option value="NOT_SCHEDULABLE">不可排产</option>
            </select>
          );
        },
      },
      {
        key: 'workOrderNo',
        header: '工单号',
        className: 'mono',
        render: (row: WorkOrder) => row.workOrderNo,
      },
      {
        key: 'productCode',
        header: '产品',
        className: 'mono',
        render: (row: WorkOrder) => row.productCode,
      },
      { key: 'quantity', header: '数量', render: (row: WorkOrder) => row.quantity },
      {
        key: 'salesOrder',
        header: '销售订单',
        render: (row: WorkOrder) =>
          row.salesOrderNo ? `${row.salesOrderNo} / ${row.salesOrderLineNo}` : '—',
      },
      {
        key: 'plannedSlot',
        header: '主计划槽位',
        render: (row: WorkOrder) => formatSlotDate(row.plannedSlotDate, row.plannedShiftId),
      },
      {
        key: 'resourceId',
        header: '主计划资源',
        className: 'mono',
        render: (row: WorkOrder) => row.resourceId || '—',
      },
      {
        key: 'dispatchedTs',
        header: '下发时间',
        render: (row: WorkOrder) => formatTs(row.dispatchedTs),
      },
      ...extraColumns,
    ],
    [extraColumns, onPendingScheduleEligibleChange],
  );

  return (
    <FilterableTable
      tableId={tableId}
      tableClassName="pp-table data-table"
      wrapClassName="ft-table-wrap"
      rows={rows}
      rowKey={(row) => row.workOrderNo}
      loading={loading}
      columns={columns}
      getRowClassName={(row) => (row.workOrderNo === activeWorkOrderNo ? 'active' : '')}
      onRowClick={onSelect}
    />
  );
}
