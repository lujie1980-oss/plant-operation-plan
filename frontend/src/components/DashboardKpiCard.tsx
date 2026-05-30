import type { CSSProperties } from 'react';
import './DashboardKpiCard.css';

export type DashboardKpiId = 'fulfillment' | 'capacity' | 'shortage';

interface DashboardKpiCardProps {
  id: DashboardKpiId;
  label: string;
  valuePct: number;
  subLabel: string;
  active: boolean;
  color: string;
  onSelect: (id: DashboardKpiId) => void;
}

export function DashboardKpiCard({
  id,
  label,
  valuePct,
  subLabel,
  active,
  color,
  onSelect,
}: DashboardKpiCardProps) {
  const pct = Math.min(100, Math.max(0, valuePct));
  const rest = 100 - pct;

  return (
    <button
      type="button"
      className={`dash-kpi-card ${active ? 'is-active' : ''}`}
      onClick={() => onSelect(id)}
      style={{ '--kpi-color': color } as CSSProperties}
    >
      <div className="dash-kpi-head">
        <span className="dash-kpi-label">{label}</span>
        <span className="dash-kpi-value">{pct.toFixed(1)}%</span>
      </div>
      <div className="dash-kpi-charts">
        <div
          className="dash-kpi-donut"
          style={{
            background: `conic-gradient(${color} 0 ${pct}%, #e2e8f0 ${pct}% 100%)`,
          }}
          title={`${label} ${pct}%`}
        />
        <div className="dash-kpi-bars" aria-hidden>
          <div className="dash-kpi-bar-track">
            <div className="dash-kpi-bar-fill" style={{ width: `${pct}%`, background: color }} />
          </div>
          <div className="dash-kpi-bar-legend">
            <span style={{ color }}>达成 {pct}%</span>
            <span className="muted">缺口 {rest.toFixed(0)}%</span>
          </div>
        </div>
      </div>
      <p className="dash-kpi-sub">{subLabel}</p>
    </button>
  );
}
