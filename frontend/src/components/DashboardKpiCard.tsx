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

/** 首页紧凑 KPI：数字 + 细进度条，无圆环/副图表 */
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

  return (
    <button
      type="button"
      className={`dash-kpi-strip-item ${active ? 'is-active' : ''}`}
      onClick={() => onSelect(id)}
      style={{ '--kpi-color': color } as CSSProperties}
      title={`${label} ${pct.toFixed(1)}% · ${subLabel}`}
    >
      <span className="dash-kpi-strip-label">{label}</span>
      <span className="dash-kpi-strip-value">{pct.toFixed(1)}%</span>
      <span className="dash-kpi-strip-bar" aria-hidden>
        <span className="dash-kpi-strip-bar-fill" style={{ width: `${pct}%` }} />
      </span>
      <span className="dash-kpi-strip-sub">{subLabel}</span>
    </button>
  );
}
