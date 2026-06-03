import { useCallback, useEffect, useMemo, useState } from 'react';
import { ViewMode } from 'gantt-task-react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { FulfillmentChainSyncView } from '../components/FulfillmentChainSyncView';
import { FulfillmentGanttToolbar } from '../components/FulfillmentGanttToolbar';
import { OrderChainNodeDetail } from '../components/OrderChainNodeDetail';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import { usePlan } from '../context/PlanContext';
import type { DemandPoolEntry } from '../types/api';
import type { OrderPlanningChain, OrderPlanningChainNode } from '../types/orderPlanningChain';
import {
  chainEdgesForGantt,
  orderPlanningChainToDisplayNodes,
  orderPlanningChainToGanttTasks,
} from '../utils/orderPlanningChainGantt';
import { FULFILLMENT_STATUS_LABEL } from '../utils/fulfillmentGantt';
import './OrderPlanningChainPage.css';
import './DemandPage.css';

function orderKey(o: DemandPoolEntry) {
  return `${o.salesOrderNo}-${o.salesOrderLineNo}`;
}

function statusClass(status: string) {
  if (status === 'BLOCKED' || status === 'SHORTAGE') return 'badge danger';
  if (status === 'AT_RISK' || status === 'WARN') return 'badge danger';
  if (status === 'OK' || status === 'ON_TRACK') return 'badge ok';
  return 'badge info';
}

