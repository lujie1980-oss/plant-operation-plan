import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { PageHeader } from '../components/PageHeader';
import { PendingWorkOrderTable } from '../components/PendingWorkOrderTable';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { MachineScheduleGantt } from '../components/MachineScheduleGantt';
import { useScheduleVersion } from '../context/ScheduleVersionContext';
import type {
  DetailScheduleOperation,
  ProductionBatch,
  WorkOrder,
  WorkOrderRoutingDetail,
  WorkOrderRoutingOperation,
} from '../types/api';
import './ProductionPlanPage.css';

type BottomTab = 'routing' | 'detailSchedule';

export function PendingScheduleWorkOrdersPage() {
  const { activeVersionId } = useScheduleVersion();
  const [rows, setRows] = useState<WorkOrder[]>([]);
  const [activeWo, setActiveWo] = useState<WorkOrder | null>(null);
  const [activeOp, setActiveOp] = useState<WorkOrderRoutingOperation | null>(null);
  const [routing, setRouting] = useState<WorkOrderRoutingDetail | null>(null);
  const [batches, setBatches] = useState<ProductionBatch[]>([]);
  const [scheduledOps, setScheduledOps] = useState<DetailScheduleOperation[]>([]);
  const [bottomTab, setBottomTab] = useState<BottomTab>('routing');
  const [loading, setLoading] = useState(false);
  const [routingLoading, setRoutingLoading] = useState(false);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (detailScheduleVersionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.workOrders.listDispatched(detailScheduleVersionId ?? undefined));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(activeVersionId);
  }, [activeVersionId, load]);

  useEffect(() => {
    if (rows.length === 0) {
      setActiveWo(null);
      return;
    }
    setActiveWo((prev) => {
      if (prev) {
        const match = rows.find((r) => r.workOrderNo === prev.workOrderNo);
        if (match) return match;
      }
      return rows[0];
    });
  }, [rows]);

  useEffect(() => {
    if (!activeWo) {
      setRouting(null);
      setActiveOp(null);
      return;
    }
    let cancelled = false;
    setRoutingLoading(true);
    void api.workOrders
      .routingDetail(activeWo.workOrderNo)
      .then((detail) => {
        if (!cancelled) {
          setRouting(detail);
          setActiveOp(detail.operations[0] ?? null);
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setRouting(null);
          setActiveOp(null);
          setError(e instanceof Error ? e.message : '工艺路径加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setRoutingLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeWo]);

  useEffect(() => {
    if (!activeWo) {
      setBatches([]);
      setScheduledOps([]);
      return;
    }
    let cancelled = false;
    setScheduleLoading(true);
    const batchPromise = api.schedulingBatches.listByWorkOrder(activeWo.workOrderNo);
    const schedulePromise = activeVersionId
      ? api.getDetailSchedule(activeVersionId)
      : Promise.resolve(null);

    void Promise.all([batchPromise, schedulePromise])
      .then(([batchRows, scheduleResult]) => {
        if (cancelled) return;
        setBatches(batchRows);
        setScheduledOps(
          scheduleResult?.operations.filter((op) => op.workOrderNo === activeWo.workOrderNo) ?? [],
        );
      })
      .catch((e) => {
        if (!cancelled) {
          setBatches([]);
          setScheduledOps([]);
          setError(e instanceof Error ? e.message : '批次或排程加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setScheduleLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeWo, activeVersionId]);

  const batchFilteredOperations = useMemo(() => {
    if (scheduledOps.length === 0) return [];
    const batchNos = new Set(batches.map((b) => b.batchNo));
    if (batchNos.size === 0) return scheduledOps;
    return scheduledOps.filter((op) => op.batchNo && batchNos.has(op.batchNo));
  }, [scheduledOps, batches]);

  const sortedGanttOperations = useMemo(
    () =>
      [...batchFilteredOperations].sort((a, b) => {
        const lineCmp = (a.lineId ?? '').localeCompare(b.lineId ?? '', 'zh-CN');
        if (lineCmp !== 0) return lineCmp;
        const startCmp = (a.startMinute ?? 0) - (b.startMinute ?? 0);
        if (startCmp !== 0) return startCmp;
        return (a.sequenceIndex ?? 0) - (b.sequenceIndex ?? 0);
      }),
    [batchFilteredOperations],
  );

  const updateEligible = async (workOrderNo: string, eligible: boolean) => {
    setError(null);
    try {
      const updated = await api.workOrders.updatePendingScheduleEligible(workOrderNo, eligible);
      setRows((prev) => prev.map((r) => (r.workOrderNo === workOrderNo ? { ...r, ...updated } : r)));
      setActiveWo((prev) => (prev?.workOrderNo === workOrderNo ? { ...prev, ...updated } : prev));
    } catch (e) {
      setError(e instanceof Error ? e.message : '更新待排状态失败');
    }
  };

  const scheduledCount = rows.filter((r) => r.detailScheduled).length;
  const eligibleCount = rows.filter((r) => r.pendingScheduleEligible !== false).length;

  const operationColumns = useMemo(
    () => [
      { key: 'seq', header: '序号', render: (op: WorkOrderRoutingOperation) => op.sequenceNo },
      { key: 'name', header: '工序', render: (op: WorkOrderRoutingOperation) => op.operationName },
      {
        key: 'resources',
        header: '可选资源数',
        render: (op: WorkOrderRoutingOperation) => op.resourceOptions.length,
      },
    ],
    [],
  );

  const lineRows = useMemo(() => {
    if (!activeOp) return [];
    const byLine = new Map<
      string,
      Array<{ resourceId: string; priority: number; durationMinutes: number }>
    >();
    for (const opt of activeOp.resourceOptions) {
      for (const lineId of opt.allowedLineIds) {
        const list = byLine.get(lineId) ?? [];
        list.push({
          resourceId: opt.resourceId,
          priority: opt.resourcePriority,
          durationMinutes: opt.durationMinutes,
        });
        byLine.set(lineId, list);
      }
    }
    return [...byLine.entries()]
      .sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
      .map(([lineId, resources]) => {
        const sorted = [...resources].sort((a, b) => a.priority - b.priority);
        const primary = sorted[0];
        return {
          key: lineId,
          lineId,
          resourceIds: sorted.map((r) => r.resourceId).join(', '),
          priority: primary?.priority ?? 0,
          durationMinutes: primary?.durationMinutes ?? 0,
        };
      });
  }, [activeOp]);

  const lineColumns = useMemo(
    () => [
      { key: 'lineId', header: '产线', className: 'mono', render: (r: (typeof lineRows)[0]) => r.lineId },
      {
        key: 'resourceIds',
        header: '资源',
        className: 'mono',
        render: (r: (typeof lineRows)[0]) => r.resourceIds,
      },
      { key: 'priority', header: '优先级', render: (r: (typeof lineRows)[0]) => r.priority },
      { key: 'duration', header: '工时(分)', render: (r: (typeof lineRows)[0]) => r.durationMinutes },
    ],
    [],
  );

  const bottomBusy = routingLoading || scheduleLoading;

  return (
    <div className="production-plan-page">
      <PageHeader
        title="待排工单"
        showScheduleVersionSelector
        description="已下发工单待细排；可标记可排/不可排，并查看工艺路径与批次排程。已排产状态随顶部排程版本变化。"
      />
      <StatusBanner loading={loading || bottomBusy} error={error} />

      <div className="pp-toolbar card">
        <div className="pp-filters">
          <span className="pp-stat">
            已下发 <strong>{rows.length}</strong>
          </span>
          <span className="pp-filter-sep" aria-hidden />
          <span className="pp-stat">
            已排产 <strong>{scheduledCount}</strong>
          </span>
          <span className="pp-filter-sep" aria-hidden />
          <span className="pp-stat">
            可排产 <strong>{eligibleCount}</strong>
          </span>
        </div>
        <div className="pp-toolbar-actions">
          <button type="button" className="btn" disabled={loading} onClick={() => void load(activeVersionId)}>
            刷新
          </button>
        </div>
        <p className="pp-hint">
          在 <Link to="/master-plan/analysis/work-orders">生产工单</Link> 下发后进入本页；标记「不可排产」的工单不会进入{' '}
          <Link to="/scheduling/detail-schedule">生产排程</Link> 求解。批次拆分见{' '}
          <Link to="/scheduling/batch-plan">批次计划</Link>。
        </p>
      </div>

      {rows.length === 0 && !loading ? (
        <section className="card pp-chain-empty">
          <p className="muted-text">暂无已下发工单，请先在生产工单页面下发。</p>
        </section>
      ) : (
        <VerticalResizeSplit
          className="pp-split"
          storageKey="pending-schedule-split-ratio"
          minTopRatio={0.28}
          maxTopRatio={0.65}
          top={
            <section className="card pp-wo-panel">
              <div className="pp-panel-head">
                <h3 className="panel-title">待排工单列表</h3>
                <div className="pp-source-legend">
                  <span className="tag tag-scheduled">● 已排产</span>
                  <span className="tag tag-unscheduled">○ 未排产</span>
                </div>
              </div>
              <div className="pp-panel-scroll pp-table-wrap">
                <PendingWorkOrderTable
                  tableId="pending-schedule-work-orders"
                  rows={rows}
                  activeWorkOrderNo={activeWo?.workOrderNo ?? null}
                  loading={loading}
                  onSelect={setActiveWo}
                  onPendingScheduleEligibleChange={(woNo, eligible) => void updateEligible(woNo, eligible)}
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
                      {activeWo.productCode} × {activeWo.quantity}
                      {bottomBusy && ' · 加载中…'}
                    </span>
                  )}
                </div>
                <div className="pp-detail-tabs" role="tablist">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={bottomTab === 'routing'}
                    className={bottomTab === 'routing' ? 'pp-tab active' : 'pp-tab'}
                    onClick={() => setBottomTab('routing')}
                  >
                    工艺路径
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={bottomTab === 'detailSchedule'}
                    className={bottomTab === 'detailSchedule' ? 'pp-tab active' : 'pp-tab'}
                    onClick={() => setBottomTab('detailSchedule')}
                  >
                    详细排程
                  </button>
                </div>
              </div>

              {!activeWo ? (
                <p className="muted-text pp-bottom-empty">请选择工单</p>
              ) : bottomTab === 'routing' ? (
                <div className="pp-nested-split-host">
                  <HorizontalResizeSplit
                    storageKey="pending-schedule-bottom-split"
                    minLeftRatio={0.28}
                    maxLeftRatio={0.62}
                    left={
                      <div className="pp-sub-panel">
                        <h4 className="panel-title">工序顺序</h4>
                        {!routing || routing.operations.length === 0 ? (
                          <p className="muted-text">无工艺路径</p>
                        ) : (
                          <div className="pp-panel-scroll pp-table-wrap">
                            <FilterableTable
                              tableId="pending-schedule-operations"
                              tableClassName="pp-table data-table"
                              wrapClassName="ft-table-wrap"
                              rows={routing.operations}
                              rowKey={(op) => String(op.sequenceNo)}
                              columns={operationColumns}
                              getRowClassName={(op) =>
                                activeOp?.sequenceNo === op.sequenceNo ? 'active' : ''
                              }
                              onRowClick={setActiveOp}
                            />
                          </div>
                        )}
                      </div>
                    }
                    right={
                      <div className="pp-sub-panel">
                        <h4 className="panel-title">
                          可选产线{activeOp ? ` · ${activeOp.operationName}` : ''}
                        </h4>
                        {!activeOp ? (
                          <p className="muted-text">请选择工序</p>
                        ) : (
                          <div className="pp-panel-scroll pp-table-wrap">
                            <FilterableTable
                              tableId="pending-schedule-lines"
                              tableClassName="pp-table data-table"
                              wrapClassName="ft-table-wrap"
                              rows={lineRows}
                              rowKey={(r) => r.key}
                              columns={lineColumns}
                              emptyText="该工序无可选产线"
                            />
                          </div>
                        )}
                      </div>
                    }
                  />
                </div>
              ) : (
                <div className="pp-schedule-host">
                  {!activeVersionId ? (
                    <p className="muted-text ms-gantt-empty">
                      请在页头选择排程版本，或前往{' '}
                      <Link to="/scheduling/detail-schedule">生产排程</Link> 运行求解。
                    </p>
                  ) : batches.length === 0 ? (
                    <p className="muted-text ms-gantt-empty">
                      该工单尚未拆批。请先在{' '}
                      <Link to="/scheduling/batch-plan">批次计划</Link> 拆批后再查看排程。
                    </p>
                  ) : (
                    <MachineScheduleGantt
                      operations={sortedGanttOperations}
                      className="pp-gantt-panel"
                    />
                  )}
                </div>
              )}
            </section>
          }
        />
      )}
    </div>
  );
}
