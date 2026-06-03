import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { AssignLineDialog } from '../components/AssignLineDialog';
import { BatchOperationListPanel } from '../components/BatchOperationListPanel';
import { DetailScheduleKpiPanel } from '../components/DetailScheduleKpiPanel';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { MachineScheduleGantt } from '../components/MachineScheduleGantt';
import { PendingScheduleBatchList } from '../components/PendingScheduleBatchList';
import { ProductionTaskPanel } from '../components/ProductionTaskPanel';
import { ScheduleViolationsPanel } from '../components/ScheduleViolationsPanel';
import { PlanningDiagnosticsPanel } from '../components/PlanningDiagnosticsPanel';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { usePlan } from '../context/PlanContext';
import { useScheduleVersion } from '../context/ScheduleVersionContext';
import { useScheduleSession } from '../hooks/useScheduleSession';
import type { ProductionBatchKitting, ScheduleFeedback } from '../types/api';
import type { ProductionTask } from '../types/scheduleSession';
import { enrichOperationsForGantt } from '../utils/ganttTaskDisplay';
import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';
import type { SessionStepPatch } from '../types/scheduleSession';
import type { GanttDragCommit } from '../utils/ganttDragDrop';
import { buildMachineScheduleModel } from '../utils/machineScheduleModel';
import { previewOperationsToGantt } from '../utils/previewOperationsToGantt';
import {
  buildBatchPatches,
  buildOperationPatch,
  buildUnassignPatches,
  pickBestLineForEarliest,
  resolveLineForBatchSchedule,
} from '../utils/scheduleSessionInsert';
import './ProductionPlanPage.css';
import './DetailSchedulePage.css';

type RightBottomTab = 'editGantt' | 'lineTasks';

type AssignDialogState =
  | {
      kind: 'batch';
      batch: ProductionBatchKitting;
      operations: DetailSchedulePlanningPreviewOperation[];
      anchorOperationId: string;
    }
  | {
      kind: 'operation';
      operation: DetailSchedulePlanningPreviewOperation;
    };

