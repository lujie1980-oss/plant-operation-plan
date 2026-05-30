import { useState } from 'react';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import type { KpiMetric, PlanVersionCompare } from '../types/api';
export function KpiPage() {
  const [metrics, setMetrics] = useState<KpiMetric[]>([]);
  const [compare, setCompare] = useState<PlanVersionCompare | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');

  const loadKpi = async () => {
    setLoading(true);
    setError(null);
    try {
      const report = await api.kpiReport();
      setMetrics(report.metrics);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const doCompare = async () => {
    if (!fromId || !toId) {
      setError('请填写对比的两个版本号');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setCompare(await api.comparePlans(fromId, toId));
    } catch (e) {
      setError(e instanceof Error ? e.message : '对比失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="S07 KPI 与版本对比"
        description="计划绩效指标与版本差异摘要"
        actions={
          <button type="button" className="btn primary" onClick={() => void loadKpi()} disabled={loading}>
            刷新 KPI
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} />
      <div className="kpi-grid">
        {metrics.map((m) => (
          <div key={m.metricId} className="kpi-card">
            <span className="kpi-id">{m.metricId}</span>
            <span className="kpi-value">
              {m.value.toLocaleString(undefined, { maximumFractionDigits: 2 })}
              <small>{m.unit}</small>
            </span>
          </div>
        ))}
      </div>
      {metrics.length === 0 && <p className="empty">点击「刷新 KPI」加载指标</p>}
      <section className="card">
        <h3>计划版本对比</h3>
        <div className="form-row inline">
          <input className="input" placeholder="源版本" value={fromId} onChange={(e) => setFromId(e.target.value)} />
          <input
            className="input"
            placeholder="目标版本"
            value={toId}
            onChange={(e) => setToId(e.target.value)}
          />
          <button type="button" className="btn" onClick={() => void doCompare()} disabled={loading}>
            对比
          </button>
        </div>
        {compare && (
          <ul className="info-list">
            <li>{compare.fromVersionId} → {compare.toVersionId}</li>
            <li>得分 {compare.fromScore} → {compare.toScore}</li>
            {compare.impactSummary.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
