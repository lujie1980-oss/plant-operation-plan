import { useCallback, useEffect, useMemo, useState, type MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { ViewMode } from 'gantt-task-react';
import { api } from '../api/client';
import { DemandActionConfirmDialog } from '../components/DemandActionConfirmDialog';
import { DemandOrderContextMenu } from '../components/DemandOrderContextMenu';
import { FulfillmentChainSyncView } from '../components/FulfillmentChainSyncView';
import { FulfillmentGanttToolbar } from '../components/FulfillmentGanttToolbar';
import { FulfillmentMaterialPanel } from '../components/FulfillmentMaterialPanel';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import type { DemandPoolEntry, DemandPoolKpi, OrderFulfillmentChain } from '../types/api';
import type {
  DemandChainViewMode,
  OrderDemandActionId,
} from '../types/demandActions';
import type { OrderPlanningChain } from '../types/orderPlanningChain';
import { FULFILLMENT_STATUS_LABEL, fulfillmentChainToGanttTasks } from '../utils/fulfillmentGantt';
import {
  chainEdgesForGantt,
  orderPlanningChainToDisplayNodes,
  orderPlanningChainToGanttTasks,
} from '../utils/orderPlanningChainGantt';
import '../components/DemandOrderContextMenu.css';
import './DemandPage.css';

function orderKey(o: DemandPoolEntry) {
  return `${o.salesOrderNo}-${o.salesOrderLineNo}`;
}

function statusClass(status: string) {
  if (status === 'SHORTAGE' || status === 'AT_RISK' || status === 'BLOCKED') return 'badge danger';
  if (status === 'PENDING') return 'badge muted';
  if (status === 'ON_TRACK' || status === 'KITTING_OK' || status === 'OK') return 'badge ok';
  return 'badge info';
}

type ContextMenuState = {
  x: number;
  y: number;
  row: DemandPoolEntry;
};

type PendingActionState = {
  row: DemandPoolEntry;
  action: OrderDemandActionId;
};

export function DemandPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [rows, setRows] = useState<DemandPoolEntry[]>([]);
  const [kpis, setKpis] = useState<DemandPoolKpi[]>([]);
  const [selected, setSelected] = useState<DemandPoolEntry | null>(null);
  const [chain, setChain] = useState<OrderFulfillmentChain | null>(null);
  const [planningChain, setPlanningChain] = useState<OrderPlanningChain | null>(null);
  const [viewMode, setViewMode] = useState<DemandChainViewMode>('fulfillment');
  const [loading, setLoading] = useState(false);
  const [chainLoading, setChainLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingActionState | null>(null);
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
        setPlanningChain(null);
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
    if (selected && viewMode === 'fulfillment') {
      void loadChain(selected, activePlanVersionId);
    }
  }, [selected, activePlanVersionId, loadChain, viewMode]);

  const displayNodes = useMemo(() => {
    if (viewMode === 'planning' && planningChain) {
      return orderPlanningChainToDisplayNodes(planningChain.nodes);
    }
    return chain?.nodes ?? [];
  }, [viewMode, planningChain, chain]);

  const displayEdges = useMemo(() => {
    if (viewMode === 'planning' && planningChain) {
      return chainEdgesForGantt(planningChain.edges);
    }
    return chain?.edges ?? [];
  }, [viewMode, planningChain, chain]);

  useEffect(() => {
    if (viewMode === 'planning' && planningChain) {
      setGanttTasks(orderPlanningChainToGanttTasks(planningChain.nodes));
    } else {
      setGanttTasks(fulfillmentChainToGanttTasks(chain?.nodes ?? [], chain?.edges ?? []));
    }
    const so = displayNodes.find((n) => n.nodeType === 'SALES_ORDER');
    setSelectedChainNodeId(so?.nodeId ?? null);
  }, [viewMode, planningChain, chain, displayNodes]);

  const runAction = useCallback(
    async (row: DemandPoolEntry, action: OrderDemandActionId) => {
      setActionLoading(true);
      setActionError(false);
      setActionMessage(null);
      setError(null);
      try {
        const result = await api.demandOrderAction(
          row.salesOrderNo,
          row.salesOrderLineNo,
          action,
          { masterPlanVersionId: activePlanVersionId ?? undefined },
        );
        setActionMessage(result.message);
        if (result.fulfillmentChain) {
          setChain(result.fulfillmentChain);
          setViewMode('fulfillment');
          setPlanningChain(null);
        }
        if (result.planningChain) {
          setPlanningChain(result.planningChain);
          setViewMode('planning');
        }
        if (result.confirmedPromiseDate) {
          setRows((prev) =>
            prev.map((r) =>
              orderKey(r) === orderKey(row)
                ? { ...r, promiseDate: result.confirmedPromiseDate }
                : r,
            ),
          );
          setSelected((prev) =>
            prev && orderKey(prev) === orderKey(row)
              ? { ...prev, promiseDate: result.confirmedPromiseDate }
              : prev,
          );
        }
        if (action === 'CANCEL_PLAN' && result.fulfillmentChain) {
          const clearedPromise = result.fulfillmentChain.promiseDate ?? null;
          setRows((prev) =>
            prev.map((r) =>
              orderKey(r) === orderKey(row) ? { ...r, promiseDate: clearedPromise } : r,
            ),
          );
          setSelected((prev) =>
            prev && orderKey(prev) === orderKey(row)
              ? { ...prev, promiseDate: clearedPromise }
              : prev,
          );
        }
        if (action === 'BUILD_UPSTREAM_CHAIN') {
          await load(activePlanVersionId);
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : '操作失败';
        setActionError(true);
        setActionMessage(msg);
      } finally {
        setActionLoading(false);
      }
    },
    [activePlanVersionId, load],
  );

  const selectOrder = (row: DemandPoolEntry) => {
    setSelected(row);
    setPlanningChain(null);
    setViewMode('fulfillment');
  };

  const openContextMenu = (e: MouseEvent, row: DemandPoolEntry) => {
    e.preventDefault();
    setSelected(row);
    setContextMenu({ x: e.clientX, y: e.clientY, row });
  };

  const selectChainNode = (taskId: string) => {
    setSelectedChainNodeId(taskId);
  };

  const currentScenario = scenarios.find((s) => s.scenarioId === selectedScenarioId);
  const scenarioHint = currentScenario
    ? `场景「${currentScenario.name}」${activePlanVersionId ? ` · ${activePlanVersionId}` : ''}`
    : '请先在顶部选择计划场景';

  const overallStatus =
    viewMode === 'planning' ? planningChain?.overallStatus : chain?.overallStatus;

  const requestAction = (row: DemandPoolEntry, action: OrderDemandActionId) => {
    setPendingAction({ row, action });
  };

  const contextMenuItems = contextMenu
    ? [
        {
          id: 'build',
          label: '创建上游满足链',
          onSelect: () => requestAction(contextMenu.row, 'BUILD_UPSTREAM_CHAIN'),
        },
        {
          id: 'unconstrained',
          label: '无限能力计划',
          onSelect: () => requestAction(contextMenu.row, 'PLAN_UNCONSTRAINED'),
        },
        {
          id: 'finite',
          label: '有限能力计划',
          onSelect: () => requestAction(contextMenu.row, 'PLAN_FINITE'),
        },
        {
          id: 'confirm',
          label: '确认承诺交期',
          onSelect: () => requestAction(contextMenu.row, 'CONFIRM_PROMISE_DATE'),
        },
        {
          id: 'cancel',
          label: '取消计划',
          onSelect: () => requestAction(contextMenu.row, 'CANCEL_PLAN'),
        },
      ]
    : [];

  return (
    <div className={`demand-page ${embedded ? 'demand-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          title="需求满足"
          showScenarioSelector
          description={`右键订单可试算推演或确认承诺交期；${scenarioHint}`}
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
      {!embedded && <StatusBanner loading={loading || actionLoading} error={error} />}

      {actionMessage && (
        <div className={`demand-action-toast ${actionError ? 'is-error' : ''}`.trim()}>
          {actionMessage}
          {viewMode === 'planning' && !actionError && (
            <Link to="/master-plan/analysis/order-chain">深入分析</Link>
          )}
        </div>
      )}

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
              <p className="chain-meta" style={{ margin: '0 0 0.5rem' }}>
                右键订单行打开操作菜单
              </p>
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
                  getRowProps={(row) => ({
                    onContextMenu: (e) => openContextMenu(e, row),
                  })}
                  columns={[
                    {
                      key: 'order',
                      header: '订单',
                      render: (r) => `${r.salesOrderNo}-${r.salesOrderLineNo}`,
                    },
                    { key: 'product', header: '产品', render: (r) => r.productCode },
                    { key: 'qty', header: '数量', render: (r) => r.orderQty },
                    { key: 'dueDate', header: '交期', render: (r) => r.dueDate },
                    {
                      key: 'promiseDate',
                      header: '承诺交期',
                      render: (r) => r.promiseDate ?? '—',
                    },
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
                        {viewMode === 'planning' ? '计划推演' : '满足链'} · {selected.salesOrderNo}-
                        {selected.salesOrderLineNo}
                      </h3>
                      <p className="chain-meta">
                        产品 {selected.productCode} · 交期 {selected.dueDate}
                        {selected.promiseDate && <> · 承诺 {selected.promiseDate}</>}
                        {overallStatus && (
                          <>
                            {' '}
                            · 总体{' '}
                            <span className={statusClass(overallStatus)}>
                              {FULFILLMENT_STATUS_LABEL[overallStatus] ?? overallStatus}
                            </span>
                          </>
                        )}
                        {viewMode === 'planning' && planningChain?.summary.capacityStrategy && (
                          <> · {planningChain.summary.capacityStrategy}</>
                        )}
                      </p>
                    </div>
                    <div className="chain-header-actions">
                      <div className="demand-view-toggle" role="group" aria-label="链视图">
                        <button
                          type="button"
                          className={viewMode === 'fulfillment' ? 'is-active' : ''}
                          onClick={() => setViewMode('fulfillment')}
                        >
                          满足链
                        </button>
                        <button
                          type="button"
                          className={viewMode === 'planning' ? 'is-active' : ''}
                          disabled={!planningChain}
                          onClick={() => setViewMode('planning')}
                        >
                          计划推演
                        </button>
                      </div>
                      {displayNodes.length > 0 && (
                        <FulfillmentGanttToolbar
                          showArrows={showArrows}
                          onShowArrowsChange={setShowArrows}
                          viewMode={ganttViewMode}
                          onViewModeChange={setGanttViewMode}
                          compact
                        />
                      )}
                      {(chainLoading || actionLoading) && (
                        <span className="chain-loading">更新中…</span>
                      )}
                    </div>
                  </div>
                  <div className="chain-sync-host">
                    <HorizontalResizeSplit
                      className="demand-chain-material-split"
                      storageKey="demand-chain-material-ratio"
                      minLeftRatio={0.42}
                      maxLeftRatio={0.82}
                      left={
                        <div className="demand-chain-gantt-wrap">
                          <FulfillmentChainSyncView
                            nodes={displayNodes}
                            edges={displayEdges}
                            tasks={ganttTasks}
                            onTasksChange={setGanttTasks}
                            selectedNodeId={selectedChainNodeId}
                            onSelectNode={selectChainNode}
                            showArrows={showArrows}
                            viewMode={ganttViewMode}
                          />
                          {displayNodes.length > 0 && (
                            <div className="chain-legend">
                              <span className="legend-item">
                                <i className="dot pending" /> 销售订单
                              </span>
                              <span className="legend-item">
                                <i className="dot planned" /> 工单
                              </span>
                            </div>
                          )}
                        </div>
                      }
                      right={
                        <FulfillmentMaterialPanel
                          nodes={displayNodes}
                          edges={displayEdges}
                          selectedTaskId={selectedChainNodeId}
                        />
                      }
                    />
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

      {contextMenu && (
        <DemandOrderContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          items={contextMenuItems}
          onClose={() => setContextMenu(null)}
        />
      )}

      {pendingAction && (
        <DemandActionConfirmDialog
          row={pendingAction.row}
          action={pendingAction.action}
          masterPlanVersionId={activePlanVersionId}
          busy={actionLoading}
          onCancel={() => {
            if (!actionLoading) setPendingAction(null);
          }}
          onConfirm={() => {
            const { row, action } = pendingAction;
            void runAction(row, action).finally(() => setPendingAction(null));
          }}
        />
      )}
    </div>
  );
}
