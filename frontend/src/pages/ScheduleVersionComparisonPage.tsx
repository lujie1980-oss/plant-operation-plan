import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable, type TableColumnDef } from '../components/table/FilterableTable';
import type {
  DetailScheduleVersionSummary,
  ScenarioComparison,
  ScenarioMetric,
} from '../types/api';
import './ScenarioComparisonPage.css';

const VERSION_COLORS = ['#2563eb', '#7c3aed', '#059669', '#d97706', '#dc2626', '#0891b2'];

function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatValue(metricId: string, value: number, unit: string): string {
  if (unit === '%') return `${value.toFixed(1)}%`;
  if (metricId.includes('score')) return String(Math.round(value));
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
          const color = VERSION_COLORS[idx % VERSION_COLORS.length];
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
          const color = VERSION_COLORS[idx % VERSION_COLORS.length];
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

export function ScheduleVersionComparisonPage() {
  const [versions, setVersions] = useState<DetailScheduleVersionSummary[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [comparison, setComparison] = useState<ScenarioComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const [comparing, setComparing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadVersions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setVersions(await api.listDetailScheduleVersions(50));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载排程版本失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadVersions();
  }, [loadVersions]);

  const selectedIds = useMemo(() => Array.from(selected), [selected]);

  useEffect(() => {
    if (selectedIds.length === 0) {
      setComparison(null);
      return;
    }
    let cancelled = false;
    setComparing(true);
    void api
      .compareDetailScheduleVersions(selectedIds)
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
  const scheduleMetrics =
    comparison?.metrics.filter((m) => m.metricId.startsWith('ds_')) ?? [];

  const kpiColumns = useMemo((): TableColumnDef<ScenarioMetric>[] => {
    const cols: TableColumnDef<ScenarioMetric>[] = [
      { key: 'label', header: '指标', render: (metric) => metric.label },
    ];
    selectedIds.forEach((id) => {
      const v = versions.find((x) => x.planVersionId === id);
      cols.push({
        key: id,
        header: v ? fmtDateTime(v.generatedAt) : id,
        render: (metric) => {
          const point = comparison?.series.find(
            (x) => x.planVersionId === id && x.metricId === metric.metricId,
          );
          return point != null ? formatValue(metric.metricId, point.value, metric.unit) : '—';
        },
      });
    });
    return cols;
  }, [selectedIds, versions, comparison]);

  return (
    <div className="scenario-page">
      <PageHeader
        title="排程版本对比"
        showScheduleVersionSelector
        description="勾选多次生产排程求解产生的版本，对比 Score、工序规模与求解耗时。"
        actions={
          <button type="button" className="btn" onClick={() => void loadVersions()} disabled={loading}>
            刷新版本
          </button>
        }
      />
      <StatusBanner loading={loading || comparing} error={error} />

      <div className="scn-layout">
        <aside className="scn-list card">
          <h3>排程版本</h3>
          <p className="scn-list-hint">勾选 2 个及以上版本以生成对比图表</p>
          <ul className="scn-scenario-list">
            {versions.length === 0 ? (
              <li className="scn-empty">暂无排程版本，请先在「生产排程」执行求解</li>
            ) : (
              versions.map((v) => (
                <li key={v.planVersionId}>
                  <label className="scn-scenario-item">
                    <input
                      type="checkbox"
                      checked={selected.has(v.planVersionId)}
                      onChange={() => toggle(v.planVersionId)}
                    />
                    <span className="scn-scenario-body">
                      <strong className="mono">{v.planVersionId}</strong>
                      <span className="scn-scenario-meta">{fmtDateTime(v.generatedAt)}</span>
                      <span className="scn-scenario-meta">
                        Score {v.score ?? '—'} · {v.operationCount} 工序 · {v.workOrderCount} 工单
                        {v.batchCount > 0 ? ` · ${v.batchCount} 批次` : ''}
                      </span>
                      {v.solveDurationMs != null && (
                        <span className="scn-scenario-meta">
                          求解 {(v.solveDurationMs / 1000).toFixed(1)}s
                        </span>
                      )}
                    </span>
                  </label>
                </li>
              ))
            )}
          </ul>
        </aside>

        <section className="scn-charts">
          {selectedIds.length === 0 ? (
            <div className="card scn-placeholder">
              <p>请在左侧勾选一个或多个排程版本</p>
            </div>
          ) : comparison ? (
            <>
              <div className="card scn-kpi-table-wrap">
                <h3>KPI 对比表</h3>
                <FilterableTable
                  tableId="schedule-version-comparison-kpi"
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
                <h3>排程规模 KPI</h3>
                <div className="scn-chart-grid">
                  {scheduleMetrics.map((m) => (
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
