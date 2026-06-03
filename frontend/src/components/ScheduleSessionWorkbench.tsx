import { useCallback, useState } from 'react';
import { api } from '../api/client';
import { ScheduleViolationsPanel } from './ScheduleViolationsPanel';
import type { DetailSchedulePlanningPreview } from '../types/detailSchedulePlanningPreview';
import type {
  ConfirmScheduleSessionResult,
  ScheduleSession,
  ScheduleSessionSimulateResult,
  SessionStepPatch,
} from '../types/scheduleSession';
import './ScheduleSessionWorkbench.css';

export interface ScheduleSessionWorkbenchProps {
  masterPlanVersionId: string | null;
  /** 创建 Session 时是否仅 seed 队列（默认 true） */
  seedOnCreate?: boolean;
  onPreviewChange?: (preview: DetailSchedulePlanningPreview | null) => void;
  onSessionChange?: (session: ScheduleSession | null) => void;
  onConfirm?: (result: ConfirmScheduleSessionResult) => void;
}

export function ScheduleSessionWorkbench({
  masterPlanVersionId,
  seedOnCreate = true,
  onPreviewChange,
  onSessionChange,
  onConfirm,
}: ScheduleSessionWorkbenchProps) {
  const [loading, setLoading] = useState(false);
  const [simulateLoading, setSimulateLoading] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [session, setSession] = useState<ScheduleSession | null>(null);
  const [preview, setPreview] = useState<DetailSchedulePlanningPreview | null>(null);
  const [simulateResult, setSimulateResult] = useState<ScheduleSessionSimulateResult | null>(null);
  const [confirmResult, setConfirmResult] = useState<ConfirmScheduleSessionResult | null>(null);

  const [patchStepId, setPatchStepId] = useState('');
  const [patchLineId, setPatchLineId] = useState('');
  const [patchSeq, setPatchSeq] = useState('');
  const [patchPinned, setPatchPinned] = useState<boolean | null>(null);

  const updateSession = useCallback(
    (next: ScheduleSession | null, nextPreview: DetailSchedulePlanningPreview | null) => {
      setSession(next);
      setPreview(nextPreview);
      onSessionChange?.(next);
      onPreviewChange?.(nextPreview);
    },
    [onPreviewChange, onSessionChange],
  );

  const createSession = useCallback(async () => {
    if (!masterPlanVersionId) {
      setErr('请先选择主计划版本');
      return;
    }
    setLoading(true);
    setErr(null);
    setConfirmResult(null);
    setSimulateResult(null);
    try {
      const result = await api.createScheduleSession({
        masterPlanVersionId,
        seedInitialQueues: seedOnCreate,
        solve: false,
      });
      updateSession(result, result.preview);
    } catch (e: unknown) {
      updateSession(null, null);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [masterPlanVersionId, seedOnCreate, updateSession]);

  const runSimulate = useCallback(
    async (patches?: SessionStepPatch[], fullReschedule?: boolean) => {
      if (!session?.sessionId) {
        setErr('请先创建 Session');
        return;
      }
      setSimulateLoading(true);
      setErr(null);
      try {
        const result = await api.simulateScheduleSession(session.sessionId, {
          stepPatches: patches,
          fullReschedule,
        });
        setSimulateResult(result);
        updateSession(result.session, result.session.preview);
      } catch (e: unknown) {
        setErr(e instanceof Error ? e.message : String(e));
      } finally {
        setSimulateLoading(false);
      }
    },
    [session, updateSession],
  );

  const applyPatchAndSimulate = useCallback(async () => {
    if (!patchStepId) {
      setErr('请选择要调整的工序');
      return;
    }
    const patch: SessionStepPatch = { stepId: patchStepId };
    if (patchLineId.trim()) {
      patch.lineId = patchLineId.trim();
    }
    if (patchSeq.trim()) {
      const seq = Number.parseInt(patchSeq, 10);
      if (!Number.isFinite(seq) || seq < 1) {
        setErr('产线顺序须为正整数');
        return;
      }
      patch.sequenceOnLine = seq;
    }
    if (patchPinned !== null) {
      patch.pinned = patchPinned;
    }
    await runSimulate([patch], false);
  }, [patchStepId, patchLineId, patchSeq, patchPinned, runSimulate]);

  const confirmSession = useCallback(async () => {
    if (!session?.sessionId) {
      setErr('请先创建 Session');
      return;
    }
    setConfirmLoading(true);
    setErr(null);
    try {
      const result = await api.confirmScheduleSession(session.sessionId);
      setConfirmResult(result);
      updateSession(null, null);
      onConfirm?.(result);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setConfirmLoading(false);
    }
  }, [session, updateSession, onConfirm]);

  const scheduledOps = preview?.operations.filter((op) => op.scheduled) ?? [];
  const lineOptions = preview?.lines.map((l) => l.lineId) ?? [];

  return (
    <div className="schedule-session-workbench">
      <div className="schedule-session-toolbar">
        <button type="button" className="btn" disabled={loading || !masterPlanVersionId} onClick={() => void createSession()}>
          {loading ? '创建中…' : '创建 Session'}
        </button>
        <button
          type="button"
          className="btn"
          disabled={simulateLoading || !session?.sessionId}
          onClick={() => void runSimulate(undefined, true)}
          title="对当前 Session 全部已排工序重新链式赋时"
        >
          {simulateLoading ? '推演中…' : '全量推演'}
        </button>
        <button
          type="button"
          className="btn primary"
          disabled={confirmLoading || !session?.sessionId}
          onClick={() => void confirmSession()}
        >
          {confirmLoading ? '发布中…' : '确认发布'}
        </button>
      </div>

      {session?.sessionId && (
        <p className="schedule-session-meta muted-text">
          Session {session.sessionId} · 过期 {new Date(session.expiresAt).toLocaleString()}
        </p>
      )}
      {confirmResult && (
        <p className="schedule-session-meta">
          已发布 {confirmResult.planVersionId} · RELEASED {confirmResult.releasedCount} 条
          {confirmResult.conflicts.length > 0 && ` · 冲突 ${confirmResult.conflicts.length} 条`}
        </p>
      )}
      {preview && (
        <p className="schedule-session-meta">
          工序 {preview.scheduledOperationCount}/{preview.operationCount} 已排产
          {preview.simulationMode && (
            <span>
              {' '}
              · 推演 {preview.simulationMode} {preview.simulationDurationMs ?? 0} ms
            </span>
          )}
        </p>
      )}
      {simulateResult && (
        <p className="schedule-session-meta">
          校验：硬 {simulateResult.hardViolationCount} · 中 {simulateResult.mediumViolationCount}
          {simulateResult.recalculatedOperationIds.length > 0 &&
            ` · 波及 ${simulateResult.recalculatedOperationIds.length} 道工序`}
        </p>
      )}
      {err && <p className="error">{err}</p>}

      {session && scheduledOps.length > 0 && (
        <div className="schedule-session-patch card">
          <h4 className="panel-title">手动调整 → 增量推演（仅本 patch）</h4>
          <div className="schedule-session-patch-row">
            <label>
              工序
              <select className="input" value={patchStepId} onChange={(e) => setPatchStepId(e.target.value)}>
                <option value="">选择…</option>
                {scheduledOps.map((op) => (
                  <option key={op.operationId} value={op.operationId}>
                    {op.operationId} ({op.lineId ?? '未分配'})
                  </option>
                ))}
              </select>
            </label>
            <label>
              产线
              <select className="input" value={patchLineId} onChange={(e) => setPatchLineId(e.target.value)}>
                <option value="">不变</option>
                {lineOptions.map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              </select>
            </label>
            <label>
              产线顺序
              <input
                className="input"
                type="number"
                min={1}
                placeholder="不变"
                value={patchSeq}
                onChange={(e) => setPatchSeq(e.target.value)}
              />
            </label>
            <label className="schedule-session-check">
              <input
                type="checkbox"
                checked={patchPinned === true}
                onChange={(e) => setPatchPinned(e.target.checked ? true : null)}
              />
              锁定
            </label>
            <button
              type="button"
              className="btn primary"
              disabled={simulateLoading || !patchStepId}
              onClick={() => void applyPatchAndSimulate()}
            >
              应用并重算
            </button>
          </div>
        </div>
      )}

      <ScheduleViolationsPanel
        violations={preview?.violations}
        appliedRules={simulateResult?.appliedRules}
        simulationProfileId={
          simulateResult?.simulationProfileId ?? session?.simulationProfileId
        }
      />
    </div>
  );
}
