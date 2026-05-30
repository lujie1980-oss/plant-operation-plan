import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { PlanningDiagnosticsPanel } from '../components/PlanningDiagnosticsPanel';
import { usePlan } from '../context/PlanContext';
import type { PipelineRunLogLine, PlanningPipelineRun, PlanningScenario, RuleSetVersion } from '../types/api';
import {
  CAPACITY_STRATEGY_LABELS,
  type MasterPlanStrategySummary,
} from '../types/masterPlanStrategies';
import './DashboardPage.css';

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

function runStrategyLabel(row: PlanningPipelineRun): string {
  if (row.strategyName) {
    return row.strategyName;
  }
  return CAPACITY_STRATEGY_LABELS[row.capacityStrategy as keyof typeof CAPACITY_STRATEGY_LABELS] ?? row.capacityStrategy;
}

export function PlanRunPage() {
  const { setMasterPlan, setPipeline, masterPlan, refreshScenarios, selectScenario, scenarios } = usePlan();
  const [pipelineRuns, setPipelineRuns] = useState<PlanningPipelineRun[]>([]);
  const [strategies, setStrategies] = useState<MasterPlanStrategySummary[]>([]);
  const [ruleVersions, setRuleVersions] = useState<RuleSetVersion[]>([]);
  const [runScenarioId, setRunScenarioId] = useState('');
  const [selectedStrategyId, setSelectedStrategyId] = useState<string>('');
  const [showCreateScenario, setShowCreateScenario] = useState(false);
  const [newScenarioName, setNewScenarioName] = useState('');
  const [newScenarioRuleId, setNewScenarioRuleId] = useState('');
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [runLogs, setRunLogs] = useState<PipelineRunLogLine[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [includeDetailSchedule, setIncludeDetailSchedule] = useState(false);
  const [refreshAfterSchedule, setRefreshAfterSchedule] = useState(false);
  const [showMpDiagnostics, setShowMpDiagnostics] = useState(false);
  const [diagUseFeedbackOverlay, setDiagUseFeedbackOverlay] = useState(false);
  const [diagFeedbackCutoff, setDiagFeedbackCutoff] = useState(() => new Date().toISOString().slice(0, 10));

  const loadPipelineRuns = useCallback(async () => {
    setPipelineRuns(await api.listPipelineRuns(30));
  }, []);

  const loadStrategies = useCallback(async () => {
    const list = await api.listMasterPlanStrategies();
    setStrategies(list);
    if (list.length === 0) {
      setSelectedStrategyId('');
      return;
    }
    setSelectedStrategyId((prev) => {
      const valid = prev && list.some((s) => s.id === prev);
      if (valid) return prev;
      return list.find((s) => s.isDefault)?.id ?? list[0].id;
    });
  }, []);

  const loadRuleVersions = useCallback(async () => {
    setRuleVersions(await api.listRuleSetVersions());
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [catalog] = await Promise.all([
        refreshScenarios(),
        loadPipelineRuns(),
        loadStrategies(),
        loadRuleVersions(),
      ]);
      setRunScenarioId((prev) => {
        if (prev && catalog.some((s) => s.scenarioId === prev)) return prev;
        return catalog.find((s) => s.isDefault)?.scenarioId ?? catalog[0]?.scenarioId ?? '';
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [loadPipelineRuns, loadStrategies, loadRuleVersions, refreshScenarios]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectRun = (row: PlanningPipelineRun) => {
    setSelectedRunId(row.runId);
    setRunLogs(row.executionLog ?? []);
  };

  useEffect(() => {
    if (pipelineRuns.length === 0) return;
    if (selectedRunId && pipelineRuns.some((r) => r.runId === selectedRunId)) return;
    selectRun(pipelineRuns[0]);
  }, [pipelineRuns, selectedRunId]);

  const selectedStrategy = strategies.find((s) => s.id === selectedStrategyId);
  const selectedRun = pipelineRuns.find((r) => r.runId === selectedRunId);
  const runScenario = scenarios.find((s) => s.scenarioId === runScenarioId);

  const createScenario = async () => {
    if (!newScenarioName.trim()) {
      setError('请输入场景名称');
      return;
    }
    if (!selectedStrategyId) {
      setError('请先选择主计划策略');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const created = await api.createScenario({
        name: newScenarioName.trim(),
        strategyId: selectedStrategyId,
        ruleSetVersionId: newScenarioRuleId || undefined,
      });
      setShowCreateScenario(false);
      setNewScenarioName('');
      const list = await refreshScenarios();
      setRunScenarioId(created.scenarioId);
      await selectScenario(created.scenarioId);
      if (list.length > 0) setSuccess(`已创建场景「${created.name}」`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建场景失败');
    } finally {
      setLoading(false);
    }
  };

  const runPlanning = async () => {
    if (!selectedStrategyId) {
      setError('请先配置并选择主计划策略');
      return;
    }
    if (!runScenarioId) {
      setError('请选择要运行的计划场景');
      return;
    }
    setRunning(true);
    setError(null);
    setSuccess(null);
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let activeRunId: string | null = null;
    try {
      const started = await api.startPipelineRun(selectedStrategyId, {
        scenarioId: runScenarioId,
        ruleSetVersionId: runScenario?.ruleSetVersionId,
      });
      activeRunId = started.runId;
      setSelectedRunId(activeRunId);
      setRunLogs(started.executionLog ?? []);

      pollTimer = setInterval(() => {
        void api
          .getPipelineRun(activeRunId!)
          .then((run) => setRunLogs(run.executionLog ?? []))
          .catch(() => undefined);
        void loadPipelineRuns();
      }, 1500);

      const result = await api.executePipelineRun(activeRunId, {
        includeDetailSchedule,
        refreshMasterPlanAfterSchedule: refreshAfterSchedule,
      });
      setPipeline(result);
      setMasterPlan(result.masterPlan);
      setRunLogs(result.executionLog ?? []);
      if (result.detailSchedule && result.masterPlanRefresh) {
        setSuccess(
          `闭环完成：排程 ${result.detailSchedule.planVersionId}，主计划已更新为 ${result.masterPlan.planVersionId}`,
        );
      } else if (result.detailSchedule) {
        setSuccess(
          `主计划 ${result.masterPlan.planVersionId}，排程 ${result.detailSchedule.planVersionId}（未刷新主计划）`,
        );
      } else {
        setSuccess(
          `主计划运行完成（${result.pipelineRunId}）：版本 ${result.masterPlan.planVersionId}。请在「生产工单」确认并发布后再排程。`,
        );
      }
      await refreshScenarios();
      await selectScenario(runScenarioId);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '计划运行失败');
      if (activeRunId) {
        try {
          const run = await api.getPipelineRun(activeRunId);
          setRunLogs(run.executionLog ?? []);
          setSelectedRunId(activeRunId);
        } catch {
          /* ignore */
        }
      }
      await loadPipelineRuns();
    } finally {
      if (pollTimer) clearInterval(pollTimer);
      setRunning(false);
    }
  };

  return (
    <div className="dashboard-page">
      <PageHeader
        title="计划运行"
        description="选择计划场景与策略后运行；每个场景仅保留当前与上一版主计划，最新版为分析生效版本。"
        actions={
          <button type="button" className="btn" onClick={() => void load()} disabled={loading || running}>
            刷新
          </button>
        }
      />
      <StatusBanner loading={loading || running} error={error} success={success} />

      <section className="card dash-detail-panel">
        <div className="dash-run-toolbar">
          <label className="dash-strategy-select">
            <span>计划场景</span>
            <select
              className="input"
              value={runScenarioId}
              onChange={(e) => setRunScenarioId(e.target.value)}
              disabled={running || scenarios.length === 0}
            >
              {scenarios.length === 0 ? (
                <option value="">暂无场景</option>
              ) : (
                scenarios.map((s: PlanningScenario) => (
                  <option key={s.scenarioId} value={s.scenarioId}>
                    {s.name}
                    {s.isDefault ? '（默认）' : ''}
                  </option>
                ))
              )}
            </select>
          </label>
          <button
            type="button"
            className="btn"
            onClick={() => {
              setShowCreateScenario((v) => !v);
              setNewScenarioRuleId(
                ruleVersions.find((r) => r.isDefault)?.ruleSetVersionId
                  ?? ruleVersions[0]?.ruleSetVersionId
                  ?? '',
              );
            }}
            disabled={running}
          >
            {showCreateScenario ? '取消' : '新建场景'}
          </button>
          <label className="dash-strategy-select">
            <span>主计划策略</span>
            <select
              className="input"
              value={selectedStrategyId}
              onChange={(e) => setSelectedStrategyId(e.target.value)}
              disabled={running || strategies.length === 0}
            >
              {strategies.length === 0 ? (
                <option value="">暂无策略，请先到优化目标配置</option>
              ) : (
                strategies.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                    {s.isDefault ? '（默认）' : ''}
                  </option>
                ))
              )}
            </select>
          </label>
          {selectedStrategy && (
            <span className="dash-strategy-hint">
              产能：{CAPACITY_STRATEGY_LABELS[selectedStrategy.capacityStrategy]}
            </span>
          )}
          {runScenario && (
            <span className="dash-strategy-hint" title="该场景绑定的规则版本">
              规则：{runScenario.ruleSetVersionName ?? runScenario.ruleSetVersionId}
            </span>
          )}
          <button
            type="button"
            className="btn"
            onClick={() => setShowMpDiagnostics((v) => !v)}
            disabled={!selectedStrategyId}
          >
            {showMpDiagnostics ? '收起推演诊断' : '推演诊断'}
          </button>
          <button
            type="button"
            className="btn primary"
            onClick={() => void runPlanning()}
            disabled={loading || running || !selectedStrategyId}
          >
            {running ? '运行中…' : '执行计划运行'}
          </button>
        </div>
        {showMpDiagnostics && (
          <section className="card dash-diagnostics-panel">
            <h3 className="dash-diagnostics-title">主计划推演诊断（S04 实时预览）</h3>
            <div className="dash-diagnostics-options">
              <label className="dash-check">
                <input
                  type="checkbox"
                  checked={diagUseFeedbackOverlay}
                  onChange={(e) => setDiagUseFeedbackOverlay(e.target.checked)}
                />
                反馈 overlay 预览
              </label>
              <label className="dash-diagnostics-cutoff">
                <span>反馈截止日</span>
                <input
                  type="date"
                  className="input"
                  value={diagFeedbackCutoff}
                  onChange={(e) => setDiagFeedbackCutoff(e.target.value)}
                  disabled={!diagUseFeedbackOverlay}
                />
              </label>
            </div>
            <PlanningDiagnosticsPanel
              layer="master-plan"
              contextId={selectedStrategyId}
              feedbackCutoff={diagUseFeedbackOverlay ? diagFeedbackCutoff : null}
              autoLoad
            />
          </section>
        )}
        {showCreateScenario && (
          <div className="dash-create-scenario card">
            <h4>新建计划场景</h4>
            <div className="dash-create-scenario-fields">
              <label>
                场景名称
                <input
                  className="input"
                  value={newScenarioName}
                  onChange={(e) => setNewScenarioName(e.target.value)}
                  placeholder="例如：旺季方案 A"
                />
              </label>
              <label>
                规则版本
                <select
                  className="input"
                  value={newScenarioRuleId}
                  onChange={(e) => setNewScenarioRuleId(e.target.value)}
                >
                  {ruleVersions.map((r) => (
                    <option key={r.ruleSetVersionId} value={r.ruleSetVersionId}>
                      {r.name}
                      {r.isDefault ? '（默认）' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <p className="dash-create-scenario-hint">
                将使用上方所选主计划策略；规则版本可在 <Link to="/business-rules/capacity">业务规则</Link> 中维护。
              </p>
              <button type="button" className="btn primary" onClick={() => void createScenario()} disabled={loading}>
                创建场景
              </button>
            </div>
          </div>
        )}
        <div className="dash-run-options">
          <label className="dash-check">
            <input
              type="checkbox"
              checked={includeDetailSchedule}
              onChange={(e) => {
                setIncludeDetailSchedule(e.target.checked);
                if (!e.target.checked) setRefreshAfterSchedule(false);
              }}
              disabled={running}
            />
            主计划后继续详细排程
          </label>
          <label className="dash-check">
            <input
              type="checkbox"
              checked={refreshAfterSchedule}
              onChange={(e) => setRefreshAfterSchedule(e.target.checked)}
              disabled={running || !includeDetailSchedule}
            />
            排程后按反馈滚动更新主计划
          </label>
        </div>
        <p className="dash-detail-meta">
          策略在 <Link to="/master-plan/objectives">优化目标</Link> 中维护（含产能模式与目标权重）。
          未勾选排程时，主计划完成后请到 <Link to="/master-plan/analysis">计划分析</Link> 查看工单并发布，再到{' '}
          <Link to="/scheduling/detail-schedule">生产排程</Link> 求解。
        </p>
        <div className="dash-plan-meta">
          <span>
            运行场景：<strong>{runScenario?.name ?? '—'}</strong>
          </span>
          <span>
            生效主计划：<strong>{runScenario?.currentPlanVersionId ?? masterPlan?.planVersionId ?? '—'}</strong>
          </span>
          {runScenario?.previousPlanVersionId && (
            <span>
              上一版：<strong className="mono">{runScenario.previousPlanVersionId}</strong>
            </span>
          )}
        </div>
        <FilterableTable
          tableId="plan-run-pipeline"
          wrapClassName="dash-detail-table-wrap ft-table-wrap"
          rows={pipelineRuns}
          rowKey={(row) => row.runId}
          emptyText="暂无运行记录"
          onRowClick={selectRun}
          getRowClassName={(row) => (selectedRunId === row.runId ? 'is-selected' : '')}
          columns={[
            { key: 'runId', header: '运行编号', className: 'mono', render: (row) => row.runId },
            { key: 'strategy', header: '策略', render: (row) => runStrategyLabel(row) },
            {
              key: 'status',
              header: '状态',
              render: (row) => <span className={runStatusBadge(row.status)}>{row.status}</span>,
            },
            { key: 'startedAt', header: '开始时间', render: (row) => fmtDateTime(row.startedAt) },
            { key: 'finishedAt', header: '结束时间', render: (row) => fmtDateTime(row.finishedAt) },
            {
              key: 'duration',
              header: '耗时',
              render: (row) => (row.durationMs != null ? `${(row.durationMs / 1000).toFixed(1)}s` : '—'),
            },
            {
              key: 'planVersion',
              header: '主计划版本',
              className: 'mono',
              render: (row) => row.masterPlanVersionId ?? '—',
            },
            {
              key: 'score',
              header: 'Score',
              className: 'mono',
              render: (row) => row.masterPlanScore ?? '—',
            },
            {
              key: 'error',
              header: '错误信息',
              className: 'dash-error-cell',
              render: (row) => (
                <span title={row.errorMessage ?? undefined}>{row.errorMessage ?? '—'}</span>
              ),
            },
          ]}
        />
        <div className="dash-run-log-panel">
          <div className="dash-run-log-head">
            <h4>运行日志</h4>
            <span className="dash-run-log-meta">
              {selectedRunId ? `运行 ${selectedRunId}` : '点击上方记录查看日志'}
              {running ? ' · 刷新中…' : ''}
            </span>
          </div>
          <div className="dash-run-log-body" aria-live="polite">
            {runLogs.length === 0 ? (
              <p className="dash-run-log-empty">暂无日志</p>
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
        {selectedRun?.diagnostics && (
          <div className="dash-run-diagnostics">
            <h4>推演诊断（运行记录）</h4>
            {selectedRun.diagnostics.masterPlan && (
              <PlanningDiagnosticsPanel
                layer="master-plan"
                snapshot={selectedRun.diagnostics.masterPlan}
                readOnly
                compact
              />
            )}
            {selectedRun.diagnostics.detailSchedule && (
              <PlanningDiagnosticsPanel
                layer="detail-schedule"
                snapshot={selectedRun.diagnostics.detailSchedule}
                readOnly
                compact
              />
            )}
          </div>
        )}
      </section>
    </div>
  );
}
