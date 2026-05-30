import { useCallback, useEffect, useState } from 'react';
import { ViewMode } from 'gantt-task-react';
import { api } from '../api/client';
import { FulfillmentChainSyncView } from '../components/FulfillmentChainSyncView';
import { FulfillmentGanttToolbar } from '../components/FulfillmentGanttToolbar';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import type { DemandPoolEntry, DemandPoolKpi, OrderFulfillmentChain } from '../types/api';
import { FULFILLMENT_STATUS_LABEL, fulfillmentChainToGanttTasks } from '../utils/fulfillmentGantt';
import './DemandPage.css';

function orderKey(o: DemandPoolEntry) {
  return `${o.salesOrderNo}-${o.salesOrderLineNo}`;
}

function statusClass(status: string) {
  if (status === 'SHORTAGE' || status === 'AT_RISK') return 'badge danger';
  if (status === 'PENDING') return 'badge muted';
  if (status === 'ON_TRACK' || status === 'KITTING_OK' || status === 'OK') return 'badge ok';
  return 'badge info';
}

export function DemandPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [rows, setRows] = useState<DemandPoolEntry[]>([]);
  const [kpis, setKpis] = useState<DemandPoolKpi[]>([]);
  const [selected, setSelected] = useState<DemandPoolEntry | null>(null);
  const [chain, setChain] = useState<OrderFulfillmentChain | null>(null);
  const [loading, setLoading] = useState(false);
  const [chainLoading, setChainLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ganttTasks, setGanttTasks] = useState<import('gantt-task-react').Task[]>([]);
  const [showArrows, setShowArrows] = useState(true);
  const [ganttViewMode, setGanttViewMode] = useState(ViewMode.Day);
  const [selectedChainNodeId, setSelectedChainNodeId] = useState<string | null>(null);

  const load = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      const [pool, summary] = await Promise.all([
        api.demandPool(versionId ?? undefined),
        api.demandPoolSummary(versionId ?? undefined),
      ]);
      setRows(pool);
      setKpis(summary.kpis);
      if (pool.length > 0) {
        setSelected((prev) => {
          if (prev) {
            const match = pool.find((r) => orderKey(r) === orderKey(prev));
            return match ?? pool[0];
          }
          return pool[0];
        });
      } else {
        setSelected(null);
        setChain(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadChain = useCallback(
    async (order: DemandPoolEntry, versionId: string | null | undefined) => {
      setChainLoading(true);
      try {
        setChain(
          await api.fulfillmentChain(
            order.salesOrderNo,
            order.salesOrderLineNo,
            versionId ?? undefined,
          ),
        );
      } catch (e) {
        setChain(null);
        setError(e instanceof Error ? e.message : '满足链加载失败');
      } finally {
        setChainLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void load(activePlanVersionId);
  }, [activePlanVersionId, load]);

  useEffect(() => {
    if (selected) {
      void loadChain(selected, activePlanVersionId);
    }
  }, [selected, activePlanVersionId, loadChain]);

  useEffect(() => {
    setGanttTasks(
      fulfillmentChainToGanttTasks(chain?.nodes ?? [], chain?.edges ?? []),
    );
    const so = chain?.nodes.find((n) => n.nodeType === 'SALES_ORDER');
    setSelectedChainNodeId(so?.nodeId ?? null);
  }, [chain]);

  const selectOrder = (row: DemandPoolEntry) => {
    setSelected(row);
  };

  const currentScenario = scenarios.find((s) => s.scenarioId === selectedScenarioId);
  const scenarioHint = currentScenario
    ? `场景「${currentScenario.name}」${activePlanVersionId ? ` · ${activePlanVersionId}` : ''}`
    : '请先在顶部选择计划场景';

  return (
    <div className={`demand-page ${embedded ? 'demand-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          title="需求满足"
          showScenarioSelector
          description={`订单由库存或工单满足；工单由上游工单或库存满足（追溯链）。${scenarioHint}`}
          actions={
            <button
              type="button"
              className="btn primary"
              onClick={() => void load(activePlanVersionId)}
              disabled={loading}
            >
              刷新
            </button>
          }
        />
      )}
      {!embedded && <StatusBanner loading={loading} error={error} />}

      <div className="demand-s01-layout">
        <aside className="demand-kpi-panel card">
          <h3 className="panel-title">关键 KPI</h3>
          <div className="panel-scroll kpi-scroll">
            <ul className="kpi-list">
              {kpis.map((k) => (
                <li key={k.metricId} className={`kpi-item severity-${k.severity}`}>
                  <span className="kpi-item-label">{k.label}</span>
                  <span className="kpi-item-value">
                    {k.value.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                    <small>{k.unit}</small>
                  </span>
                </li>
              ))}
            </ul>
            {kpis.length === 0 && <p className="empty">加载中…</p>}
          </div>
        </aside>

        <VerticalResizeSplit
          className="demand-right-split"
          storageKey="demand-s01-orders-ratio"
          top={
            <section className="demand-orders-panel card">
              <h3 className="panel-title">销售订单列表</h3>
              <div className="panel-scroll order-table-wrap">
                <FilterableTable
                  tableId="demand-orders"
                  tableClassName="order-select-table"
                  wrapClassName="ft-table-wrap"
                  rows={rows}
                  rowKey={(r) => orderKey(r)}
                  emptyText="暂无订单"
                  onRowClick={selectOrder}
                  getRowClassName={(r) => (selected && orderKey(r) === orderKey(selected) ? 'selected' : '')}
                  columns={[
                    {
                      key: 'order',
                      header: '订单',
                      render: (r) => `${r.salesOrderNo}-${r.salesOrderLineNo}`,
                    },
                    { key: 'product', header: '产品', render: (r) => r.productCode },
                    { key: 'qty', header: '数量', render: (r) => r.orderQty },
                    { key: 'dueDate', header: '交期', render: (r) => r.dueDate },
                    { key: 'priority', header: '优先级', render: (r) => r.priority },
                    {
                      key: 'kitting',
                      header: '齐套',
                      render: (r) => (
                        <span className={statusClass(r.kittingStatus)}>
                          {FULFILLMENT_STATUS_LABEL[r.kittingStatus] ?? r.kittingStatus}
                        </span>
                      ),
                    },
                    {
                      key: 'fulfillment',
                      header: '满足',
                      render: (r) => (
                        <span className={statusClass(r.fulfillmentStatus)}>
                          {FULFILLMENT_STATUS_LABEL[r.fulfillmentStatus] ?? r.fulfillmentStatus}
                        </span>
                      ),
                    },
                  ]}
                />
              </div>
            </section>
          }
          bottom={
            <section className="demand-chain-panel card">
              {selected ? (
                <>
                  <div className="chain-header">
                    <div>
                      <h3 className="panel-title">
                        满足链 · {selected.salesOrderNo}-{selected.salesOrderLineNo}
                      </h3>
                      <p className="chain-meta">
                        产品 {selected.productCode} · 交期 {selected.dueDate}
                        {chain && (
                          <>
                            {' '}
                            · 总体{' '}
                            <span className={statusClass(chain.overallStatus)}>
                              {FULFILLMENT_STATUS_LABEL[chain.overallStatus] ?? chain.overallStatus}
                            </span>
                          </>
                        )}
                      </p>
                    </div>
                    <div className="chain-header-actions">
                      {chain && chain.nodes.length > 0 && (
                        <FulfillmentGanttToolbar
                          showArrows={showArrows}
                          onShowArrowsChange={setShowArrows}
                          viewMode={ganttViewMode}
                          onViewModeChange={setGanttViewMode}
                          compact
                        />
                      )}
                      {chainLoading && <span className="chain-loading">更新中…</span>}
                    </div>
                  </div>
                  <div className="chain-sync-host">
                    <FulfillmentChainSyncView
                      nodes={chain?.nodes ?? []}
                      edges={chain?.edges ?? []}
                      tasks={ganttTasks}
                      onTasksChange={setGanttTasks}
                      selectedNodeId={selectedChainNodeId}
                      onSelectNode={setSelectedChainNodeId}
                      showArrows={showArrows}
                      viewMode={ganttViewMode}
                    />
                    {chain && chain.nodes.length > 0 && (
                      <div className="chain-legend">
                        <span className="legend-item"><i className="dot ok" /> 库存满足</span>
                        <span className="legend-item"><i className="dot planned" /> 工单满足</span>
                        <span className="legend-item"><i className="dot risk" /> 缺料</span>
                        <span className="legend-item"><i className="dot pending" /> 销售订单需求</span>
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <div className="panel-scroll chain-scroll chain-empty">
                  <p className="empty">请选择上方订单查看满足链</p>
                </div>
              )}
            </section>
          }
        />
      </div>
    </div>
  );
}
