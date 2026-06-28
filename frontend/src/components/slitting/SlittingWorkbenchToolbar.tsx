import type { SlittingPlanSummary } from '../../types/slitting';

type Props = {
  title?: string;
  plans: SlittingPlanSummary[];
  planVersionId: string | null;
  sessionId: string | null;
  utilizationPct: number;
  loading: boolean;
  onPlanChange: (id: string) => void;
  onSolve: () => void;
  onSave: () => void;
  onCreateSession: () => void;
  onLocalOptimize: () => void;
  onAutoNest: () => void;
  onConfirmSession: () => void;
};

export function SlittingWorkbenchToolbar({
  title = '分切求解工作台',
  plans,
  planVersionId,
  sessionId,
  utilizationPct,
  loading,
  onPlanChange,
  onSolve,
  onSave,
  onCreateSession,
  onLocalOptimize,
  onAutoNest,
  onConfirmSession,
}: Props) {
  return (
    <div
      className="slitting-toolbar slitting-toolbar--page"
      title="画板拖拽、树形钻取、求解与编辑会话"
    >
      <h1 className="slitting-toolbar-title">{title}</h1>
      <div className="slitting-toolbar-divider" aria-hidden />

      <div className="slitting-toolbar-group">
        <span className="slitting-toolbar-label">方案</span>
        <label className="slitting-toolbar-field">
          <span className="sr-only">选择方案</span>
          <select
            className="input slitting-select"
            value={planVersionId ?? ''}
            onChange={(e) => {
              const id = e.target.value;
              if (id) onPlanChange(id);
            }}
          >
            <option value="">— 选择方案 —</option>
            {plans.map((p) => (
              <option key={p.planVersionId} value={p.planVersionId}>
                {p.name} ({p.status})
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="slitting-toolbar-divider" aria-hidden />

      <div className="slitting-toolbar-group">
        <span className="slitting-toolbar-label">求解与保存</span>
        <button
          type="button"
          className="btn primary slitting-btn-accent"
          disabled={!planVersionId || loading}
          onClick={onSolve}
        >
          {loading ? '处理中…' : '全局求解'}
        </button>
        <button type="button" className="btn" disabled={!planVersionId || loading} onClick={onSave}>
          保存坐标
        </button>
      </div>

      <div className="slitting-toolbar-divider" aria-hidden />

      <div className="slitting-toolbar-group">
        <span className="slitting-toolbar-label">编辑会话</span>
        <button type="button" className="btn" disabled={!planVersionId || loading} onClick={onCreateSession}>
          创建会话
        </button>
        <button type="button" className="btn" disabled={!sessionId || loading} onClick={onLocalOptimize}>
          局部重算
        </button>
        <button type="button" className="btn" disabled={!sessionId || loading} onClick={onAutoNest}>
          Auto-Nest
        </button>
        <button
          type="button"
          className="btn slitting-btn-confirm"
          disabled={!sessionId || loading}
          onClick={onConfirmSession}
        >
          确认写回
        </button>
      </div>

      <div className="slitting-toolbar-kpis">
        <span className="slitting-kpi">
          利用率 <strong>{utilizationPct.toFixed(1)}%</strong>
        </span>
        {sessionId ? (
          <span className="slitting-kpi slitting-kpi--session" title={sessionId}>
            会话 <code>{sessionId.slice(0, 8)}…</code>
          </span>
        ) : null}
      </div>
    </div>
  );
}