export function DetailSchedulePage() {
  const { activePlanVersionId, setDetailSchedule, setMasterPlan } = usePlan();
  const { viewHistory, registerNewVersion, activeVersionId } = useScheduleVersion();
  const {
    session,
    preview,
    loading: sessionLoading,
    simulating,
    confirming,
    error: sessionError,
    createSession,
    simulateFull,
    simulateIncremental,
    confirm,
    optimize,
    hasSession,
    simulateMeta,
  } = useScheduleSession(activePlanVersionId);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [refreshMasterPlan, setRefreshMasterPlan] = useState(true);
  const [feedbackCutoff, setFeedbackCutoff] = useState(() => new Date().toISOString().slice(0, 10));
  const [showDsDiagnostics, setShowDsDiagnostics] = useState(false);
  const [selectedBatch, setSelectedBatch] = useState<ProductionBatchKitting | null>(null);
  const [selectedOperationId, setSelectedOperationId] = useState<string | null>(null);
  const [assignDialog, setAssignDialog] = useState<AssignDialogState | null>(null);
  const [rightBottomTab, setRightBottomTab] = useState<RightBottomTab>('editGantt');
  const [lineFilter, setLineFilter] = useState('');
  const [productionTasks, setProductionTasks] = useState<ProductionTask[]>([]);
  const [scheduleFeedback, setScheduleFeedback] = useState<ScheduleFeedback[]>([]);
  const sessionBootstrapped = useRef(false);

  const viewingHistorical = viewHistory;

  useEffect(() => {
    let cancelled = false;
    void api
      .listProductionTasks()
      .then((tasks) => {
        if (!cancelled) setProductionTasks(tasks);
      })
      .catch(() => {
        if (!cancelled) setProductionTasks([]);
      });
    return () => {
      cancelled = true;
    };
  }, [preview?.computedAt, session?.sessionId]);

  useEffect(() => {
    let cancelled = false;
    if (!activeVersionId) {
      setScheduleFeedback([]);
      return () => {
        cancelled = true;
      };
    }
    void api
      .listScheduleFeedback({ detailScheduleVersionId: activeVersionId })
      .then((rows) => {
        if (!cancelled) setScheduleFeedback(rows);
      })
      .catch(() => {
        if (!cancelled) setScheduleFeedback([]);
      });
    return () => {
      cancelled = true;
    };
  }, [activeVersionId, preview?.computedAt]);

  const allGanttOps = useMemo(
    () =>
      enrichOperationsForGantt(
        previewOperationsToGantt(preview?.operations),
        productionTasks,
        scheduleFeedback,
      ),
    [preview, productionTasks, scheduleFeedback],
  );

  const scheduledPreviewOps = useMemo(
    () =>
      (preview?.operations ?? []).filter((op) => op.scheduled || op.lineId),
    [preview],
  );

  const ganttModel = useMemo(
    () => buildMachineScheduleModel(allGanttOps),
    [allGanttOps],
  );

  const lineTasks = useCallback(
    (lineId: string) => ganttModel?.rows.find((r) => r.lineId === lineId)?.tasks ?? [],
    [ganttModel],
  );

  const batchOperations = useCallback(
    (batchNo: string) => {
      if (!preview?.operations) return [];
      return preview.operations
        .filter((op) => op.batchNo === batchNo)
        .sort((a, b) => a.operationSeq - b.operationSeq);
    },
    [preview],
  );

  const applyPatches = useCallback(
    async (patches: SessionStepPatch[]) => {
      if (patches.length === 0) {
        setError('没有可排产的工序');
        return;
      }
      const result = await simulateIncremental(patches);
      if (result) {
        setSuccess(`已增量推演 ${patches.length} 道工序`);
      }
    },
    [simulateIncremental],
  );

  const scheduleOnLine = useCallback(
    async (
      operations: DetailSchedulePlanningPreviewOperation[],
      lineId: string,
      dropMinute?: number,
    ) => {
      const patches = buildBatchPatches(operations, lineId, lineTasks(lineId), dropMinute);
      await applyPatches(patches);
    },
    [applyPatches, lineTasks],
  );

  const resolveAutoLineId = useCallback(
    async (op: DetailSchedulePlanningPreviewOperation): Promise<string | null> => {
      if (op.lineId) return op.lineId;
      const sessionId = session?.sessionId;
      if (!sessionId) return null;
      try {
        const candidates = await api.scheduleSessionCandidateLines(
          sessionId,
          op.operationId,
        );
        return pickBestLineForEarliest(
          candidates,
          op.earliestStartMinute ?? 0,
          lineTasks,
          op.resourceId,
        );
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : String(e));
        return null;
      }
    },
    [session?.sessionId, lineTasks],
  );

  const handleCancelAllPlans = useCallback(async () => {
    if (!hasSession) {
      setError('请先创建 Session');
      return;
    }
    const patches = buildUnassignPatches(scheduledPreviewOps);
    if (patches.length === 0) {
      setError('当前 Session 暂无已排产计划');
      return;
    }
    if (
      !window.confirm(
        `确认取消当前 Session 的全部排产计划？\n将从产线队列移除 ${patches.length} 道工序并重新推演。`,
      )
    ) {
      return;
    }
    setError(null);
    const result = await simulateIncremental(patches);
    if (result) {
      setSuccess(`已取消全部计划（${patches.length} 道工序）`);
    }
  }, [hasSession, scheduledPreviewOps, simulateIncremental]);

  const handleCancelBatchPlan = useCallback(
    async (batch: ProductionBatchKitting) => {
      if (!hasSession) {
        setError('请先创建 Session');
        return;
      }
      const ops = batchOperations(batch.batchNo);
      const patches = buildUnassignPatches(ops);
      if (patches.length === 0) {
        setError('该批次暂无已排计划');
        return;
      }
      if (
        !window.confirm(
          `确认取消批次 ${batch.batchNo} 的排产计划？\n将从产线队列移除 ${patches.length} 道工序并重新推演。`,
        )
      ) {
        return;
      }
      setError(null);
      const result = await simulateIncremental(patches);
      if (result) {
        setSuccess(`已取消计划：${batch.batchNo}（${patches.length} 道工序）`);
      }
    },
    [hasSession, batchOperations, simulateIncremental],
  );

  const openBatchLineDialog = useCallback(
    (batch: ProductionBatchKitting) => {
      if (!hasSession) {
        setError('请先创建 Session');
        return;
      }
      const ops = batchOperations(batch.batchNo);
      if (ops.length === 0) {
        setError('该批次在 Session 中无工序');
        return;
      }
      const anchor = ops.find((o) => o.operationId)?.operationId;
      if (!anchor) {
        setError('批次工序缺少 operationId');
        return;
      }
      setAssignDialog({
        kind: 'batch',
        batch,
        operations: ops,
        anchorOperationId: anchor,
      });
    },
    [hasSession, batchOperations],
  );

  const handleScheduleBatch = useCallback(
    async (batch: ProductionBatchKitting) => {
      if (!hasSession) {
        setError('请先创建 Session');
        return;
      }
      const ops = batchOperations(batch.batchNo);
      if (ops.length === 0) {
        setError('该批次在 Session 中无工序');
        return;
      }
      setError(null);
      const existingLine = resolveLineForBatchSchedule(ops);
      const anchor =
        ops.find((o) => !o.scheduled && !o.lineId) ?? ops[0];
      const lineId = existingLine ?? (await resolveAutoLineId(anchor));
      if (!lineId) {
        setError('该批次无可排产线，请使用「选择机台」手动指定');
        return;
      }
      await scheduleOnLine(ops, lineId);
    },
    [hasSession, batchOperations, resolveAutoLineId, scheduleOnLine],
  );

  const handleBatchDrop = useCallback(
    (payload: { batchNo: string; lineId: string; dropMinute: number }) => {
      if (!hasSession) return;
      const ops = batchOperations(payload.batchNo);
      if (ops.length === 0) {
        setError('该批次在 Session 中无工序');
        return;
      }
      void scheduleOnLine(ops, payload.lineId, payload.dropMinute);
    },
    [hasSession, batchOperations, scheduleOnLine],
  );

  const handleScheduleOperationEarliest = useCallback(
    async (op: DetailSchedulePlanningPreviewOperation) => {
      if (!hasSession) {
        setError('请先创建 Session');
        return;
      }
      setError(null);
      const lineId = op.lineId ?? (await resolveAutoLineId(op));
      if (!lineId) {
        setError('该工序无可排产线，请使用「选择机台」手动指定');
        return;
      }
      const patch = buildOperationPatch(op, lineId, lineTasks(lineId));
      await applyPatches([patch]);
    },
    [hasSession, lineTasks, applyPatches, resolveAutoLineId],
  );

  const handlePickOperationLine = useCallback(
    (op: DetailSchedulePlanningPreviewOperation) => {
      if (!hasSession) {
        setError('请先创建 Session');
        return;
      }
      setAssignDialog({ kind: 'operation', operation: op });
    },
    [hasSession],
  );

  const handleAssignLineConfirm = useCallback(
    (lineId: string) => {
      if (!assignDialog) return;
      if (assignDialog.kind === 'batch') {
        void scheduleOnLine(assignDialog.operations, lineId);
      } else {
        const patch = buildOperationPatch(
          assignDialog.operation,
          lineId,
          lineTasks(lineId),
        );
        void applyPatches([patch]);
      }
      setAssignDialog(null);
    },
    [assignDialog, scheduleOnLine, lineTasks, applyPatches],
  );

  const handleDragCommit = useCallback(
    (patch: GanttDragCommit) => {
      void simulateIncremental([
        {
          stepId: patch.operationId,
          lineId: patch.lineId,
          sequenceOnLine: patch.sequenceOnLine,
        },
      ]);
    },
    [simulateIncremental],
  );

  useEffect(() => {
    sessionBootstrapped.current = false;
  }, [activePlanVersionId]);

  useEffect(() => {
    if (!activePlanVersionId || viewingHistorical || sessionBootstrapped.current) {
      return;
    }
    sessionBootstrapped.current = true;
    void createSession();
  }, [activePlanVersionId, viewingHistorical, createSession]);

  const solve = async () => {
    if (!activePlanVersionId) {
      setError('请先在主计划模块运行主计划');
      return;
    }
    setError(null);
    setSuccess(null);
    try {
      const result = await api.solveDetailSchedule(activePlanVersionId, {
        refreshMasterPlan,
        feedbackCutoff: refreshMasterPlan ? feedbackCutoff : undefined,
      });
      setDetailSchedule(result);
      registerNewVersion(result);
      if (result.masterPlanRefresh) {
        const refreshed = await api.getMasterPlan(result.masterPlanRefresh.newMasterPlanVersionId);
        setMasterPlan(refreshed);
        setSuccess(`排程完成 ${result.planVersionId}；主计划已滚动更新`);
      } else {
        setSuccess(`排程完成：${result.planVersionId}`);
      }
      await createSession();
    } catch (e) {
      setError(e instanceof Error ? e.message : '排程失败');
    }
  };

  const handleConfirm = async () => {
    const result = await confirm();
    if (!result) return;
    setSuccess(`已发布 ${result.planVersionId} · RELEASED ${result.releasedCount} 条`);
    try {
      const ds = await api.getDetailSchedule(result.planVersionId);
      setDetailSchedule(ds);
      registerNewVersion(ds);
    } catch {
      /* ignore */
    }
    await createSession();
  };

  const bannerError = error ?? sessionError;
  const busy = sessionLoading || simulating || confirming;

  const assignDialogTitle =
    assignDialog?.kind === 'batch'
      ? `选择机台 · 批次 ${assignDialog.batch.batchNo}`
      : '选择机台';
  const assignDialogDesc =
    assignDialog?.kind === 'batch'
      ? '批次内工序将排入同一条产线（最早可排位置）'
      : assignDialog?.kind === 'operation'
        ? `${assignDialog.operation.operationName} · 最早可排`
        : undefined;
  const assignOperationId =
    assignDialog?.kind === 'batch'
      ? assignDialog.anchorOperationId
      : assignDialog?.kind === 'operation'
        ? assignDialog.operation.operationId
        : null;

  return (
    <div className="production-plan-page detail-schedule-page detail-schedule-page-v2">
      <PageHeader
        title="生产排程"
        showScheduleVersionSelector
        description="左侧 KPI；右上批次与工序；下方全量可拖拽推演甘特。"
      />
      <StatusBanner loading={busy} error={bannerError} success={success} />

      <div className="pp-toolbar card ds-toolbar-compact">
        <div className="pp-filters ds-toolbar-filters">
          <label className="ds-check">
            <input
              type="checkbox"
              checked={refreshMasterPlan}
              onChange={(e) => setRefreshMasterPlan(e.target.checked)}
              disabled={viewingHistorical}
            />
            排程后滚动主计划
          </label>
          <label className="ds-cutoff">
            <span>反馈截止</span>
            <input
              type="date"
              className="input"
              value={feedbackCutoff}
              onChange={(e) => setFeedbackCutoff(e.target.value)}
              disabled={!refreshMasterPlan || viewingHistorical}
            />
          </label>
        </div>
        <div className="pp-toolbar-actions">
          <button
            type="button"
            className="btn"
            disabled={
              busy || !hasSession || viewingHistorical || scheduledPreviewOps.length === 0
            }
            title={
              scheduledPreviewOps.length === 0
                ? '当前无已排产工序'
                : `取消全部已排计划（${scheduledPreviewOps.length} 道工序）`
            }
            onClick={() => void handleCancelAllPlans()}
          >
            取消计划
          </button>
          <button
            type="button"
            className="btn"
            disabled={busy || !hasSession}
            title="对当前 Session 全部已排工序重新链式赋时"
            onClick={() => void simulateFull()}
          >
            {simulating ? '推演中…' : '全量推演'}
          </button>
          <button
            type="button"
            className="btn"
            disabled={busy || !hasSession}
            onClick={() => void optimize()}
          >
            Timefold 优化
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={busy || !hasSession}
            onClick={() => void handleConfirm()}
          >
            {confirming ? '发布中…' : '确认发布'}
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => setShowDsDiagnostics((v) => !v)}
            disabled={!activePlanVersionId}
          >
            {showDsDiagnostics ? '收起诊断' : '推演诊断'}
          </button>
          <button type="button" className="btn" disabled={busy} onClick={() => void solve()}>
            求解并排程
          </button>
        </div>
        <p className="pp-hint">
          <Link to="/scheduling/kitting">齐套</Link>
          {' · '}
          <Link to="/scheduling/pending-work-orders">待排工单</Link>
          {session && <span> · Session {session.sessionId}</span>}
          {selectedBatch && <span> · 已选批次 {selectedBatch.batchNo}</span>}
        </p>
      </div>

      {showDsDiagnostics && (
        <section className="card ds-diagnostics-panel">
          <PlanningDiagnosticsPanel
            layer="detail-schedule"
            contextId={activePlanVersionId ?? undefined}
            autoLoad
          />
        </section>
      )}

      <ScheduleViolationsPanel
        violations={preview?.violations}
        maxItems={8}
        appliedRules={simulateMeta?.appliedRules}
        simulationProfileId={simulateMeta?.simulationProfileId}
      />

      <HorizontalResizeSplit
        className="ds-layout-split"
        storageKey="detail-schedule-h-split-v2"
        minLeftRatio={0.1}
        maxLeftRatio={0.2}
        defaultLeftRatio={0.14}
        left={
          <section className="card ds-left-kpi">
            <DetailScheduleKpiPanel
              detailScheduleVersionId={activeVersionId}
              preview={preview}
            />
          </section>
        }
        right={
          <VerticalResizeSplit
            className="ds-right-split"
            storageKey="detail-schedule-right-v-split"
            minTopRatio={0.14}
            maxTopRatio={0.48}
            defaultTopRatio={0.28}
            collapsible
            collapseBarLabel="待排产批次 · 批次工序"
            top={
              <section className="card ds-right-top ds-batch-workspace">
                <HorizontalResizeSplit
                  className="ds-batch-split"
                  storageKey="detail-schedule-batch-h-split"
                  minLeftRatio={0.45}
                  maxLeftRatio={0.7}
                  left={
                    <PendingScheduleBatchList
                      selectedBatchNo={selectedBatch?.batchNo ?? null}
                      onSelectBatch={setSelectedBatch}
                      hasSession={hasSession && !viewingHistorical}
                      disabled={busy}
                      previewOperations={preview?.operations}
                      previewRefreshKey={preview?.computedAt ?? session?.sessionId}
                      onScheduleBatch={(b) => void handleScheduleBatch(b)}
                      onPickBatchLine={openBatchLineDialog}
                      onCancelBatchPlan={handleCancelBatchPlan}
                    />
                  }
                  right={
                    <BatchOperationListPanel
                      selectedBatchNo={selectedBatch?.batchNo ?? null}
                      preview={preview}
                      hasSession={hasSession}
                      selectedOperationId={selectedOperationId}
                      onSelectOperation={(op) =>
                        setSelectedOperationId(op?.operationId ?? null)
                      }
                      onScheduleEarliest={(op) =>
                        void handleScheduleOperationEarliest(op)
                      }
                      onAssignLine={handlePickOperationLine}
                      disabled={busy || viewingHistorical}
                    />
                  }
                />
              </section>
            }
            bottom={
              <section className="card ds-right-bottom">
                <div className="pp-panel-head ds-bottom-tabs">
                  <div className="ds-view-toggle" role="tablist">
                    <button
                      type="button"
                      role="tab"
                      aria-selected={rightBottomTab === 'editGantt'}
                      className={`btn ds-view-toggle-btn ${rightBottomTab === 'editGantt' ? 'active' : ''}`}
                      onClick={() => setRightBottomTab('editGantt')}
                    >
                      拖拽甘特
                    </button>
                    <button
                      type="button"
                      role="tab"
                      aria-selected={rightBottomTab === 'lineTasks'}
                      className={`btn ds-view-toggle-btn ${rightBottomTab === 'lineTasks' ? 'active' : ''}`}
                      onClick={() => setRightBottomTab('lineTasks')}
                    >
                      产线任务
                    </button>
                  </div>
                  {rightBottomTab === 'lineTasks' && (
                    <label className="ds-line-filter">
                      <span>产线</span>
                      <select
                        className="input"
                        value={lineFilter}
                        onChange={(e) => setLineFilter(e.target.value)}
                      >
                        <option value="">全部</option>
                        {[...new Set(allGanttOps.map((o) => o.lineId))].sort().map((id) => (
                          <option key={id} value={id}>
                            {id}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                </div>
                <div className="ds-right-bottom-body">
                  {rightBottomTab === 'editGantt' ? (
                    <MachineScheduleGantt
                      operations={allGanttOps}
                      className="ds-gantt-panel ds-gantt-editable"
                      editable={hasSession && !viewingHistorical}
                      onDragCommit={handleDragCommit}
                      acceptBatchDrop={hasSession && !viewingHistorical}
                      onBatchDrop={handleBatchDrop}
                    />
                  ) : (
                    <ProductionTaskPanel lineFilter={lineFilter} hideLineFilter />
                  )}
                </div>
              </section>
            }
          />
        }
      />

      <AssignLineDialog
        open={assignDialog != null}
        title={assignDialogTitle}
        description={assignDialogDesc}
        sessionId={session?.sessionId ?? null}
        operationId={assignOperationId}
        onConfirm={handleAssignLineConfirm}
        onCancel={() => setAssignDialog(null)}
        busy={busy}
      />
    </div>
  );
}
