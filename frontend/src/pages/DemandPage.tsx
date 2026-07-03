import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from 'react';
import { ViewMode } from 'gantt-task-react';
import { api } from '../api/client';
import { DemandActionConfirmDialog } from '../components/DemandActionConfirmDialog';
import { DemandOrderContextMenu } from '../components/DemandOrderContextMenu';
import { FulfillmentChainTreePanel } from '../components/FulfillmentChainTreePanel';
import { FulfillmentGanttToolbar } from '../components/FulfillmentGanttToolbar';
import { FulfillmentMaterialDrawer } from '../components/FulfillmentMaterialDrawer';
import { FulfillmentRootCausePanel } from '../components/FulfillmentRootCausePanel';
import '../components/FulfillmentRootCausePanel.css';
import { MasterPlanBusinessKpiPanel } from '../components/MasterPlanBusinessKpiPanel';
import '../components/MasterPlanBusinessKpiPanel.css';
import { SupplyOrderPlanUnitGantt } from '../components/SupplyOrderPlanUnitGantt';
import { FilterableTable } from '../components/table/FilterableTable';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import type { CustomerOrderLineDeliveryListItem, DemandPoolEntry, DemandPoolKpi, OrderFulfillmentChain } from '../types/api';
import type { OrderDemandActionId } from '../types/demandActions';
import { FULFILLMENT_STATUS_LABEL } from '../utils/fulfillmentGantt';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { chainSolverEngine, chainTrialRevision } from '../utils/fulfillmentChainMeta';
import { resolveSupplyOrderNodeForGantt } from '../utils/supplyOrderPlanUnitGantt';
import '../components/DemandOrderContextMenu.css';
import './DemandPage.css';

function deliveryKey(d: CustomerOrderLineDeliveryListItem) {
  return d.deliveryId;
}

function deliveryToActionRow(d: CustomerOrderLineDeliveryListItem): DemandPoolEntry {
  return {
    salesOrderNo: d.salesOrderNo,
    salesOrderLineNo: d.salesOrderLineNo,
    productCode: d.productCode,
    orderQty: d.deliveryQty,
    dueDate: d.latestDesiredDate ?? '',
    promiseDate: d.promiseDate,
    priority: d.priority,
    expediteLevel: 0,
    status: d.status,
    scheduleLockFlag: false,
    kittingStatus: d.kittingStatus,
    fulfillmentStatus: d.fulfillmentStatus,
  };
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
  row: CustomerOrderLineDeliveryListItem;
};

type PendingActionState = {
  row: CustomerOrderLineDeliveryListItem;
  action: OrderDemandActionId;
};

function chainOverallToListStatus(overall: string, hasSupplyOrder: boolean): string {
  if (!hasSupplyOrder) {
    return 'PENDING';
  }
  if (overall === 'AT_RISK' || overall === 'BLOCKED') {
    return 'AT_RISK';
  }
  if (overall === 'PLANNED') {
    return 'PLANNED';
  }
  return 'ON_TRACK';
}

function patchRowAfterCancel(
  row: CustomerOrderLineDeliveryListItem,
  chain: OrderFulfillmentChain,
): CustomerOrderLineDeliveryListItem {
  const hasSupplyOrder = chain.nodes.some((n) => n.nodeType === 'SUPPLY_ORDER');
  return {
    ...row,
    promiseDate: chain.promiseDate ?? null,
    fulfillmentStatus: chainOverallToListStatus(chain.overallStatus, hasSupplyOrder),
    kittingStatus: chain.kittingStatus ?? row.kittingStatus,
  };
}

