import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { slittingClient } from '../../api/slittingClient';
import { DECISION_PAGE_HEADER, PageHeader } from '../../components/PageHeader';
import { StatusBanner } from '../../components/StatusBanner';
import { FilterableTable } from '../../components/table/FilterableTable';
import type { SlittingSolverRun, SlittingSolverRunLogLine } from '../../types/slitting';
import { SLITTING_RUN_TYPE_LABELS } from '../../types/slitting';
import '../../pages/DashboardPage.css';

function runStatusBadge(status: string) {
  if (status === 'SUCCESS') return 'badge ok';
  if (status === 'FAILED') return 'badge danger';
  if (status === 'RUNNING') return 'badge warn';
  return 'badge muted';
}

function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function runTypeLabel(runType: string) {
  return SLITTING_RUN_TYPE_LABELS[runType] ?? runType;
}

export function SlittingOptimizeRunPage() {
  const [runs, setRuns] = useState<SlittingSolverRun[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [runLogs, setRunLogs] = useState<SlittingSolverRunLogLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [polling, setPolling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadRuns = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await slittingClient.listSolverRuns(40);
      setRuns(list);
      if (list.length === 0) {
        setSelectedRunId(null);
        setRunLogs([]);
        return list;
      }
      const keep = selectedRunId && list.some((r) => r.runId === selectedRunId);
      const target = keep ? selectedRunId! : list[0].runId;
      setSelectedRunId(target);
      const row = list.find((r) => r.runId === target);
      setRunLogs(row?.executionLog ?? []);
      return list;
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
      return [];
    } finally {
      setLoading(false);
    }
  }, [selectedRunId]);

  useEffect(() => {
    void loadRuns();
  }, []);

  useEffect(() => {
    const hasRunning = runs.some((r) => r.status === 'RUNNING');
    if (!hasRunning) {
      setPolling(false);
      return;
    }
    setPolling(true);
    const timer = window.setInterval(() => {
      void loadRuns();
    }, 2000);
    return () => window.clearInterval(timer);
  }, [runs, loadRuns]);

  const selectRun = (row: SlittingSolverRun) => {
    setSelectedRunId(row.runId);
    setRunLogs(row.executionLog ?? []);
  };

  const selectedRun = runs.find((r) => r.runId === selectedRunId);

  return (
    <div className="master-data-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="优化运行"
        description="查看分切 Timefold 求解运行记录与算法日志。在工作台「自动分切 / 优化未锁定」、整方案求解或会话局部优化时会自动生成记录。"
        actions={
          <button type="button" className="btn" onClick={() => void loadRuns()} disabled={loading}>
            刷新
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} />

      <section className="dash-run-section">
        <FilterableTable<SlittingSolverRun>
          tableId="slitting-solver-runs"
          rows={runs}
          rowKey={(r) => r.runId}
          onRowClick={selectRun}
          getRowClassName={(r) => (selectedRunId === r.runId ? 'is-selected' : '')}
          columns={[
            { key: 'runId', header: '运行 ID', className: 'mono', render: (r) => r.runId },
            {
              key: 'runType',
              header: '类型',
              render: (r) => runTypeLabel(r.runType),
            },
            {
              key: 'status',
              header: '状态',
              render: (r) => <span className={runStatusBadge(r.status)}>{r.status}</span>,
            },
            { key: 'planVersionId', header: '方案', render: (r) => r.planVersionId ?? '—' },
            { key: 'masterNodeId', header: '母卷节点', render: (r) => r.masterNodeId ?? '—' },
            { key: 'score', header: '得分', render: (r) => r.score ?? '—' },
            {
              key: 'durationMs',
              header: '耗时',
              render: (r) => (r.durationMs != null ? `${(r.durationMs / 1000).toFixed(1)}s` : '—'),
            },
            { key: 'startedTs', header: '开始', render: (r) => fmtDateTime(r.startedTs) },
          ]}
        />

        <div className="dash-run-log-panel">
          <div className="dash-run-log-head">
            <h4>Timefold 运行日志</h4>
            <span className="dash-run-log-meta">
              {selectedRun
                ? `${selectedRun.runId} · ${runTypeLabel(selectedRun.runType)}`
                : '点击上方记录查看日志'}
              {polling ? ' · 自动刷新中…' : ''}
            </span>
          </div>
          {selectedRun?.summary && <p className="md-tab-desc">{selectedRun.summary}</p>}
          {selectedRun?.errorMessage && (
            <p className="status-banner error" style={{ margin: '0.5rem 0' }}>
              {selectedRun.errorMessage}
            </p>
          )}
          <div className="dash-run-log-body" aria-live="polite">
            {runLogs.length === 0 ? (
              <p className="dash-run-log-empty">暂无日志（请先在工作台执行一次优化）</p>
            ) : (
              runLogs.map((line, idx) => (
                <div key={`${line.timestamp}-${idx}`} className={`dash-run-log-line level-${line.level}`}>
                  <span className="dash-run-log-ts">{fmtDateTime(line.timestamp)}</span>
                  <span className="dash-run-log-lvl">[{line.level}]</span>
                  <span className="dash-run-log-msg">{line.message}</span>
                </div>
              ))
            )}
          </div>
        </div>

        <p className="md-tab-desc">
          参数维护见 <Link to="/slitting/parameters">优化参数</Link>；分切编辑见{' '}
          <Link to="/slitting/studio">母卷分切</Link>。
        </p>
      </section>
    </div>
  );
}
