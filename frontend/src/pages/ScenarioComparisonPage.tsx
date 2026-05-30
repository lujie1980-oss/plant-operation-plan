import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable, type TableColumnDef } from '../components/table/FilterableTable';
import type { MasterPlanCapacityStrategy, PlanningScenario, ScenarioComparison, ScenarioMetric } from '../types/api';
import { CAPACITY_STRATEGY_LABELS } from '../types/masterPlanStrategies';
import './ScenarioComparisonPage.css';

const SCENARIO_COLORS = ['#2563eb', '#7c3aed', '#059669', '#d97706', '#dc2626', '#0891b2'];

function capacityLabel(strategy: string): string {
  return CAPACITY_STRATEGY_LABELS[strategy as MasterPlanCapacityStrategy] ?? strategy;
}

function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatValue(metricId: string, value: number, unit: string): string {
  if (unit === '%') return `${value.toFixed(1)}%`;
  if (metricId.startsWith('mp_score')) return String(Math.round(value));
  if (unit === '秒') return `${value.toFixed(1)}s`;
  return `${Math.round(value)} ${unit}`;
}

function seriesForMetric(comparison: ScenarioComparison | null, metricId: string) {
  if (!comparison) return [];
  return comparison.series.filter((s) => s.metricId === metricId);
}

