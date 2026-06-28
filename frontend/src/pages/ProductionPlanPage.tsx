import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { FulfillmentChainTree } from '../components/FulfillmentChainTree';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { PpToolbar, PpToolbarHint, PpToolbarRow } from '../components/PpToolbar';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { WorkOrderHierarchyTable } from '../components/WorkOrderHierarchyTable';
import { WorkOrderOperationPlanGantt, type GanttTimingMarkerOptions } from '../components/WorkOrderOperationPlanGantt';
import { buildWorkOrderForest } from '../utils/workOrderTree';
import {
  buildDownstreamChainTree,
  buildUpstreamChainTree,
} from '../utils/fulfillmentChainTree';
import { usePlan } from '../context/PlanContext';
import type {
  OrderFulfillmentChain,
  WorkOrder,
  WorkOrderCapacityGantt,
  WorkOrderDispatchResult,
} from '../types/api';
import './ProductionPlanPage.css';

type BottomDetailTab = 'operationPlan' | 'upstream' | 'downstream';

export function ProductionPlanPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [rows, setRows] = useState<WorkOrder[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [lastDispatch, setLastDispatch] = useState<WorkOrderDispatchResult | null>(null);
  const [filter, setFilter] = useState<'all' | 'pending' | 'dispatched'>('all');

  const [activeWo, setActiveWo] = useState<WorkOrder | null>(null);
  const [bottomTab, setBottomTab] = useState<BottomDetailTab>('operationPlan');

  const [operationPlan, setOperationPlan] = useState<WorkOrderCapacityGantt | null>(null);
  const [operationPlanLoading, setOperationPlanLoading] = useState(false);

  const [upstreamChain, setUpstreamChain] = useState<OrderFulfillmentChain | null>(null);
  const [upstreamLoading, setUpstreamLoading] = useState(false);

  const [downstreamChain, setDownstreamChain] = useState<OrderFulfillmentChain | null>(null);
  const [downstreamLoading, setDownstreamLoading] = useState(false);

  const [ganttMarkerOptions, setGanttMarkerOptions] = useState<GanttTimingMarkerOptions>({
    showLatestConstraints: true,
    showEarliestFeasible: true,
    showEarliestOwn: true,
  });

  const load = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.workOrders.list(versionId ?? undefined));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(activePlanVersionId);
  }, [activePlanVersionId, load]);

  const filtered = useMemo(() => {
    if (filter === 'pending') {
      return rows.filter((r) => r.dispatchStatus !== 'DISPATCHED');
    }
    if (filter === 'dispatched') {
      return rows.filter((r) => r.dispatchStatus === 'DISPATCHED');
    }
    return rows;
  }, [rows, filter]);

  useEffect(() => {
    if (filtered.length === 0) {
      setActiveWo(null);
      return;
    }
    setActiveWo((prev) => {
      if (prev) {
        const match = filtered.find((r) => r.workOrderNo === prev.workOrderNo);
        if (match) return match;
      }
      const roots = buildWorkOrderForest(filtered);
      return roots[0]?.workOrder ?? filtered[0];
    });
  }, [filtered]);

  const loadOperationPlan = useCallback(
    async (wo: WorkOrder, versionId: string | null | undefined) => {
      if (!versionId) {
        setOperationPlan(null);
        return;
      }
      setOperationPlanLoading(true);
      try {
        setOperationPlan(await api.workOrderCapacityGantt(versionId, wo.workOrderNo));
      } catch (e) {
        setOperationPlan(null);
        setError(e instanceof Error ? e.message : '工序计划加载失败');
      } finally {
        setOperationPlanLoading(false);
      }
    },
    [],
  );

  const loadUpstream = useCallback(async (wo: WorkOrder, versionId: string | null | undefined) => {
    setUpstreamLoading(true);
    try {
      setUpstreamChain(
        await api.ontologySupplyOrderUpstreamChain(wo.workOrderNo, versionId ?? undefined),
      );
    } catch (e) {
      setUpstreamChain(null);
      setError(e instanceof Error ? e.message : '上游满足链加载失败');
    } finally {
      setUpstreamLoading(false);
    }
  }, []);

  const loadDownstream = useCallback(async (wo: WorkOrder, versionId: string | null | undefined) => {
    setDownstreamLoading(true);
    try {
      setDownstreamChain(
        await api.ontologySupplyOrderDownstreamChain(wo.workOrderNo, versionId ?? undefined),
      );
    } catch (e) {
      setDownstreamChain(null);
      setError(e instanceof Error ? e.message : '下游满足链加载失败');
    } finally {
      setDownstreamLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!activeWo) {
      setOperationPlan(null);
      setUpstreamChain(null);
      setDownstreamChain(null);
      return;
    }
    if (bottomTab === 'operationPlan') {
      void loadOperationPlan(activeWo, activePlanVersionId);
    } else if (bottomTab === 'upstream') {
      void loadUpstream(activeWo, activePlanVersionId);
    } else {
      void loadDownstream(activeWo, activePlanVersionId);
    }
  }, [
    activeWo,
    activePlanVersionId,
    bottomTab,
    loadOperationPlan,
    loadUpstream,
    loadDownstream,
  ]);

  const upstreamRoots = useMemo(() => {
    if (!activeWo || !upstreamChain) return [];
    return buildUpstreamChainTree(
      upstreamChain.nodes,
      upstreamChain.edges,
      activeWo.workOrderNo,
    );
  }, [activeWo, upstreamChain]);

  const downstreamRoots = useMemo(() => {
    if (!activeWo || !downstreamChain) return [];
    return buildDownstreamChainTree(
      downstreamChain.nodes,
      downstreamChain.edges,
      activeWo.workOrderNo,
    );
  }, [activeWo, downstreamChain]);

  const pendingSelectable = useMemo(
    () => filtered.filter((r) => r.dispatchStatus !== 'DISPATCHED'),
    [filtered],
  );

  const toggle = (woNo: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(woNo)) {
        next.delete(woNo);
      } else {
        next.add(woNo);
      }
      return next;
    });
  };

  const toggleAllPending = () => {
    const pendingNos = pendingSelectable.map((r) => r.workOrderNo);
    const allSelected = pendingNos.length > 0 && pendingNos.every((n) => selected.has(n));
    if (allSelected) {
      setSelected((prev) => {
        const next = new Set(prev);
        pendingNos.forEach((n) => next.delete(n));
        return next;
      });
    } else {
      setSelected((prev) => new Set([...prev, ...pendingNos]));
    }
  };

  const dispatchSelected = async () => {
    const nos = [...selected].filter((no) => {
      const row = rows.find((r) => r.workOrderNo === no);
      return row && row.dispatchStatus !== 'DISPATCHED';
    });
    if (nos.length === 0) {
      setError('请勾选待下发的工单');
      return;
    }
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.workOrders.dispatch(nos);
      setLastDispatch(result);
      setSuccess(`已下发 ${result.dispatchedCount} 张工单至排程`);
      setSelected(new Set());
      await load(activePlanVersionId);
    } catch (e) {
      setError(e instanceof Error ? e.message : '下发失败');
    } finally {
      setLoading(false);
    }
  };

  const currentScenario = scenarios.find((s) => s.scenarioId === selectedScenarioId);
  const scenarioHint = currentScenario
    ? `场景「${currentScenario.name}」${activePlanVersionId ? ` · ${activePlanVersionId}` : ''}`
    : '请先在顶部选择计划场景';

  const detailLoading =
    (bottomTab === 'operationPlan' && operationPlanLoading) ||
    (bottomTab === 'upstream' && upstreamLoading) ||
    (bottomTab === 'downstream' && downstreamLoading);

  return (
    <div className={`production-plan-page ${embedded ? 'production-plan-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          variant={DECISION_PAGE_HEADER}
          title="生产工单"
          showScenarioSelector
          description={`MRP 合并工单列表；选中工单后可查看工序计划、上游/下游满足链。${scenarioHint}`}
        />
      )}
      <StatusBanner loading={loading} error={error} success={success} />

      <PpToolbar>
        <PpToolbarRow>
          <div className="pp-filters">
            <button
              type="button"
              className={filter === 'all' ? 'pp-filter active' : 'pp-filter'}
              onClick={() => setFilter('all')}
            >
              全部 ({rows.length})
            </button>
            <button
              type="button"
              className={filter === 'pending' ? 'pp-filter active' : 'pp-filter'}
              onClick={() => setFilter('pending')}
            >
              待下发 ({rows.filter((r) => r.dispatchStatus !== 'DISPATCHED').length})
            </button>
            <button
              type="button"
              className={filter === 'dispatched' ? 'pp-filter active' : 'pp-filter'}
              onClick={() => setFilter('dispatched')}
            >
              已下发 ({rows.filter((r) => r.dispatchStatus === 'DISPATCHED').length})
            </button>
          </div>
          <div className="pp-toolbar-actions">
            <button
              type="button"
              className="btn"
              onClick={() => void load(activePlanVersionId)}
              disabled={loading}
            >
              刷新
            </button>
            <button
              type="button"
              className="btn primary"
              onClick={() => void dispatchSelected()}
              disabled={loading || selected.size === 0}
            >
              下发排程 ({selected.size})
            </button>
          </div>
        </PpToolbarRow>
        <PpToolbarHint>
          <p className="pp-hint">
            平铺展示全部 MRP 工单（成品 + 组件），按 BOM 层级与序号排序。下发后可前往{' '}
            <Link to="/scheduling/kitting">物料齐套</Link> 检查，并在{' '}
            <Link to="/scheduling/detail-schedule">生产排程</Link> 求解工序排程。
            {lastDispatch && (
              <span className="pp-last-dispatch">
                · 最近下发 {lastDispatch.dispatchedCount} 张 · {lastDispatch.dispatchedTs}
              </span>
            )}
          </p>
        </PpToolbarHint>
      </PpToolbar>

      <VerticalResizeSplit
        className="pp-split"
        storageKey="production-plan-split-ratio"
        minTopRatio={0.3}
        maxTopRatio={0.75}
        top={
          <section className="card pp-wo-panel">
            <div className="pp-panel-head">
              <h3 className="panel-title">工单列表</h3>
              <div className="pp-source-legend">
                <span className="tag tag-external">成品工单</span>
                <span className="tag tag-replenish">组件工单</span>
              </div>
              <label className="pp-select-all" title="全选待下发">
                <input
                  type="checkbox"
                  checked={
                    pendingSelectable.length > 0 &&
                    pendingSelectable.every((r) => selected.has(r.workOrderNo))
                  }
                  onChange={toggleAllPending}
                  disabled={pendingSelectable.length === 0}
                  aria-label="全选待下发"
                />
              </label>
            </div>
            <div className="pp-panel-scroll pp-table-wrap">
              <WorkOrderHierarchyTable
                rows={filtered}
                activePlanVersionId={activePlanVersionId}
                selected={selected}
                activeWorkOrderNo={activeWo?.workOrderNo ?? null}
                onToggleSelect={toggle}
                onRowClick={setActiveWo}
                treeMode="mrp"
                emptyText="暂无工单，请先在「计划运行」执行主计划流程"
              />
            </div>
          </section>
        }
        bottom={
          <section className="card pp-chain-panel">
            <div className="pp-panel-head">
              <div className="pp-chain-title">
                <h3 className="panel-title">
                  {activeWo ? `工单详情 · ${activeWo.workOrderNo}` : '工单详情'}
                </h3>
                {activeWo && (
                  <span className="pp-chain-sub">
                    产品 {activeWo.productCode} ×{activeWo.quantity}
                    {activeWo.peggingCount
                      ? ` · 合并 ×${activeWo.peggingCount}`
                      : activeWo.salesOrderNo
                        ? ` · 订单 ${activeWo.salesOrderNo}-${activeWo.salesOrderLineNo}`
                        : ''}
                  </span>
                )}
              </div>
              <div className="pp-detail-tabs" role="tablist">
                <button
                  type="button"
                  role="tab"
                  aria-selected={bottomTab === 'operationPlan'}
                  className={bottomTab === 'operationPlan' ? 'pp-tab active' : 'pp-tab'}
                  onClick={() => setBottomTab('operationPlan')}
                >
                  工序计划
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={bottomTab === 'upstream'}
                  className={bottomTab === 'upstream' ? 'pp-tab active' : 'pp-tab'}
                  onClick={() => setBottomTab('upstream')}
                >
                  上游满足链
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={bottomTab === 'downstream'}
                  className={bottomTab === 'downstream' ? 'pp-tab active' : 'pp-tab'}
                  onClick={() => setBottomTab('downstream')}
                >
                  下游满足链
                </button>
              </div>
              <div className="pp-chain-actions">
                {bottomTab === 'operationPlan' && activeWo && (
                  <div className="pp-gantt-marker-toggles" role="group" aria-label="甘特图时间参考线">
                    <label className="pp-marker-toggle">
                      <input
                        type="checkbox"
                        checked={ganttMarkerOptions.showLatestConstraints}
                        onChange={(e) =>
                          setGanttMarkerOptions((prev) => ({
                            ...prev,
                            showLatestConstraints: e.target.checked,
                          }))
                        }
                      />
                      <span className="pp-marker-swatch pp-marker-swatch--red" aria-hidden />
                      最晚/可行开始
                    </label>
                    <label className="pp-marker-toggle">
                      <input
                        type="checkbox"
                        checked={ganttMarkerOptions.showEarliestFeasible}
                        onChange={(e) =>
                          setGanttMarkerOptions((prev) => ({
                            ...prev,
                            showEarliestFeasible: e.target.checked,
                          }))
                        }
                      />
                      <span className="pp-marker-swatch pp-marker-swatch--yellow" aria-hidden />
                      可行开始/交付
                    </label>
                    <label className="pp-marker-toggle">
                      <input
                        type="checkbox"
                        checked={ganttMarkerOptions.showEarliestOwn}
                        onChange={(e) =>
                          setGanttMarkerOptions((prev) => ({
                            ...prev,
                            showEarliestOwn: e.target.checked,
                          }))
                        }
                      />
                      <span className="pp-marker-swatch pp-marker-swatch--gray" aria-hidden />
                      自身开始/交付
                    </label>
                  </div>
                )}
                {detailLoading && <span className="chain-loading">加载中…</span>}
              </div>
            </div>

            {!activeWo ? (
              <div className="pp-chain-empty">
                <p className="empty">请在上方工单列表中选择一个工单</p>
              </div>
            ) : bottomTab === 'operationPlan' ? (
              <div className="pp-schedule-host">
                <WorkOrderOperationPlanGantt
                  data={operationPlan}
                  loading={operationPlanLoading}
                  markerOptions={ganttMarkerOptions}
                />
              </div>
            ) : bottomTab === 'upstream' ? (
              <div className="pp-tree-host">
                <FulfillmentChainTree
                  nodes={upstreamChain?.nodes ?? []}
                  edges={upstreamChain?.edges ?? []}
                  roots={upstreamRoots}
                  title="上游追溯"
                  meta="子件工单 · 库存 · 缺料"
                />
                {upstreamChain && upstreamChain.nodes.length > 0 && (
                  <div className="chain-legend">
                    <span className="legend-item"><i className="dot ok" /> 库存满足</span>
                    <span className="legend-item"><i className="dot planned" /> 工单满足</span>
                    <span className="legend-item"><i className="dot risk" /> 缺料</span>
                  </div>
                )}
              </div>
            ) : (
              <div className="pp-tree-host">
                <FulfillmentChainTree
                  nodes={downstreamChain?.nodes ?? []}
                  edges={downstreamChain?.edges ?? []}
                  roots={downstreamRoots}
                  title="下游追溯"
                  meta="父工单 · 销售订单"
                />
                {downstreamChain && downstreamChain.nodes.length > 0 && (
                  <div className="chain-legend">
                    <span className="legend-item"><i className="dot planned" /> 父工单</span>
                    <span className="legend-item"><i className="dot pending" /> 销售订单需求</span>
                  </div>
                )}
              </div>
            )}
          </section>
        }
      />
    </div>
  );
}
