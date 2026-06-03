import { useCallback, useState } from 'react';
import { api } from '../api/client';
import { PlanningDiagnosticsPanel } from './PlanningDiagnosticsPanel';
import type { MasterPlanPlanningPreview } from '../types/masterPlanPlanningPreview';
import './DetailSchedulePlanningPreviewPanel.css';

export interface MasterPlanPlanningPreviewPanelProps {
  strategyId: string | null;
  feedbackCutoff: string | null;
}

export function MasterPlanPlanningPreviewPanel({
  strategyId,
  feedbackCutoff,
}: MasterPlanPlanningPreviewPanelProps) {
  const [solve, setSolve] = useState(false);
  const [persist, setPersist] = useState(false);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [preview, setPreview] = useState<MasterPlanPlanningPreview | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const result = await api.previewMasterPlanPlanning({
        strategyId: strategyId ?? undefined,
        solve,
        persist: solve && persist,
        feedbackCutoff: feedbackCutoff ?? undefined,
      });
      setPreview(result);
    } catch (e: unknown) {
      setPreview(null);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [strategyId, solve, persist, feedbackCutoff]);

  const scheduled = preview?.allocations.filter((a) => a.scheduled) ?? [];

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
        <button type="button" className="btn primary" disabled={loading} onClick={load}>
          {loading ? '推演中…' : '运行推演预览'}
        </button>
      </div>
      {err && <p className="error">{err}</p>}
      {preview && (
        <div className="ds-planning-preview-meta">
          <span>
            分配 {preview.scheduledAllocationCount}/{preview.allocationCount} 已落槽
          </span>
          <span>策略 {preview.strategyName}</span>
          <span>产能 {preview.capacityStrategy}</span>
          {preview.overlayActive && <span>反馈 overlay</span>}
          {preview.solved && preview.score && <span>得分 {preview.score}</span>}
          {preview.persisted && preview.planVersionId && (
            <span>已落库 {preview.planVersionId}</span>
          )}
          {preview.solveDurationMs != null && <span>耗时 {preview.solveDurationMs} ms</span>}
        </div>
      )}
      {preview?.diagnostics && (
        <PlanningDiagnosticsPanel
          layer="master-plan"
          contextId={strategyId}
          snapshot={preview.diagnostics}
          readOnly
          compact
        />
      )}
      {scheduled.length > 0 && (
        <div className="ds-planning-preview-gantt card">
          <h4>已落槽分配（前 30 条）</h4>
          <div className="table-wrap">
            <table className="data-table compact">
              <thead>
                <tr>
                  <th>工单</th>
                  <th>工序</th>
                  <th>资源</th>
                  <th>槽位日</th>
                  <th>班次</th>
                </tr>
              </thead>
              <tbody>
                {scheduled.slice(0, 30).map((a) => (
                  <tr key={a.allocationId}>
                    <td>{a.workOrderNo}</td>
                    <td>
                      {a.operationSeq}
                      {a.operationName ? ` ${a.operationName}` : ''}
                    </td>
                    <td>{a.resourceId}</td>
                    <td>{a.slotDate ?? '—'}</td>
                    <td>{a.shiftId ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