function BarChart({
  metric,
  comparison,
  selectedIds,
}: {
  metric: ScenarioMetric;
  comparison: ScenarioComparison;
  selectedIds: string[];
}) {
  const points = seriesForMetric(comparison, metric.metricId);
  const ordered = selectedIds
    .map((id) => points.find((p) => p.planVersionId === id))
    .filter((p): p is NonNullable<typeof p> => p != null);
  if (ordered.length === 0) return null;

  const values = ordered.map((p) => p.value);
  const maxAbs = Math.max(...values.map((v) => Math.abs(v)), 1);

  return (
    <div className="scn-chart-card">
      <h4>{metric.label}</h4>
      <div className="scn-bar-chart">
        {ordered.map((p, idx) => {
          const pct = (Math.abs(p.value) / maxAbs) * 100;
          const color = SCENARIO_COLORS[idx % SCENARIO_COLORS.length];
          return (
            <div key={p.planVersionId} className="scn-bar-row">
              <span className="scn-bar-label" title={p.scenarioLabel}>
                {p.scenarioLabel}
              </span>
              <div className="scn-bar-track">
                <div
                  className="scn-bar-fill"
                  style={{ width: `${pct}%`, backgroundColor: color }}
                />
              </div>
              <span className="scn-bar-value">{formatValue(metric.metricId, p.value, metric.unit)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function GroupedColumnChart({
  metric,
  comparison,
  selectedIds,
}: {
  metric: ScenarioMetric;
  comparison: ScenarioComparison;
  selectedIds: string[];
}) {
  const points = seriesForMetric(comparison, metric.metricId);
  const ordered = selectedIds
    .map((id) => points.find((p) => p.planVersionId === id))
    .filter((p): p is NonNullable<typeof p> => p != null);
  if (ordered.length === 0) return null;

  const maxVal = Math.max(...ordered.map((p) => Math.abs(p.value)), 1);

  return (
    <div className="scn-chart-card scn-column-card">
      <h4>{metric.label}（柱状对比）</h4>
      <div className="scn-column-chart">
        {ordered.map((p, idx) => {
          const h = (Math.abs(p.value) / maxVal) * 100;
          const color = SCENARIO_COLORS[idx % SCENARIO_COLORS.length];
          return (
            <div key={p.planVersionId} className="scn-column">
              <div className="scn-column-bar-wrap">
                <div
                  className="scn-column-bar"
                  style={{ height: `${h}%`, backgroundColor: color }}
                  title={formatValue(metric.metricId, p.value, metric.unit)}
                />
              </div>
              <span className="scn-column-val">{formatValue(metric.metricId, p.value, metric.unit)}</span>
              <span className="scn-column-label" title={p.scenarioLabel}>
                {p.planVersionId}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function ScenarioComparisonPage({
  title = '场景对比',
  description = '勾选多个主计划场景，对比 Score、产能与排产关键 KPI',
  emptyHint = '暂无主计划场景，请先在「计划运行」执行主计划运行',
}: {
  title?: string;
  description?: string;
  emptyHint?: string;
}) {
  const [scenarios, setScenarios] = useState<PlanningScenario[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [comparison, setComparison] = useState<ScenarioComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const [comparing, setComparing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadScenarios = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setScenarios(await api.listScenarios(50));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载场景失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadScenarios();
  }, [loadScenarios]);

  const selectedIds = useMemo(() => Array.from(selected), [selected]);

  useEffect(() => {
    if (selectedIds.length === 0) {
      setComparison(null);
      return;
    }
    let cancelled = false;
    setComparing(true);
    void api
      .compareScenarios(selectedIds)
      .then((data) => {
        if (!cancelled) setComparison(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : '对比失败');
      })
      .finally(() => {
        if (!cancelled) setComparing(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedIds]);

  const toggle = (planVersionId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(planVersionId)) {
        next.delete(planVersionId);
      } else {
        next.add(planVersionId);
      }
      return next;
    });
  };

  const scoreMetrics = comparison?.metrics.filter((m) => m.metricId.includes('score')) ?? [];
  const capacityMetrics =
    comparison?.metrics.filter((m) => m.metricId.startsWith('cap_')) ?? [];
  const planMetrics =
    comparison?.metrics.filter(
      (m) => m.metricId.startsWith('mp_') && !m.metricId.includes('score'),
    ) ?? [];

  const kpiColumns = useMemo((): TableColumnDef<ScenarioMetric>[] => {
    const cols: TableColumnDef<ScenarioMetric>[] = [
      { key: 'label', header: '指标', render: (metric) => metric.label },
    ];
    selectedIds.forEach((id) => {
      const s = scenarios.find((x) => x.planVersionId === id);
      cols.push({
        key: id,
        header: s?.label ?? id,
        render: (metric) => {
          const point = comparison?.series.find(
            (x) => x.planVersionId === id && x.metricId === metric.metricId,
          );
          return point != null ? formatValue(metric.metricId, point.value, metric.unit) : '—';
        },
      });
    });
    return cols;
  }, [selectedIds, scenarios, comparison]);

  return (
    <div className="scenario-page">
      <PageHeader
        title={title}
        description={description}
        actions={
          <button type="button" className="btn" onClick={() => void loadScenarios()} disabled={loading}>
            刷新场景
          </button>
        }
      />
      <StatusBanner loading={loading || comparing} error={error} />

      <div className="scn-layout">
        <aside className="scn-list card">
          <h3>场景列表</h3>
          <p className="scn-list-hint">勾选 2 个及以上场景以生成对比图表</p>
          <ul className="scn-scenario-list">
            {scenarios.length === 0 ? (
              <li className="scn-empty">{emptyHint}</li>
            ) : (
              scenarios.map((s) => {
                const versionId = s.currentPlanVersionId ?? s.planVersionId;
                return (
                <li key={s.scenarioId}>
                  <label className="scn-scenario-item">
                    <input
                      type="checkbox"
                      disabled={!versionId}
                      checked={versionId ? selected.has(versionId) : false}
                      onChange={() => versionId && toggle(versionId)}
                    />
                    <span className="scn-scenario-body">
                      <strong>{s.name}</strong>
                      <span className="scn-scenario-meta mono">{versionId ?? '未运行'}</span>
                      <span className="scn-scenario-meta">
                        {s.strategyName ?? capacityLabel(s.capacityStrategy)} ·{' '}
                        {fmtDateTime(s.currentGeneratedAt ?? s.generatedAt)} · Score{' '}
                        {s.currentScore ?? s.score ?? '—'}
                      </span>
                      {s.runId && <span className="scn-scenario-meta mono">运行 {s.runId}</span>}
                    </span>
                  </label>
                </li>
              );
              })
            )}
          </ul>
        </aside>

        <section className="scn-charts">
          {selectedIds.length === 0 ? (
            <div className="card scn-placeholder">
              <p>请在左侧勾选一个或多个主计划场景</p>
            </div>
          ) : comparison ? (
            <>
              <div className="card scn-kpi-table-wrap">
                <h3>KPI 对比表</h3>
                <FilterableTable
                  tableId="scenario-comparison-kpi"
                  tableClassName="scn-kpi-table"
                  wrapClassName="ft-table-wrap"
                  rows={comparison.metrics}
                  rowKey={(metric) => metric.metricId}
                  columns={kpiColumns}
                />
              </div>

              <div className="scn-chart-section">
                <h3>Score 对比</h3>
                <div className="scn-chart-grid">
                  {scoreMetrics.map((m) => (
                    <GroupedColumnChart
                      key={m.metricId}
                      metric={m}
                      comparison={comparison}
                      selectedIds={selectedIds}
                    />
                  ))}
                </div>
              </div>

              <div className="scn-chart-section">
                <h3>产能 KPI</h3>
                <div className="scn-chart-grid">
                  {capacityMetrics.map((m) => (
                    <BarChart
                      key={m.metricId}
                      metric={m}
                      comparison={comparison}
                      selectedIds={selectedIds}
                    />
                  ))}
                </div>
              </div>

              <div className="scn-chart-section">
                <h3>主计划排产 KPI</h3>
                <div className="scn-chart-grid">
                  {planMetrics.map((m) => (
                    <BarChart
                      key={m.metricId}
                      metric={m}
                      comparison={comparison}
                      selectedIds={selectedIds}
                    />
                  ))}
                  {comparison.metrics
                    .filter((m) => m.metricId === 'solve_duration')
                    .map((m) => (
                      <GroupedColumnChart
                        key={m.metricId}
                        metric={m}
                        comparison={comparison}
                        selectedIds={selectedIds}
                      />
                    ))}
                </div>
              </div>
            </>
          ) : (
            <div className="card scn-placeholder">
              <p>加载对比数据…</p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
