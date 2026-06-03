import { useCallback, useEffect, useMemo, useState, type MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { DemandOrderContextMenu } from '../components/DemandOrderContextMenu';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { useScheduleVersion } from '../context/ScheduleVersionContext';
import type {
  BatchPlanWorkOrder,
  ProductionBatch,
  WorkOrderRoutingDetail,
  WorkOrderRoutingOperation,
} from '../types/api';
import '../components/DemandOrderContextMenu.css';
import './ProductionPlanPage.css';

const BATCH_STATUS_LABEL: Record<string, string> = {
  NONE: '未拆批',
  SPLIT: '已拆完',
  PARTIAL: '部分拆批',
};

const KITTING_STATUS_LABEL: Record<string, string> = {
  KITTED: '齐套',
  SHORT: '缺料',
  UNKNOWN: '未评估',
};

const SPLIT_METHOD_LABEL: Record<string, string> = {
  MANUAL: '手工',
  FIXED: '固定量',
  KITTING: '齐套',
  AUTO: '自动',
  WHOLE: '整单',
};

export function BatchPlanPage() {
  useScheduleVersion();
  const [workOrders, setWorkOrders] = useState<BatchPlanWorkOrder[]>([]);
  const [activeWo, setActiveWo] = useState<BatchPlanWorkOrder | null>(null);
  const [batches, setBatches] = useState<ProductionBatch[]>([]);
  const [activeBatch, setActiveBatch] = useState<ProductionBatch | null>(null);
  const [routing, setRouting] = useState<WorkOrderRoutingDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [routingLoading, setRoutingLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    row: BatchPlanWorkOrder;
  } | null>(null);
  const [activeOp, setActiveOp] = useState<WorkOrderRoutingOperation | null>(null);

  const loadWorkOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const rows = await api.schedulingBatches.listWorkOrders();
      setWorkOrders(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载工单失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadBatches = useCallback(async (workOrderNo: string) => {
    try {
      setBatches(await api.schedulingBatches.listByWorkOrder(workOrderNo));
    } catch (e) {
      setBatches([]);
      setError(e instanceof Error ? e.message : '加载批次失败');
    }
  }, []);

  useEffect(() => {
    void loadWorkOrders();
  }, [loadWorkOrders]);

  useEffect(() => {
    if (workOrders.length === 0) {
      setActiveWo(null);
      return;
    }
    setActiveWo((prev) => {
      if (prev) {
        const match = workOrders.find((w) => w.workOrderNo === prev.workOrderNo);
        if (match) return match;
      }
      return workOrders[0];
    });
  }, [workOrders]);

  useEffect(() => {
    if (!activeWo) {
      setBatches([]);
      setActiveBatch(null);
      return;
    }
    void loadBatches(activeWo.workOrderNo);
  }, [activeWo, loadBatches]);

  useEffect(() => {
    if (batches.length === 0) {
      setActiveBatch(null);
      return;
    }
    setActiveBatch((prev) => {
      if (prev) {
        const match = batches.find((b) => b.batchNo === prev.batchNo);
        if (match) return match;
      }
      return batches[0];
    });
  }, [batches]);

  useEffect(() => {
    if (!activeBatch) {
      setRouting(null);
      setActiveOp(null);
      return;
    }
    let cancelled = false;
    setRoutingLoading(true);
    void api.schedulingBatches
      .routing(activeBatch.batchNo)
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
          setError(e instanceof Error ? e.message : '工艺加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setRoutingLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeBatch]);

  const refreshAfterAction = async (workOrderNo: string) => {
    const [rows, batchRows] = await Promise.all([
      api.schedulingBatches.listWorkOrders(),
      api.schedulingBatches.listByWorkOrder(workOrderNo),
    ]);
    setWorkOrders(rows);
    setActiveWo(rows.find((w) => w.workOrderNo === workOrderNo) ?? rows[0] ?? null);
    setBatches(batchRows);
  };

  const runAutoSplitAll = async () => {
    const eligible = workOrders.filter((w) => w.remainingQuantity > 0);
    if (eligible.length === 0) {
      setError('没有剩余可拆量的工单');
      return;
    }
    if (
      !window.confirm(
        `按「计划参数 · 批次拆解」当前策略，对 ${eligible.length} 张工单执行自动拆批？\n已拆完或无剩余量的工单将跳过。`,
      )
    ) {
      return;
    }
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.schedulingBatches.autoSplitAll();
      await loadWorkOrders();
      if (activeWo) {
        await loadBatches(activeWo.workOrderNo);
      }
      const failHint =
        result.failures.length > 0 ? `；${result.failures.length} 张失败` : '';
      setSuccess(
        `自动拆批完成：成功 ${result.succeeded}/${result.attempted}，跳过 ${result.skipped}${failHint}`,
      );
      if (result.failures.length > 0) {
        setError(result.failures.slice(0, 5).join('\n'));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '批量自动拆批失败');
    } finally {
      setActionLoading(false);
    }
  };

  const eligibleAutoSplitCount = useMemo(
    () => workOrders.filter((w) => w.remainingQuantity > 0).length,
    [workOrders],
  );

  const runAutoSplit = async (wo: BatchPlanWorkOrder) => {
    if (!window.confirm(`按当前策略对工单 ${wo.workOrderNo} 自动拆批？`)) return;
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await api.schedulingBatches.autoSplit(wo.workOrderNo);
      await refreshAfterAction(wo.workOrderNo);
      setSuccess(`工单 ${wo.workOrderNo} 自动拆批完成`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '自动拆批失败');
    } finally {
      setActionLoading(false);
    }
  };

  const runManualSplit = async (wo: BatchPlanWorkOrder) => {
    const raw = window.prompt(
      `手工创建批次 — 工单 ${wo.workOrderNo}\n剩余可拆量：${wo.remainingQuantity}\n请输入批次数量：`,
      String(Math.min(wo.remainingQuantity, wo.remainingQuantity > 0 ? wo.remainingQuantity : 1)),
    );
    if (raw == null || raw.trim() === '') return;
    const qty = Number.parseFloat(raw);
    if (!Number.isFinite(qty) || qty <= 0) {
      setError('批次数量无效');
      return;
    }
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await api.schedulingBatches.manualSplit(wo.workOrderNo, qty);
      await refreshAfterAction(wo.workOrderNo);
      setSuccess(`已创建批次，数量 ${qty}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '手工建批失败');
    } finally {
      setActionLoading(false);
    }
  };

  const runCancelAll = async (wo: BatchPlanWorkOrder) => {
    if (!window.confirm(`取消工单 ${wo.workOrderNo} 的全部批次？`)) return;
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await api.schedulingBatches.cancel({ workOrderNo: wo.workOrderNo, cancelAll: true });
      await refreshAfterAction(wo.workOrderNo);
      setSuccess(`已取消工单 ${wo.workOrderNo} 的全部批次`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '取消批次失败');
    } finally {
      setActionLoading(false);
    }
  };

  const runRefreshKitting = async (wo: BatchPlanWorkOrder) => {
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await api.schedulingBatches.refreshKitting(wo.workOrderNo);
      await refreshAfterAction(wo.workOrderNo);
      setSuccess(`已刷新工单 ${wo.workOrderNo} 的批次齐套状态`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '刷新齐套状态失败');
    } finally {
      setActionLoading(false);
    }
  };

  const woColumns = useMemo(
    () => [
      { key: 'wo', header: '工单', className: 'mono', render: (r: BatchPlanWorkOrder) => r.workOrderNo },
      { key: 'product', header: '产品', className: 'mono', render: (r: BatchPlanWorkOrder) => r.productCode },
      { key: 'qty', header: '总量', render: (r: BatchPlanWorkOrder) => r.quantity },
      { key: 'batched', header: '已拆', render: (r: BatchPlanWorkOrder) => r.batchedQuantity },
      { key: 'remain', header: '剩余', render: (r: BatchPlanWorkOrder) => r.remainingQuantity },
      {
        key: 'status',
        header: '拆批状态',
        render: (r: BatchPlanWorkOrder) => BATCH_STATUS_LABEL[r.batchSplitStatus] ?? r.batchSplitStatus,
      },
    ],
    [],
  );

  const batchColumns = useMemo(
    () => [
      { key: 'batch', header: '批次', className: 'mono', render: (r: ProductionBatch) => r.batchNo },
      { key: 'seq', header: '序号', render: (r: ProductionBatch) => r.batchSeq },
      { key: 'qty', header: '数量', render: (r: ProductionBatch) => r.quantity },
      {
        key: 'kitting',
        header: '齐套',
        render: (r: ProductionBatch) => KITTING_STATUS_LABEL[r.kittingStatus] ?? r.kittingStatus,
      },
      {
        key: 'method',
        header: '方式',
        render: (r: ProductionBatch) => SPLIT_METHOD_LABEL[r.splitMethod] ?? r.splitMethod,
      },
    ],
    [],
  );

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

  return (
    <div className="production-plan-page">
      <PageHeader
        title="批次计划"
        showScheduleVersionSelector
        description="对已下发工单拆批；拆批后仅批次进入生产排程（S05）。与 MRP 无关。"
      />
      <StatusBanner loading={loading || actionLoading || routingLoading} error={error} success={success} />

      <div className="pp-toolbar card">
        <div className="pp-toolbar-actions">
          <button
            type="button"
            className="btn btn-primary"
            disabled={loading || actionLoading || eligibleAutoSplitCount === 0}
            onClick={() => void runAutoSplitAll()}
          >
            自动拆批{eligibleAutoSplitCount > 0 ? ` (${eligibleAutoSplitCount})` : ''}
          </button>
          <button type="button" className="btn" disabled={loading} onClick={() => void loadWorkOrders()}>
            刷新
          </button>
          {activeWo && (
            <button
              type="button"
              className="btn"
              disabled={loading || actionLoading || activeWo.batchedQuantity <= 0}
              onClick={() => void runRefreshKitting(activeWo)}
            >
              刷新批次齐套
            </button>
          )}
        </div>
        <p className="pp-hint">
          在 <Link to="/scheduling/parameters">计划参数 · 批次拆解</Link> 配置策略；「自动拆批」按当前策略批量处理所有可拆工单，也可右键单张工单操作。
        </p>
      </div>

      {workOrders.length === 0 && !loading ? (
        <section className="card pp-chain-empty">
          <p className="muted-text">暂无已下发工单，请先在生产工单页面下发。</p>
        </section>
      ) : (
        <HorizontalResizeSplit
          storageKey="batch-plan-main-split"
          minLeftRatio={0.28}
          maxLeftRatio={0.55}
          left={
            <section className="card pp-wo-panel">
              <h3 className="panel-title">生产工单</h3>
              <div className="pp-panel-scroll pp-table-wrap">
                <FilterableTable
                  tableId="batch-plan-work-orders"
                  tableClassName="pp-table data-table"
                  wrapClassName="ft-table-wrap"
                  rows={workOrders}
                  rowKey={(r) => r.workOrderNo}
                  columns={woColumns}
                  getRowClassName={(r) =>
                    activeWo?.workOrderNo === r.workOrderNo ? 'active' : ''
                  }
                  onRowClick={setActiveWo}
                  getRowProps={(row) => ({
                    onContextMenu: (e: MouseEvent) => {
                      e.preventDefault();
                      setContextMenu({ x: e.clientX, y: e.clientY, row });
                    },
                  })}
                />
              </div>
            </section>
          }
          right={
            <VerticalResizeSplit
              storageKey="batch-plan-right-split"
              minTopRatio={0.25}
              maxTopRatio={0.6}
              top={
                <section className="card pp-wo-panel">
                  <h3 className="panel-title">
                    {activeWo ? `批次 · ${activeWo.workOrderNo}` : '批次'}
                  </h3>
                  {!activeWo ? (
                    <p className="muted-text">请选择工单</p>
                  ) : batches.length === 0 ? (
                    <p className="muted-text">暂无批次，请右键工单拆批</p>
                  ) : (
                    <div className="pp-panel-scroll pp-table-wrap">
                      <FilterableTable
                        tableId="batch-plan-batches"
                        tableClassName="pp-table data-table"
                        wrapClassName="ft-table-wrap"
                        rows={batches}
                        rowKey={(r) => r.batchNo}
                        columns={batchColumns}
                        getRowClassName={(r) =>
                          activeBatch?.batchNo === r.batchNo ? 'active' : ''
                        }
                        onRowClick={setActiveBatch}
                      />
                    </div>
                  )}
                </section>
              }
              bottom={
                <section className="card pp-chain-panel">
                  <div className="pp-panel-head">
                    <h3 className="panel-title">
                      {activeBatch
                        ? `工艺 · ${activeBatch.batchNo}（${activeBatch.quantity}）`
                        : '工艺与设备'}
                    </h3>
                  </div>
                  <HorizontalResizeSplit
                    storageKey="batch-plan-routing-split"
                    minLeftRatio={0.28}
                    maxLeftRatio={0.62}
                    left={
                      <div className="pp-sub-panel">
                        <h4 className="panel-title">工序</h4>
                        {!routing || routing.operations.length === 0 ? (
                          <p className="muted-text">请选择批次</p>
                        ) : (
                          <FilterableTable
                            tableId="batch-plan-operations"
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
                        )}
                      </div>
                    }
                    right={
                      <div className="pp-sub-panel">
                        <h4 className="panel-title">
                          可用产线{activeOp ? ` · ${activeOp.operationName}` : ''}
                        </h4>
                        {lineRows.length === 0 ? (
                          <p className="muted-text">无产线数据</p>
                        ) : (
                          <FilterableTable
                            tableId="batch-plan-lines"
                            tableClassName="pp-table data-table"
                            wrapClassName="ft-table-wrap"
                            rows={lineRows}
                            rowKey={(r) => r.key}
                            columns={lineColumns}
                          />
                        )}
                      </div>
                    }
                  />
                </section>
              }
            />
          }
        />
      )}

      {contextMenu && (
        <DemandOrderContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          onClose={() => setContextMenu(null)}
          items={[
            {
              id: 'auto',
              label: '自动拆批',
              disabled: contextMenu.row.remainingQuantity <= 0,
              onSelect: () => void runAutoSplit(contextMenu.row),
            },
            {
              id: 'manual',
              label: '手工创建批次',
              disabled: contextMenu.row.remainingQuantity <= 0,
              onSelect: () => void runManualSplit(contextMenu.row),
            },
            {
              id: 'refresh-kitting',
              label: '刷新批次齐套',
              disabled: contextMenu.row.batchedQuantity <= 0,
              onSelect: () => void runRefreshKitting(contextMenu.row),
            },
            {
              id: 'cancel',
              label: '取消全部批次',
              disabled: contextMenu.row.batchedQuantity <= 0,
              onSelect: () => void runCancelAll(contextMenu.row),
            },
          ]}
        />
      )}
    </div>
  );
}
