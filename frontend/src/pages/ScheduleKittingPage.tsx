import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import type { WorkOrderKitting, WorkOrderKittingLine } from '../types/api';
import './ScheduleKittingPage.css';

function kittingClass(status: string) {
  if (status === 'SHORTAGE') return 'badge danger';
  if (status === 'KITTING_OK') return 'badge ok';
  return 'badge muted';
}

const lineColumns = [
  {
    key: 'component',
    header: '组件料号',
    className: 'mono',
    render: (line: WorkOrderKittingLine) => line.componentProductCode,
  },
  { key: 'required', header: '需求量', render: (line: WorkOrderKittingLine) => line.requiredQty },
  { key: 'available', header: '可用量', render: (line: WorkOrderKittingLine) => line.availableQty },
  { key: 'shortage', header: '缺料', render: (line: WorkOrderKittingLine) => (line.shortage ? '是' : '否') },
];

export function ScheduleKittingPage() {
  const [rows, setRows] = useState<WorkOrderKitting[]>([]);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.workOrders.dispatchedKitting());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const compute = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const data = await api.workOrders.computeDispatchedKitting();
      setRows(data);
      const shortage = data.filter((r) => r.kittingStatus === 'SHORTAGE').length;
      setSuccess(
        shortage > 0
          ? `齐套检查完成：${data.length} 张已下发工单，${shortage} 张缺料`
          : `齐套检查完成：${data.length} 张已下发工单全部齐套`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '齐套检查失败');
    } finally {
      setLoading(false);
    }
  };

  const shortageCount = rows.filter((r) => r.kittingStatus === 'SHORTAGE').length;

  const columns = useMemo(
    () => [
      {
        key: 'expand',
        header: '',
        filterable: false,
        resizable: false,
        className: 'sk-expand',
        render: (row: WorkOrderKitting) => (expanded === row.workOrderNo ? '▼' : '▶'),
      },
      { key: 'workOrderNo', header: '工单号', className: 'mono', render: (row: WorkOrderKitting) => row.workOrderNo },
      {
        key: 'productCode',
        header: '产品',
        className: 'mono',
        render: (row: WorkOrderKitting) => row.productCode,
      },
      { key: 'quantity', header: '数量', render: (row: WorkOrderKitting) => row.quantity },
      {
        key: 'kittingStatus',
        header: '齐套状态',
        render: (row: WorkOrderKitting) => (
          <span className={kittingClass(row.kittingStatus)}>{row.kittingStatus}</span>
        ),
      },
      {
        key: 'shortageReason',
        header: '缺料说明',
        className: 'sk-reason',
        render: (row: WorkOrderKitting) => (
          <>
            <span>{row.shortageReason ?? '—'}</span>
            {expanded === row.workOrderNo && row.lines.length > 0 && (
              <FilterableTable
                tableId={`sk-lines-${row.workOrderNo}`}
                tableClassName="sk-lines"
                wrapClassName="sk-lines-wrap ft-table-wrap"
                rows={row.lines}
                rowKey={(line) => line.componentProductCode}
                getRowClassName={(line) => (line.shortage ? 'shortage' : '')}
                columns={lineColumns}
              />
            )}
          </>
        ),
      },
    ],
    [expanded],
  );

  return (
    <div className="schedule-kitting-page">
      <PageHeader
        title="物料齐套"
        description="对已下发排程的工单，按 BOM 关键件检查物料齐套情况"
        actions={
          <>
            <button type="button" className="btn" onClick={() => void load()} disabled={loading}>
              刷新
            </button>
            <button type="button" className="btn primary" onClick={() => void compute()} disabled={loading}>
              齐套检查
            </button>
          </>
        }
      />
      <StatusBanner loading={loading} error={error} success={success} />

      <section className="card sk-summary">
        <span>已下发工单：{rows.length}</span>
        <span className={shortageCount > 0 ? 'sk-warn' : ''}>缺料：{shortageCount}</span>
        <span className="sk-hint">
          请先在 <Link to="/master-plan/work-orders">生产工单</Link> 下发工单；无已下发工单时列表为空。
        </span>
      </section>

      <section className="card">
        <FilterableTable
          tableId="schedule-kitting"
          wrapClassName="ft-table-wrap"
          rows={rows}
          rowKey={(row) => row.workOrderNo}
          emptyText="暂无已下发工单"
          onRowClick={(row) =>
            setExpanded((prev) => (prev === row.workOrderNo ? null : row.workOrderNo))
          }
          getRowClassName={(row) => (expanded === row.workOrderNo ? 'expanded' : '')}
          columns={columns}
        />
      </section>
    </div>
  );
}
