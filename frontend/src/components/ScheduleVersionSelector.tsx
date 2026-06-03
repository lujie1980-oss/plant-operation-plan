import { useScheduleVersion } from '../context/ScheduleVersionContext';
import './ScenarioSelector.css';

function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function ScheduleVersionSelector() {
  const {
    versions,
    versionsLoading,
    currentVersionId,
    viewHistory,
    historyVersionId,
    activeVersionId,
    activeVersion,
    refreshVersions,
    setViewHistory,
    selectHistoryVersion,
  } = useScheduleVersion();

  const busy = versionsLoading;
  const displayId = activeVersionId ?? currentVersionId;

  return (
    <div className="scenario-selector scenario-selector--topbar schedule-version-selector">
      <label className="schedule-version-history-toggle">
        <input
          type="checkbox"
          checked={viewHistory}
          onChange={(e) => setViewHistory(e.target.checked)}
          disabled={busy || versions.length === 0}
        />
        查看历史版本
      </label>
      <span className="scenario-selector-title">排程版本</span>
      {viewHistory ? (
        <select
          className="scenario-selector-input"
          value={historyVersionId ?? currentVersionId ?? ''}
          onChange={(e) => selectHistoryVersion(e.target.value || null)}
          disabled={busy || versions.length === 0}
          aria-label="选择历史排程版本"
        >
          {versions.length === 0 ? (
            <option value="">{busy ? '加载中…' : '暂无版本'}</option>
          ) : (
            versions.map((v) => (
              <option key={v.planVersionId} value={v.planVersionId}>
                {v.planVersionId}
                {v.planVersionId === currentVersionId ? '（当前）' : ''}
                {' · '}
                {fmtDateTime(v.generatedAt)}
              </option>
            ))
          )}
        </select>
      ) : (
        <span className="schedule-version-current mono" title="当前排程版本">
          {displayId ?? (busy ? '加载中…' : '尚未求解')}
        </span>
      )}
      <button
        type="button"
        className="scenario-selector-refresh"
        onClick={() => void refreshVersions()}
        disabled={busy}
        title="刷新版本列表"
        aria-label="刷新版本列表"
      >
        ↻
      </button>
      {activeVersion && (
        <span className="scenario-selector-strategy schedule-version-meta" title="版本摘要">
          {fmtDateTime(activeVersion.generatedAt)}
          {activeVersion.score ? ` · ${activeVersion.score}` : ''}
          {` · ${activeVersion.operationCount} 工序`}
        </span>
      )}
      {busy && <span className="scenario-selector-busy">加载中…</span>}
    </div>
  );
}
