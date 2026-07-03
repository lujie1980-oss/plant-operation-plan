import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
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
  if (unit === 'ms') return `${Math.round(value)}ms`;
  if (unit === '天') return `${value.toFixed(1)} 天`;
  return `${Math.round(value)} ${unit}`;
}

function formatDelta(metricId: string, delta: number, unit: string): string {
  if (unit === '%') return `${delta >= 0 ? '+' : ''}${delta.toFixed(1)}%`;
  if (metricId.startsWith('mp_score')) return `${delta >= 0 ? '+' : ''}${Math.round(delta)}`;
  if (unit === '秒') return `${delta >= 0 ? '+' : ''}${delta.toFixed(1)}s`;
  if (unit === 'ms') return `${delta >= 0 ? '+' : ''}${Math.round(delta)}ms`;
  if (unit === '天') return `${delta >= 0 ? '+' : ''}${delta.toFixed(1)} 天`;
  return `${delta >= 0 ? '+' : ''}${Math.round(delta)} ${unit}`;
}

function scenarioVersionId(s: PlanningScenario): string | undefined {
  const id = s.currentPlanVersionId ?? s.planVersionId;
  return id ?? undefined;
}

function metricValue(
  comparison: ScenarioComparison | null,
  planVersionId: string,
  metricId: string,
): number | null {
  const point = comparison?.series.find(
    (x) => x.planVersionId === planVersionId && x.metricId === metricId,
  );
  return point != null ? point.value : null;
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
  description = '勾选多个订单协同计划场景，对比 Score、COLD 交付、§15 B01~B10、产能与排产 KPI',
  emptyHint = '暂无订单协同计划场景，请先在「计划运行」执行计划运行',
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
  const deliveryMetrics = comparison?.metrics.filter((m) => m.metricId.startsWith('cold_')) ?? [];
  const businessMetrics = comparison?.metrics.filter((m) => m.metricId.startsWith('mp_b')) ?? [];
  const capacityMetrics =
    comparison?.metrics.filter((m) => m.metricId.startsWith('cap_')) ?? [];
  const planMetrics =
    comparison?.metrics.filter(
      (m) =>
        m.metricId === 'mp_total_wo'
        || m.metricId === 'mp_total_load'
        || m.metricId === 'solve_duration',
    ) ?? [];

  const baselineId = selectedIds[0];

  const kpiColumns = useMemo((): TableColumnDef<ScenarioMetric>[] => {
    const cols: TableColumnDef<ScenarioMetric>[] = [
      { key: 'label', header: '指标', render: (metric) => metric.label },
    ];
    selectedIds.forEach((id, idx) => {
      const s = scenarios.find((x) => scenarioVersionId(x) === id);
      const isBaseline = id === baselineId;
      cols.push({
        key: id,
        header: isBaseline ? `${s?.name ?? id}（基线）` : (s?.name ?? id),
        className: `scn-td-scenario scn-td-scenario-${idx % SCENARIO_COLORS.length}`,
        render: (metric) => {
          const value = metricValue(comparison, id, metric.metricId);
          if (value == null) return '—';
          const main = formatValue(metric.metricId, value, metric.unit);
          if (isBaseline || !baselineId) {
            return <span className="scn-val-baseline">{main}</span>;
          }
          const baseVal = metricValue(comparison, baselineId, metric.metricId);
          if (baseVal == null) return main;
          const delta = value - baseVal;
          if (Math.abs(delta) < 0.0001) {
            return <span className="scn-val-same">{main}</span>;
          }
          return (
            <span className="scn-val-wrap">
              <span className="scn-val-main">{main}</span>
              <span className="scn-val-delta">{formatDelta(metric.metricId, delta, metric.unit)}</span>
            </span>
          );
        },
      });
    });
    return cols;
  }, [selectedIds, scenarios, comparison, baselineId]);

  return (
    <div className="scenario-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
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
          <p className="scn-list-hint">勾选场景；首列为 KPI 基线，其余列显示差异</p>
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

        <section className="scn-main">
          {selectedIds.length === 0 ? (
            <div className="card scn-placeholder">
              <p>请在左侧勾选一个或多个订单协同计划场景</p>
            </div>
          ) : comparison ? (
            <>
              <div className="card scn-kpi-table-wrap">
                <div className="scn-kpi-table-head">
                  <h3>KPI 对比表</h3>
                  <span className="scn-kpi-table-meta">
                    {selectedIds.length} 个场景 · 基线：
                    {scenarios.find((s) => scenarioVersionId(s) === baselineId)?.name ?? baselineId}
                  </span>
                </div>
                <FilterableTable
                  tableId="scenario-comparison-kpi"
                  tableClassName="scn-kpi-table data-table"
                  wrapClassName="scn-kpi-table-scroll ft-table-wrap"
                  rows={comparison.metrics}
                  rowKey={(metric) => metric.metricId}
                  columns={kpiColumns}
                />
              </div>

              <details className="scn-charts-details">
                <summary>图表对比（Score / COLD 交付 / §15 业务 / 产能 / 排产）</summary>
                <div className="scn-charts-details-body">
                  <div className="scn-chart-section">
                    <h4>Score</h4>
                    <div className="scn-chart-grid scn-chart-grid--compact">
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
                    <h4>COLD 交付 KPI</h4>
                    <div className="scn-chart-grid scn-chart-grid--compact">
                      {deliveryMetrics.map((m) => (
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
                    <h4>§15 业务 KPI（B01~B10）</h4>
                    <div className="scn-chart-grid scn-chart-grid--compact">
                      {businessMetrics.map((m) => (
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
                    <h4>产能 KPI</h4>
                    <div className="scn-chart-grid scn-chart-grid--compact">
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
                    <h4>订单协同计划排产</h4>
                    <div className="scn-chart-grid scn-chart-grid--compact">
                      {planMetrics
                        .filter((m) => m.metricId !== 'solve_duration')
                        .map((m) => (
                          <BarChart
                            key={m.metricId}
                            metric={m}
                            comparison={comparison}
                            selectedIds={selectedIds}
                          />
                        ))}
                      {planMetrics
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
                </div>
              </details>
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
