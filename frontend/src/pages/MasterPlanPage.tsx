import { useCallback, useEffect, useMemo, useState } from 'react';
import { ViewMode } from 'gantt-task-react';
import { api } from '../api/client';
import { FulfillmentChainSyncView } from '../components/FulfillmentChainSyncView';
import { FulfillmentGanttToolbar } from '../components/FulfillmentGanttToolbar';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import { WorkOrderCapacityGantt } from '../components/WorkOrderCapacityGantt';
import { usePlan } from '../context/PlanContext';
import type {
  MasterPlanAllocation,
  OrderFulfillmentChain,
  WorkOrderCapacityGantt as WorkOrderCapacityGanttModel,
} from '../types/api';
import { CAPACITY_STRATEGY_LABELS, type MasterPlanStrategySummary } from '../types/masterPlanStrategies';
import { fulfillmentChainToGanttTasks } from '../utils/fulfillmentGantt';
import { isFinishedGoodsSource, WORK_ORDER_SOURCE_LABEL } from '../utils/workOrderSourceLabels';
import './MasterPlanPage.css';

type BottomTab = 'fulfillment' | 'capacity';

function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function allocationKey(a: MasterPlanAllocation): string {
  return a.allocationId ?? a.workOrderNo;
}

const CAPACITY_STRATEGY_OPTIONS = (
  Object.keys(CAPACITY_STRATEGY_LABELS) as import('../types/api').MasterPlanCapacityStrategy[]
).map((value) => ({ value, label: CAPACITY_STRATEGY_LABELS[value] }));

