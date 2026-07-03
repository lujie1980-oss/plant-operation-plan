import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { KpiBreakdown, KpiDomainScore, MasterPlanBusinessKpi } from '../types/masterPlanKpis';
import './MasterPlanBusinessKpiPanel.css';

export interface MasterPlanBusinessKpiPanelProps {
  planVersionId?: string | null;
  /** When set, only render these KPI-MP-B* ids (order preserved from API). */
  filterKpiIds?: string[];
  showBreakdown?: boolean;
  className?: string;
  title?: string;
}

const DOMAIN_LABELS: Record<string, string> = {
  delivery: '交付',
  material: '物料',
  capacity: '产能',
  supply: '供应',
  preference: '偏好',
};

function formatKpiValue(kpi: MasterPlanBusinessKpi): string {
  if (kpi.unit === '%') {
    return kpi.value.toLocaleString(undefined, { maximumFractionDigits: 1 });
  }
  if (kpi.unit === 'ms' || kpi.unit === '单' || kpi.unit === '个' || kpi.unit === '道') {
    return Math.round(kpi.value).toLocaleString();
  }
  return kpi.value.toLocaleString(undefined, { maximumFractionDigits: 1 });
}

function domainRows(breakdown: KpiBreakdown): KpiDomainScore[] {
  return [
    breakdown.delivery,
    breakdown.material,
    breakdown.capacity,
    breakdown.supply,
    breakdown.preference,
  ];
}

export function MasterPlanBusinessKpiPanel({
  planVersionId,
  filterKpiIds,
  showBreakdown = false,
  className = '',
  title = '主计划业务 KPI',
}: MasterPlanBusinessKpiPanelProps) {
  const [businessKpis, setBusinessKpis] = useState<MasterPlanBusinessKpi[]>([]);
  const [totalKpi, setTotalKpi] = useState<number | null>(null);
  const [scoreSummary, setScoreSummary] = useState<string | null>(null);
  const [kpiBreakdown, setKpiBreakdown] = useState<KpiBreakdown | null>(null);
  const [loading, setLoading] = useState(false);
  const [breakdownOpen, setBreakdownOpen] = useState(showBreakdown);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      if (!planVersionId?.trim()) {
        setBusinessKpis([]);
        setTotalKpi(null);
        setScoreSummary(null);
        setKpiBreakdown(null);
        return;
      }
      setLoading(true);
      try {
        const data = await api.getMasterPlanKpis(planVersionId.trim());
        if (!cancelled) {
          setBusinessKpis(data.businessKpis ?? []);
          setTotalKpi(data.totalKpi ?? null);
          setScoreSummary(data.scoreSummary ?? null);
          setKpiBreakdown(data.kpiBreakdown ?? null);
        }
      } catch {
        if (!cancelled) {
          setBusinessKpis([]);
          setTotalKpi(null);
          setScoreSummary(null);
          setKpiBreakdown(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [planVersionId]);

  const visibleKpis = useMemo(() => {
    if (!filterKpiIds?.length) return businessKpis;
    const allowed = new Set(filterKpiIds);
    return businessKpis.filter((k) => allowed.has(k.kpiId));
  }, [businessKpis, filterKpiIds]);

  const panelClass = ['mp-business-kpi-panel', 'card', className].filter(Boolean).join(' ');

  return (
    <aside className={panelClass}>
      <h3 className="panel-title">{title}</h3>
      {(totalKpi != null || scoreSummary) && (
        <p className="mp-kpi-score-meta muted-text">
          {totalKpi != null && <span>Total KPI {totalKpi}</span>}
          {scoreSummary && <span>{totalKpi != null ? ' · ' : ''}{scoreSummary}</span>}
        </p>
      )}
      <div className="panel-scroll kpi-scroll">
        <ul className="kpi-list">
          {visibleKpis.map((k) => (
            <li key={k.kpiId} className={`kpi-item severity-${k.severity}`}>
              <span className="kpi-item-label">{k.name}</span>
              <span className="kpi-item-value">
                {formatKpiValue(k)}
                <small>{k.unit}</small>
              </span>
            </li>
          ))}
        </ul>
        {visibleKpis.length === 0 && !loading && (
          <p className="empty">{planVersionId ? '暂无业务 KPI' : '请先运行主计划'}</p>
        )}
        {loading && visibleKpis.length === 0 && <p className="empty">加载中…</p>}
      </div>
      {kpiBreakdown && (
        <div className="mp-kpi-breakdown">
          <button
            type="button"
            className="mp-kpi-breakdown-toggle"
            onClick={() => setBreakdownOpen((v) => !v)}
          >
            {breakdownOpen ? '收起' : '展开'}求解域分解
          </button>
          {breakdownOpen && (
            <table className="mp-kpi-domain-table">
              <thead>
                <tr>
                  <th>域</th>
                  <th>hard</th>
                  <th>soft</th>
                </tr>
              </thead>
              <tbody>
                {domainRows(kpiBreakdown).map((row) => (
                  <tr key={row.domain}>
                    <td>{DOMAIN_LABELS[row.domain] ?? row.domain}</td>
                    <td>{row.hard}</td>
                    <td>{row.soft}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </aside>
  );
}