export function OrderPlanningChainPage() {
  const { activePlanVersionId, masterPlan } = usePlan();
  const strategyId = masterPlan?.strategyId ?? null;

  const [rows, setRows] = useState<DemandPoolEntry[]>([]);
  const [selected, setSelected] = useState<DemandPoolEntry | null>(null);
  const [chain, setChain] = useState<OrderPlanningChain | null>(null);
  const [loading, setLoading] = useState(false);
  const [chainLoading, setChainLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [useFeedbackOverlay, setUseFeedbackOverlay] = useState(false);
  const [feedbackCutoff, setFeedbackCutoff] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [baselineVersionId, setBaselineVersionId] = useState('');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [showArrows, setShowArrows] = useState(true);
  const [ganttViewMode, setGanttViewMode] = useState(ViewMode.Day);
  const [ganttTasks, setGanttTasks] = useState<import('gantt-task-react').Task[]>([]);

  const loadPool = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      const pool = await api.demandPool(versionId ?? undefined);
      setRows(pool);
      setSelected((prev) => {
        if (prev) {
          const match = pool.find((r) => orderKey(r) === orderKey(prev));
          return match ?? pool[0] ?? null;
        }
        return pool[0] ?? null;
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const runPreview = useCallback(async () => {
    if (!selected) return;
    setChainLoading(true);
    setError(null);
    try {
      const result = await api.previewOrderPlanningChain({
        salesOrderNo: selected.salesOrderNo,
        salesOrderLineNo: selected.salesOrderLineNo,
        masterPlanStrategyId: strategyId ?? undefined,
        useFeedbackOverlay,
        feedbackCutoff: useFeedbackOverlay ? feedbackCutoff : undefined,
        detailScheduleMasterPlanVersionId: activePlanVersionId ?? undefined,
        baselineMasterPlanVersionId: baselineVersionId.trim() || undefined,
      });
      setChain(result);
      setSelectedNodeId(null);
    } catch (e) {
      setChain(null);
      setError(e instanceof Error ? e.message : '试算失败');
    } finally {
      setChainLoading(false);
    }
  }, [
    selected,
    strategyId,
    useFeedbackOverlay,
    feedbackCutoff,
    activePlanVersionId,
    baselineVersionId,
  ]);

  useEffect(() => {
    void loadPool(activePlanVersionId);
  }, [activePlanVersionId, loadPool]);

  useEffect(() => {
    if (selected) {
      void runPreview();
    }
  }, [selected, runPreview]);

  useEffect(() => {
    setGanttTasks(orderPlanningChainToGanttTasks(chain?.nodes ?? []));
  }, [chain]);

  const displayNodes = useMemo(
    () => orderPlanningChainToDisplayNodes(chain?.nodes ?? []),
    [chain],
  );

  const selectedNode: OrderPlanningChainNode | null = useMemo(() => {
    if (!selectedNodeId || !chain) return null;
    return chain.nodes.find((n) => n.nodeId === selectedNodeId) ?? null;
  }, [chain, selectedNodeId]);

  return (
    <div className="order-planning-chain-page demand-page">
      <PageHeader
        title="订单推演"
        showScenarioSelector
        description="基于 S04/S05 推演层预览订单满足全链（不求解 Timefold），可试算策略与反馈 overlay。"
        actions={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => void runPreview()} disabled={chainLoading || !selected}>
              {chainLoading ? '试算中…' : '刷新试算'}
            </button>
            <Link to="/master-plan/plan-run" className="btn">
              正式求解
            </Link>
          </>
        }
      />

      <div className="opchain-toolbar card">
        <label className="opchain-check">
          <input
            type="checkbox"
            checked={useFeedbackOverlay}
            onChange={(e) => setUseFeedbackOverlay(e.target.checked)}
          />
          反馈 overlay
        </label>
        <label>
          截止日
          <input
            type="date"
            className="input"
            value={feedbackCutoff}
            disabled={!useFeedbackOverlay}
            onChange={(e) => setFeedbackCutoff(e.target.value)}
          />
        </label>
        <label>
          基准主计划版本
          <input
            className="input opchain-baseline"
            placeholder="可选 MP-xxx"
            value={baselineVersionId}
            onChange={(e) => setBaselineVersionId(e.target.value)}
          />
        </label>
        {chain?.summary && (
          <span className="opchain-summary-chip">
            策略 {chain.summary.capacityStrategy ?? '—'} · 工单 {chain.summary.workOrderCount}
          </span>
        )}
      </div>

      <StatusBanner loading={loading} error={error} />

      <div className="demand-body">
        <VerticalResizeSplit
          storageKey="order-planning-chain-split"
          minTopRatio={0.28}
          maxTopRatio={0.7}
          top={
            <section className="card demand-table-panel">
              <FilterableTable
                tableId="order-planning-chain-orders"
                rows={rows}
                rowKey={(r) => orderKey(r)}
                emptyText="暂无订单"
                onRowClick={setSelected}
                getRowClassName={(r) =>
                  selected && orderKey(r) === orderKey(selected) ? 'selected' : ''
                }
                columns={[
                  { key: 'order', header: '订单', render: (r) => `${r.salesOrderNo}-${r.salesOrderLineNo}` },
                  { key: 'product', header: '产品', render: (r) => r.productCode },
                  { key: 'due', header: '交期', render: (r) => r.dueDate },
                ]}
              />
            </section>
          }
          bottom={
            <section className="demand-chain-panel card opchain-bottom">
              {selected && chain ? (
                <div className="opchain-layout">
                  <aside className="opchain-sidebar">
                    <div className="chain-header">
                      <h3 className="panel-title">
                        推演链 · {selected.salesOrderNo}-{selected.salesOrderLineNo}
                      </h3>
                      <p className="chain-meta">
                        总体{' '}
                        <span className={statusClass(chain.overallStatus)}>
                          {FULFILLMENT_STATUS_LABEL[chain.overallStatus] ?? chain.overallStatus}
                        </span>
                        {chain.compare && (
                          <> · 对比 {chain.compare.baselineVersionId}</>
                        )}
                      </p>
                    </div>
                    <OrderChainNodeDetail node={selectedNode} />
                  </aside>
                  <div className="opchain-gantt">
                    <div className="chain-header-actions">
                      {chain.nodes.length > 0 && (
                        <FulfillmentGanttToolbar
                          showArrows={showArrows}
                          onShowArrowsChange={setShowArrows}
                          viewMode={ganttViewMode}
                          onViewModeChange={setGanttViewMode}
                          compact
                        />
                      )}
                    </div>
                    <FulfillmentChainSyncView
                      nodes={displayNodes}
                      edges={chainEdgesForGantt(chain.edges)}
                      tasks={ganttTasks}
                      onTasksChange={setGanttTasks}
                      selectedNodeId={selectedNodeId}
                      onSelectNode={setSelectedNodeId}
                      showArrows={showArrows}
                      viewMode={ganttViewMode}
                    />
                    <div className="chain-legend">
                      <span className="legend-item"><i className="dot ok" /> 可行 OK</span>
                      <span className="legend-item"><i className="dot risk" /> 预警 AT_RISK</span>
                      <span className="legend-item"><i className="dot planned" /> 阻断 BLOCKED</span>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="empty">{selected ? '试算中…' : '请选择订单'}</p>
              )}
            </section>
          }
        />
      </div>
    </div>
  );
}