export function MasterPlanPage() {
  const { masterPlan, setMasterPlan } = usePlan();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lookupId, setLookupId] = useState('');
  const [selectedWo, setSelectedWo] = useState<MasterPlanAllocation | null>(null);
  const [tab, setTab] = useState<BottomTab>('fulfillment');
  const [chain, setChain] = useState<OrderFulfillmentChain | null>(null);
  const [chainLoading, setChainLoading] = useState(false);
  const [chainTasks, setChainTasks] = useState<import('gantt-task-react').Task[]>([]);
  const [showArrows, setShowArrows] = useState(true);
  const [ganttViewMode, setGanttViewMode] = useState(ViewMode.Day);
  const [selectedChainNodeId, setSelectedChainNodeId] = useState<string | null>(null);

  const [capacityGantt, setCapacityGantt] = useState<WorkOrderCapacityGanttModel | null>(null);
  const [capacityLoading, setCapacityLoading] = useState(false);
  const [strategies, setStrategies] = useState<MasterPlanStrategySummary[]>([]);
  const [selectedStrategyId, setSelectedStrategyId] = useState('');

  const loadStrategies = useCallback(async () => {
    const list = await api.listMasterPlanStrategies();
    setStrategies(list);
    setSelectedStrategyId((prev) => {
      if (list.length === 0) return '';
      if (prev && list.some((s) => s.id === prev)) return prev;
      return list.find((s) => s.isDefault)?.id ?? list[0].id;
    });
  }, []);

  useEffect(() => {
    void loadStrategies();
  }, [loadStrategies]);

  const allocations = masterPlan?.allocations ?? [];
  const kpis = masterPlan?.kpis ?? [];

  useEffect(() => {
    if (allocations.length === 0) {
      setSelectedWo(null);
      return;
    }
    setSelectedWo((prev) => {
      if (prev) {
        const match = allocations.find((a) => allocationKey(a) === allocationKey(prev));
        if (match) return match;
      }
      return allocations[0];
    });
  }, [allocations]);

  const solve = async () => {
    if (!selectedStrategyId) {
      setError('请先配置主计划策略');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await api.solveMasterPlan(selectedStrategyId);
      setMasterPlan(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : '求解失败');
    } finally {
      setLoading(false);
    }
  };

  const loadVersion = async () => {
    if (!lookupId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      setMasterPlan(await api.getMasterPlan(lookupId.trim()));
    } catch (e) {
      setError(e instanceof Error ? e.message : '版本不存在');
    } finally {
      setLoading(false);
    }
  };

  const loadChain = useCallback(async (wo: MasterPlanAllocation) => {
    setChainLoading(true);
    try {
      setChain(await api.fulfillmentChain(wo.salesOrderNo, wo.salesOrderLineNo));
    } catch (e) {
      setChain(null);
      setError(e instanceof Error ? e.message : '满足链加载失败');
    } finally {
      setChainLoading(false);
    }
  }, []);

  const loadCapacity = useCallback(
    async (versionId: string, wo: MasterPlanAllocation) => {
      setCapacityLoading(true);
      try {
        setCapacityGantt(await api.workOrderCapacityGantt(versionId, wo.workOrderNo));
      } catch (e) {
        setCapacityGantt(null);
        setError(e instanceof Error ? e.message : '产能甘特加载失败');
      } finally {
        setCapacityLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!selectedWo) {
      setChain(null);
      setCapacityGantt(null);
      return;
    }
    if (tab === 'fulfillment') {
      void loadChain(selectedWo);
    } else if (tab === 'capacity' && masterPlan) {
      void loadCapacity(masterPlan.planVersionId, selectedWo);
    }
  }, [selectedWo, tab, masterPlan, loadChain, loadCapacity]);

  useEffect(() => {
    setChainTasks(fulfillmentChainToGanttTasks(chain?.nodes ?? [], chain?.edges ?? []));
    if (selectedWo && chain) {
      const woNodeId = `wo-${selectedWo.workOrderNo}`;
      const matched = chain.nodes.find((n) => n.nodeId === woNodeId);
      setSelectedChainNodeId(matched?.nodeId ?? null);
    } else {
      setSelectedChainNodeId(null);
    }
  }, [chain, selectedWo]);

  const finishedGoodsCount = useMemo(
    () => allocations.filter((a) => isFinishedGoodsSource(a.workOrderSource)).length,
    [allocations],
  );
  const componentCount = allocations.length - finishedGoodsCount;

  const strategyLabel =
    masterPlan?.strategyName ??
    CAPACITY_STRATEGY_OPTIONS.find((o) => o.value === masterPlan?.capacityStrategy)?.label ??
    '—';

  const planMeta = masterPlan ? (
    <span className="mp-meta">
      版本 <strong>{masterPlan.planVersionId}</strong> · 策略 {strategyLabel} · 得分 {masterPlan.score} · 耗时{' '}
      {masterPlan.solveDurationMs} ms · 分配 {allocations.length} 条 · 成品 {finishedGoodsCount} · 组件 {componentCount}
    </span>
  ) : null;

  return (
    <div className="master-plan-page">
      <PageHeader
        title="S04 主计划"
        description="Timefold 瓶颈资源槽位分配 · 工单满足链与产能甘特"
        actions={
          <>
            <input
              className="input"
              placeholder="版本号查询"
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
            />
            <button type="button" className="btn" onClick={() => void loadVersion()} disabled={loading}>
              加载
            </button>
            <label className="mp-strategy-select">
              <span>主计划策略</span>
              <select
                className="input"
                value={selectedStrategyId}
                onChange={(e) => setSelectedStrategyId(e.target.value)}
                disabled={loading || strategies.length === 0}
              >
                {strategies.length === 0 ? (
                  <option value="">暂无策略</option>
                ) : (
                  strategies.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))
                )}
              </select>
            </label>
            <button type="button" className="btn primary" onClick={() => void solve()} disabled={loading || !selectedStrategyId}>
              求解主计划
            </button>
          </>
        }
      />
      <StatusBanner loading={loading} error={error} />
      {masterPlan && <div className="meta-row">{planMeta}</div>}

      <div className="master-plan-layout">
        <aside className="mp-kpi-panel card">
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
            {kpis.length === 0 && <p className="empty">请先求解主计划</p>}
          </div>
        </aside>

        <VerticalResizeSplit
          className="mp-right-split"
          storageKey="master-plan-split-ratio"
          minTopRatio={0.3}
          maxTopRatio={0.75}
          top={
            <section className="mp-wo-panel card">
              <div className="panel-head">
                <h3 className="panel-title">工单列表</h3>
                <div className="source-legend">
                  <span className="source-tag tag-external">成品工单</span>
                  <span className="source-tag tag-replenish">组件工单</span>
                </div>
              </div>
              <div className="panel-scroll wo-table-wrap">
                <FilterableTable
                  tableId="master-plan-allocations"
                  tableClassName="wo-select-table"
                  wrapClassName="ft-table-wrap"
                  rows={allocations}
                  rowKey={(row) => allocationKey(row)}
                  emptyText="请先求解主计划"
                  onRowClick={setSelectedWo}
                  getRowClassName={(row) =>
                    selectedWo && allocationKey(selectedWo) === allocationKey(row) ? 'selected' : ''
                  }
                  columns={[
                    {
                      key: 'source',
                      header: '来源',
                      render: (row) => {
                        const sourceClass = isFinishedGoodsSource(row.workOrderSource)
                          ? 'tag-external'
                          : 'tag-replenish';
                        return (
                          <span className={`source-tag ${sourceClass}`}>
                            {WORK_ORDER_SOURCE_LABEL[row.workOrderSource]}
                          </span>
                        );
                      },
                    },
                    {
                      key: 'workOrderNo',
                      header: '工单',
                      className: 'mono',
                      render: (row) => (
                        <>
                          {row.workOrderNo}
                          {row.segmentIndex > 0 ? (
                            <span className="segment-badge"> 段{row.segmentIndex + 1}</span>
                          ) : null}
                        </>
                      ),
                    },
                    { key: 'productCode', header: '产品', render: (row) => row.productCode },
                    { key: 'quantity', header: '数量', render: (row) => row.quantity },
                    {
                      key: 'salesOrder',
                      header: '销售订单',
                      render: (row) => `${row.salesOrderNo}-${row.salesOrderLineNo}`,
                    },
                    {
                      key: 'parentWorkOrderNo',
                      header: '父工单',
                      className: 'mono muted',
                      render: (row) => row.parentWorkOrderNo ?? '—',
                    },
                    { key: 'resourceId', header: '资源', render: (row) => row.resourceId },
                    {
                      key: 'plannedStartTs',
                      header: '计划开始',
                      render: (row) => fmtDateTime(row.plannedStartTs),
                    },
                    {
                      key: 'plannedEndTs',
                      header: '计划结束',
                      render: (row) => fmtDateTime(row.plannedEndTs),
                    },
                    { key: 'durationMinutes', header: '负荷(分)', render: (row) => row.durationMinutes },
                  ]}
                />
              </div>
            </section>
          }
          bottom={
            <section className="mp-detail-panel card">
              <div className="panel-head">
                <div className="mp-tabs" role="tablist">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={tab === 'fulfillment'}
                    className={`mp-tab ${tab === 'fulfillment' ? 'is-active' : ''}`}
                    onClick={() => setTab('fulfillment')}
                  >
                    满足链与供给链
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={tab === 'capacity'}
                    className={`mp-tab ${tab === 'capacity' ? 'is-active' : ''}`}
                    onClick={() => setTab('capacity')}
                  >
                    产能甘特图
                  </button>
                </div>
                {selectedWo && (
                  <div className="mp-tab-meta">
                    <span className="mp-tab-meta-wo mono">{selectedWo.workOrderNo}</span>
                    <span className={`source-tag ${isFinishedGoodsSource(selectedWo.workOrderSource) ? 'tag-external' : 'tag-replenish'}`}>
                      {WORK_ORDER_SOURCE_LABEL[selectedWo.workOrderSource]}
                    </span>
                    <span className="mp-tab-meta-sub">
                      {selectedWo.productCode} ×{selectedWo.quantity} · {selectedWo.resourceId}
                    </span>
                  </div>
                )}
              </div>

              {!selectedWo ? (
                <div className="panel-scroll mp-empty">
                  <p className="empty">请在上方工单列表中选择一个工单</p>
                </div>
              ) : tab === 'fulfillment' ? (
                <div className="mp-fulfillment-host">
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
                  <div className="chain-sync-host">
                    <FulfillmentChainSyncView
                      nodes={chain?.nodes ?? []}
                      edges={chain?.edges ?? []}
                      tasks={chainTasks}
                      onTasksChange={setChainTasks}
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
                </div>
              ) : (
                <div className="mp-capacity-host">
                  {capacityLoading && <p className="empty">加载中…</p>}
                  {!capacityLoading && <WorkOrderCapacityGantt data={capacityGantt} />}
                </div>
              )}
            </section>
          }
        />
      </div>
    </div>
  );
}