export function DemandPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [rows, setRows] = useState<CustomerOrderLineDeliveryListItem[]>([]);
  const [kpis, setKpis] = useState<DemandPoolKpi[]>([]);
  const [selected, setSelected] = useState<CustomerOrderLineDeliveryListItem | null>(null);
  const [chain, setChain] = useState<OrderFulfillmentChain | null>(null);
  const [loading, setLoading] = useState(false);
  const [chainLoading, setChainLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingActionState | null>(null);
  const [ganttViewMode, setGanttViewMode] = useState(ViewMode.Day);
  const [selectedChainNodeId, setSelectedChainNodeId] = useState<string | null>(null);
  const [chainDeliveryId, setChainDeliveryId] = useState<string | null>(null);
  const selectedDeliveryIdRef = useRef<string | null>(null);
  const chainRequestSeqRef = useRef(0);

  useEffect(() => {
    selectedDeliveryIdRef.current = selected?.deliveryId ?? null;
  }, [selected?.deliveryId]);

  const load = useCallback(
    async (versionId: string | null | undefined, keepDeliveryId?: string | null) => {
      setLoading(true);
      setError(null);
      try {
        const [pool, summary] = await Promise.all([
          api.ontologyDeliveries(versionId ?? undefined),
          api.ontologyDeliverySummary(versionId ?? undefined),
        ]);
        setRows(pool);
        setKpis(summary.kpis);
        if (pool.length > 0) {
          const preferredId = keepDeliveryId ?? null;
          const match =
            (preferredId && pool.find((r) => r.deliveryId === preferredId)) ||
            pool[0];
          setSelected(match);
          return match;
        }
        setSelected(null);
        setChain(null);
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : '加载失败');
        return null;
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const loadChain = useCallback(
    async (deliveryId: string, versionId: string | null | undefined) => {
      const requestSeq = ++chainRequestSeqRef.current;
      setChainLoading(true);
      try {
        const result = await api.ontologyFulfillmentChain(
          deliveryId,
          versionId ?? undefined,
        );
        if (requestSeq !== chainRequestSeqRef.current) return;
        if (selectedDeliveryIdRef.current !== deliveryId) return;
        setChain(result);
        setChainDeliveryId(deliveryId);
      } catch (e) {
        if (requestSeq !== chainRequestSeqRef.current) return;
        if (selectedDeliveryIdRef.current !== deliveryId) return;
        setChain(null);
        setChainDeliveryId(deliveryId);
        setError(e instanceof Error ? e.message : '满足链加载失败');
      } finally {
        if (requestSeq === chainRequestSeqRef.current) {
          setChainLoading(false);
        }
      }
    },
    [],
  );

  useEffect(() => {
    void load(activePlanVersionId, selectedDeliveryIdRef.current);
  }, [activePlanVersionId, load]);

  const selectedDeliveryId = selected?.deliveryId ?? null;
  const prevSelectedDeliveryIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!selectedDeliveryId) {
      prevSelectedDeliveryIdRef.current = null;
      return;
    }
    if (prevSelectedDeliveryIdRef.current !== selectedDeliveryId) {
      prevSelectedDeliveryIdRef.current = selectedDeliveryId;
      setChain(null);
      setChainDeliveryId(null);
    }
  }, [selectedDeliveryId]);

  useEffect(() => {
    if (!selectedDeliveryId) {
      return;
    }
    void loadChain(selectedDeliveryId, activePlanVersionId);
  }, [selectedDeliveryId, activePlanVersionId, loadChain]);

  const chainForSelection = useMemo(() => {
    if (!chain || chainDeliveryId !== selectedDeliveryId) {
      return null;
    }
    return chain;
  }, [chain, chainDeliveryId, selectedDeliveryId]);

  const displayNodes = chainForSelection?.nodes ?? [];
  const displayEdges = chainForSelection?.edges ?? [];
  const trialRevision = useMemo(() => chainTrialRevision(chainForSelection), [chainForSelection]);
  const solverEngine = useMemo(() => chainSolverEngine(chainForSelection), [chainForSelection]);

  const ganttSupplyOrderNode = useMemo(
    () => resolveSupplyOrderNodeForGantt(selectedChainNodeId, displayNodes),
    [selectedChainNodeId, displayNodes],
  );

  useEffect(() => {
    if (displayNodes.length === 0) {
      return;
    }
    const firstWo = displayNodes.find(
      (n) => n.nodeType === 'SUPPLY_ORDER' && (n.operations?.length ?? 0) > 0,
    );
    const root = displayNodes.find((n) => n.nodeType === 'SALES_ORDER');
    setSelectedChainNodeId(firstWo?.nodeId ?? root?.nodeId ?? null);
  }, [selectedDeliveryId, chainForSelection, displayNodes]);

  const runAction = useCallback(
    async (row: CustomerOrderLineDeliveryListItem, action: OrderDemandActionId) => {
      setActionLoading(true);
      setActionError(false);
      setActionMessage(null);
      setError(null);
      try {
        const result = await api.ontologyDeliveryAction(
          row.deliveryId,
          action,
          { masterPlanVersionId: activePlanVersionId ?? undefined },
          activePlanVersionId ?? undefined,
        );
        setActionMessage(result.message);

        const needsListRefresh =
          action === 'INFINITE_PLAN_JIT' ||
          action === 'BUILD_UPSTREAM_CHAIN' ||
          action === 'CONFIRM_PROMISE_DATE';
        let target: CustomerOrderLineDeliveryListItem = row;
        if (action === 'CANCEL_PLAN' && result.fulfillmentChain) {
          const patched = patchRowAfterCancel(row, result.fulfillmentChain);
          setRows((prev) => prev.map((r) => (r.deliveryId === row.deliveryId ? patched : r)));
          if (selected?.deliveryId === row.deliveryId) {
            setSelected(patched);
          }
          target = patched;
        } else if (needsListRefresh) {
          const updated = await load(activePlanVersionId, row.deliveryId);
          if (updated) {
            target = updated;
          }
        }

        if (result.fulfillmentChain) {
          setChain(result.fulfillmentChain);
          setChainDeliveryId(target.deliveryId);
        } else {
          await loadChain(target.deliveryId, activePlanVersionId);
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : '操作失败';
        setActionError(true);
        setActionMessage(msg);
      } finally {
        setActionLoading(false);
      }
    },
    [activePlanVersionId, load, loadChain, selected?.deliveryId],
  );

  const selectOrder = (row: CustomerOrderLineDeliveryListItem) => {
    setSelected(row);
  };

  const openContextMenu = (e: MouseEvent, row: CustomerOrderLineDeliveryListItem) => {
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

  const overallStatus = chainForSelection?.overallStatus;

  const requestAction = (row: CustomerOrderLineDeliveryListItem, action: OrderDemandActionId) => {
    if (action === 'INFINITE_PLAN_JIT' || action === 'BUILD_UPSTREAM_CHAIN') {
      void runAction(row, action);
      return;
    }
    setPendingAction({ row, action });
  };

  const contextMenuItems = contextMenu
    ? [
        {
          id: 'infinite-jit',
          label: '无限能力计划（JIT） / InfinitePlanJIT',
          onSelect: () => requestAction(contextMenu.row, 'INFINITE_PLAN_JIT'),
        },
        {
          id: 'finite',
          label: '有限能力计划 / FinitePlan',
          onSelect: () => requestAction(contextMenu.row, 'FINITE_PLAN'),
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
        {
          id: 'cancel-promise',
          label: '取消承诺交期',
          onSelect: () => requestAction(contextMenu.row, 'CANCEL_PROMISE'),
        },
      ]
    : [];

  return (
    <div className={`demand-page ${embedded ? 'demand-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          variant={DECISION_PAGE_HEADER}
          title="需求满足"
          showScenarioSelector
          description={`本体运行模式：列表为 CustomerOrderLineDelivery，满足链基于 SupplyOrder；右键可试算推演；${scenarioHint}`}
          actions={
            <button
              type="button"
              className="btn primary"
              onClick={() => void load(activePlanVersionId, selected?.deliveryId)}
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
        </div>
      )}

      <div className="demand-s01-layout">
        <aside className="demand-kpi-stack">
          <div className="demand-kpi-panel card">
            <h3 className="panel-title">需求池 KPI</h3>
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
          </div>
          <MasterPlanBusinessKpiPanel
            planVersionId={activePlanVersionId}
            filterKpiIds={['KPI-MP-B01', 'KPI-MP-B02', 'KPI-MP-B03']}
            title="主计划交付 KPI"
          />
        </aside>

        <VerticalResizeSplit
          className="demand-right-split"
          storageKey="demand-s01-orders-ratio"
          defaultTopRatio={0.32}
          minTopRatio={0.18}
          maxTopRatio={0.55}
          top={
            <section className="demand-orders-panel card">
              <h3 className="panel-title">订单交付列表</h3>
              <p className="chain-meta" style={{ margin: '0 0 0.5rem' }}>
                CustomerOrderLineDelivery · 右键行打开操作菜单
              </p>
              <div className="panel-scroll order-table-wrap">
                <FilterableTable
                  tableId="demand-orders"
                  tableClassName="order-select-table"
                  wrapClassName="ft-table-wrap"
                  rows={rows}
                  rowKey={(r) => deliveryKey(r)}
                  emptyText="暂无客户交付"
                  onRowClick={selectOrder}
                  getRowClassName={(r) => (selected && deliveryKey(r) === deliveryKey(selected) ? 'selected' : '')}
                  getRowProps={(row) => ({
                    onContextMenu: (e) => openContextMenu(e, row),
                  })}
                  columns={[
                    {
                      key: 'order',
                      header: '订单',
                      render: (r) => `${r.salesOrderNo}-${r.salesOrderLineNo}`,
                    },
                    { key: 'deliveryId', header: '交付', render: (r) => r.deliveryId.split('-').slice(-1)[0] ?? r.deliveryId },
                    { key: 'product', header: '产品', render: (r) => r.productCode },
                    { key: 'qty', header: '交付量', render: (r) => r.deliveryQty },
                    { key: 'dueDate', header: '最晚交期', render: (r) => r.latestDesiredDate ?? '—' },
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
            <VerticalResizeSplit
              className="demand-fulfillment-stack"
              storageKey="demand-fulfillment-gantt-ratio"
              defaultTopRatio={0.38}
              minTopRatio={0.22}
              maxTopRatio={0.58}
              top={
                <section className="demand-fulfillment-panel card">
                  {selected ? (
                    <>
                      <div className="chain-header">
                        <div>
                          <h3 className="panel-title">
                            订单满足 · {selected.salesOrderNo}-{selected.salesOrderLineNo}
                          </h3>
                          <p className="chain-meta">
                            交付 {selected.deliveryId} · 产品 {selected.productCode} · 最晚交期{' '}
                            {selected.latestDesiredDate ?? '—'}
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
                            {trialRevision > 0 && (
                              <> · 预览 #{trialRevision}{solverEngine ? ` (${solverEngine})` : ''}</>
                            )}
                          </p>
                        </div>
                        {(chainLoading || actionLoading) && (
                          <div className="chain-header-actions">
                            <span className="chain-loading">更新中…</span>
                          </div>
                        )}
                      </div>
                      <FulfillmentRootCausePanel chain={chainForSelection} />
                      <div className="demand-fulfillment-body">
                        <FulfillmentChainTreePanel
                          nodes={displayNodes}
                          edges={displayEdges}
                          selectedNodeId={selectedChainNodeId}
                          onSelectNode={selectChainNode}
                          loading={chainLoading}
                        />
                      </div>
                    </>
                  ) : (
                    <div className="panel-scroll chain-scroll chain-empty">
                      <p className="empty">请选择上方订单交付查看满足链</p>
                    </div>
                  )}
                </section>
              }
              bottom={
                <section className="demand-gantt-panel card">
                  {selected ? (
                    <>
                      <div className="chain-header demand-gantt-header">
                        <div>
                          <h3 className="panel-title">工单甘特 · PlanUnit / 工序</h3>
                          <p className="chain-meta">
                            {ganttSupplyOrderNode
                              ? `${ganttSupplyOrderNode.label} · 点击工序查看时间窗约束`
                              : '在上方满足链中选择供应订单（工单）'}
                          </p>
                        </div>
                        <FulfillmentGanttToolbar
                          showArrows
                          viewMode={ganttViewMode}
                          onViewModeChange={setGanttViewMode}
                          compact
                        />
                      </div>
                      <FulfillmentMaterialDrawer
                        nodes={displayNodes}
                        edges={displayEdges}
                        selectedTaskId={selectedChainNodeId}
                      >
                        <SupplyOrderPlanUnitGantt
                          node={ganttSupplyOrderNode}
                          viewMode={ganttViewMode}
                          planVersionId={activePlanVersionId}
                        />
                      </FulfillmentMaterialDrawer>
                    </>
                  ) : (
                    <div className="panel-scroll chain-scroll chain-empty">
                      <p className="empty">请选择订单交付</p>
                    </div>
                  )}
                </section>
              }
            />
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
          row={deliveryToActionRow(pendingAction.row)}
          deliveryId={pendingAction.row.deliveryId}
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
