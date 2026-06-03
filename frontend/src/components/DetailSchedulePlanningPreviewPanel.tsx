import { useCallback, useState } from 'react';
import { api } from '../api/client';
import { MachineScheduleGantt } from './MachineScheduleGantt';
import { PlanningDiagnosticsPanel } from './PlanningDiagnosticsPanel';
import { ScheduleSessionWorkbench } from './ScheduleSessionWorkbench';
import type { DetailSchedulePlanningPreview } from '../types/detailSchedulePlanningPreview';
import { previewOperationsToGantt } from '../utils/previewOperationsToGantt';
import './DetailSchedulePlanningPreviewPanel.css';

export interface DetailSchedulePlanningPreviewPanelProps {
  masterPlanVersionId: string | null;
}

export function DetailSchedulePlanningPreviewPanel({
  masterPlanVersionId,
}: DetailSchedulePlanningPreviewPanelProps) {
  const [solve, setSolve] = useState(false);
  const [persist, setPersist] = useState(false);
  const [seedInitialQueues, setSeedInitialQueues] = useState(false);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [preview, setPreview] = useState<DetailSchedulePlanningPreview | null>(null);
  const [sessionPreview, setSessionPreview] = useState<DetailSchedulePlanningPreview | null>(null);

  const load = useCallback(async () => {
    if (!masterPlanVersionId) {
      setErr('请先选择主计划版本');
      return;
    }
    setLoading(true);
    setErr(null);
    try {
      const result = await api.previewDetailSchedulePlanning({
        masterPlanVersionId,
        solve,
        persist: solve && persist,
        seedInitialQueues: !solve && seedInitialQueues,
      });
      setPreview(result);
      setSessionPreview(null);
    } catch (e: unknown) {
      setPreview(null);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [masterPlanVersionId, solve, persist, seedInitialQueues]);

  const displayPreview = sessionPreview ?? preview;
  const ganttOps = previewOperationsToGantt(displayPreview?.operations);

  return (
    <div className="ds-planning-preview">
      <div className="ds-planning-preview-toolbar">
        <label className="ds-planning-preview-check">
          <input
            type="checkbox"
            checked={solve}
            onChange={(e) => {
              setSolve(e.target.checked);
              if (!e.target.checked) {
                setPersist(false);
              }
            }}
          />
          内存求解（Timefold）
        </label>
        <label className="ds-planning-preview-check">
          <input
            type="checkbox"
            checked={persist}
            disabled={!solve}
            onChange={(e) => setPersist(e.target.checked)}
          />
          求解并落库
        </label>
        <label className="ds-planning-preview-check">
          <input
            type="checkbox"
            checked={seedInitialQueues}
            disabled={solve}
            onChange={(e) => setSeedInitialQueues(e.target.checked)}
          />
          仅初始队列 + 赋时（不求解）
        </label>
        <button type="button" className="btn primary" disabled={loading || !masterPlanVersionId} onClick={load}>
          {loading ? '推演中…' : '运行推演预览'}
        </button>
      </div>

      <ScheduleSessionWorkbench
        masterPlanVersionId={masterPlanVersionId}
        seedOnCreate={!solve && seedInitialQueues}
        onPreviewChange={setSessionPreview}
        onSessionChange={(session) => {
          if (!session) {
            setSessionPreview(null);
          }
        }}
      />

      {err && <p className="error">{err}</p>}
      {displayPreview && (
        <div className="ds-planning-preview-meta">
          <span>
            工序 {displayPreview.scheduledOperationCount}/{displayPreview.operationCount} 已排产
          </span>
          {displayPreview.solved && displayPreview.score && <span>得分 {displayPreview.score}</span>}
          {displayPreview.persisted && displayPreview.planVersionId && (
            <span>已落库 {displayPreview.planVersionId}</span>
          )}
          {displayPreview.solveDurationMs != null && <span>耗时 {displayPreview.solveDurationMs} ms</span>}
          {displayPreview.simulationMode && (
            <span>
              推演 {displayPreview.simulationMode} · {displayPreview.simulationDurationMs ?? 0} ms
            </span>
          )}
        </div>
      )}
      {displayPreview?.diagnostics && (
        <PlanningDiagnosticsPanel
          layer="detail-schedule"
          contextId={masterPlanVersionId}
          snapshot={displayPreview.diagnostics}
          readOnly
          compact
        />
      )}
      {ganttOps.length > 0 && (
        <div className="ds-planning-preview-gantt card">
          <h4>推演甘特（已排产工序）</h4>
          <MachineScheduleGantt operations={ganttOps} />
        </div>
      )}
    </div>
  );
}
