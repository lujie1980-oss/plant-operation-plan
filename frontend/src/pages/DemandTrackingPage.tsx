import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { OrderBusinessFlowGraph } from '../components/OrderBusinessFlowGraph';
import { OrderProcessPathGraph } from '../components/OrderProcessPathGraph';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import type { DemandTrackingEntry } from '../types/api';
import './DemandTrackingPage.css';

const EXEC_LABEL: Record<string, string> = {
  PENDING: '待计划',
  PLANNED: '已计划',
  DISPATCHED: '已下发',
  IN_PRODUCTION: '生产中',
  AT_RISK: '有风险',
};

function orderKey(row: DemandTrackingEntry) {
  return `${row.salesOrderNo}-${row.salesOrderLineNo}`;
}

function statusBadge(status: string) {
  if (status === 'AT_RISK' || status === 'SHORTAGE') return 'badge danger';
  if (status === 'ON_TRACK' || status === 'KITTING_OK' || status === 'IN_PRODUCTION') return 'badge ok';
  if (status === 'DISPATCHED' || status === 'PLANNED') return 'badge info';
  return 'badge muted';
}

export function DemandTrackingPage() {
  const [rows, setRows] = useState<DemandTrackingEntry[]>([]);
  const [selected, setSelected] = useState<DemandTrackingEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.demandTracking();
      setRows(data);
      setSelected((prev) => {
        if (prev) {
          const match = data.find((r) => orderKey(r) === orderKey(prev));
          if (match) return match;
        }
        return data[0] ?? null;
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="demand-tracking-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="需求跟踪"
        description="业务流程节点与工艺路径进度：原料 → 工序 → 订单"
        actions={
          <button type="button" className="btn" onClick={() => void load()} disabled={loading}>
            刷新
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} />

      <VerticalResizeSplit
        className="dt-split"
        storageKey="demand-tracking-split-ratio"
        minTopRatio={0.28}
        maxTopRatio={0.7}
        top={
          <section className="card dt-table-panel">
            <FilterableTable
              tableId="demand-tracking"
              tableClassName="dt-table"
              wrapClassName="dt-table-wrap ft-table-wrap"
                rows={rows}
                rowKey={(row) => orderKey(row)}
                emptyText="暂无需求数据"
                onRowClick={setSelected}
                getRowClassName={(row) =>
                  selected && orderKey(selected) === orderKey(row) ? 'selected' : ''
                }
                columns={[
                  {
                    key: 'order',
                    header: '销售订单',
                    className: 'mono',
                    render: (row) => `${row.salesOrderNo}-${row.salesOrderLineNo}`,
                  },
                  { key: 'product', header: '产品', className: 'mono', render: (row) => row.productCode },
                  { key: 'dueDate', header: '交期', render: (row) => row.dueDate },
                  {
                    key: 'flow',
                    header: '业务流程',
                    className: 'dt-flow-cell',
                    render: (row) => <OrderBusinessFlowGraph steps={row.flowSteps ?? []} compact />,
                  },
                  {
                    key: 'executionStatus',
                    header: '执行状态',
                    render: (row) => (
                      <span className={statusBadge(row.executionStatus)}>
                        {EXEC_LABEL[row.executionStatus] ?? row.executionStatus}
                      </span>
                    ),
                  },
                  {
                    key: 'progress',
                    header: '进度',
                    render: (row) => (
                      <div className="dt-progress">
                        <div className="dt-progress-bar" style={{ width: `${row.progressPct}%` }} />
                        <span>{Math.round(row.progressPct)}%</span>
                      </div>
                    ),
                  },
                ]}
              />
          </section>
        }
        bottom={
          <section className="card dt-detail-panel">
            {selected ? (
              <>
                <div className="dt-detail-head">
                  <div>
                    <h3 className="panel-title">
                      工艺路径 · {selected.salesOrderNo}-{selected.salesOrderLineNo}
                    </h3>
                    <p className="dt-detail-sub">
                      产品 {selected.productCode} ×{selected.orderQty} · 交期 {selected.dueDate}
                    </p>
                  </div>
                </div>
                <div className="dt-detail-section">
                  <h4>业务流程</h4>
                  <OrderBusinessFlowGraph steps={selected.flowSteps ?? []} />
                </div>
                <div className="dt-detail-section dt-process-section">
                  <h4>工艺路径进度</h4>
                  <OrderProcessPathGraph
                    nodes={selected.processNodes ?? []}
                    edges={selected.processEdges ?? []}
                  />
                </div>
              </>
            ) : (
              <p className="empty dt-detail-empty">请在上方选择一条订单查看进度图</p>
            )}
          </section>
        }
      />
    </div>
  );
}
