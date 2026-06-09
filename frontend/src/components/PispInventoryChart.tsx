import type { PispPeriodSnapshotDto } from '../types/ontology';
import './PispInventoryChart.css';

interface PispInventoryChartProps {
  snapshots: PispPeriodSnapshotDto[];
}

const CHART_W = 920;
const CHART_H = 280;
const MARGIN = { top: 20, right: 20, bottom: 56, left: 56 };

function clampDomain(values: number[]) {
  if (values.length === 0) return { min: -1, max: 1 };
  let min = Math.min(...values, 0);
  let max = Math.max(...values, 0);
  if (min === max) {
    min -= 1;
    max += 1;
  }
  const pad = (max - min) * 0.1;
  return { min: min - pad, max: max + pad };
}

export function PispInventoryChart({ snapshots }: PispInventoryChartProps) {
  if (snapshots.length === 0) {
    return <p className="empty">暂无周期快照数据</p>;
  }

  const innerW = CHART_W - MARGIN.left - MARGIN.right;
  const innerH = CHART_H - MARGIN.top - MARGIN.bottom;
  const values = snapshots.map((s) => s.plannedInventoryLevel);
  const domain = clampDomain(values);
  const xStep = snapshots.length > 1 ? innerW / (snapshots.length - 1) : 0;
  const yAt = (v: number) => {
    const ratio = (v - domain.min) / (domain.max - domain.min);
    return MARGIN.top + (1 - ratio) * innerH;
  };
  const xAt = (idx: number) => MARGIN.left + idx * xStep;
  const linePath = snapshots
    .map((snapshot, idx) => `${idx === 0 ? 'M' : 'L'} ${xAt(idx)} ${yAt(snapshot.plannedInventoryLevel)}`)
    .join(' ');
  const zeroY = yAt(0);
  const yTicks = 5;
  const xTickStep = Math.max(1, Math.ceil(snapshots.length / 8));

  return (
    <div className="pisp-inventory-chart">
      <svg viewBox={`0 0 ${CHART_W} ${CHART_H}`} preserveAspectRatio="none" role="img">
        <title>PISP 库存推演曲线</title>
        <rect x="0" y="0" width={CHART_W} height={CHART_H} fill="#ffffff" />

        {Array.from({ length: yTicks + 1 }, (_, i) => {
          const v = domain.min + ((domain.max - domain.min) * i) / yTicks;
          const y = yAt(v);
          return (
            <g key={`y-${i}`}>
              <line
                x1={MARGIN.left}
                y1={y}
                x2={CHART_W - MARGIN.right}
                y2={y}
                className="pisp-inventory-chart-grid"
              />
              <text x={MARGIN.left - 8} y={y + 4} textAnchor="end" className="pisp-inventory-chart-axis-label">
                {v.toFixed(0)}
              </text>
            </g>
          );
        })}

        <line
          x1={MARGIN.left}
          y1={zeroY}
          x2={CHART_W - MARGIN.right}
          y2={zeroY}
          className="pisp-inventory-chart-zero"
        />

        <path d={linePath} fill="none" className="pisp-inventory-chart-line" />

        {snapshots.map((snapshot, idx) => {
          const shortage = snapshot.stockShortageQuantity > 0;
          if (!shortage) return null;
          return (
            <circle
              key={snapshot.id}
              cx={xAt(idx)}
              cy={yAt(snapshot.plannedInventoryLevel)}
              r={4}
              className="pisp-inventory-chart-shortage"
            />
          );
        })}

        {snapshots.map((snapshot, idx) => {
          if (idx % xTickStep !== 0 && idx !== snapshots.length - 1) {
            return null;
          }
          const x = xAt(idx);
          const tickLabel = snapshot.periodId.length > 12 ? snapshot.periodId.slice(-12) : snapshot.periodId;
          return (
            <g key={`x-${snapshot.id}`}>
              <line
                x1={x}
                y1={CHART_H - MARGIN.bottom}
                x2={x}
                y2={CHART_H - MARGIN.bottom + 6}
                className="pisp-inventory-chart-grid"
              />
              <text
                x={x}
                y={CHART_H - MARGIN.bottom + 20}
                textAnchor="middle"
                className="pisp-inventory-chart-axis-label"
              >
                {tickLabel}
              </text>
            </g>
          );
        })}

        <text
          x={MARGIN.left + innerW / 2}
          y={CHART_H - 10}
          textAnchor="middle"
          className="pisp-inventory-chart-caption"
        >
          Period
        </text>
        <text
          x={16}
          y={MARGIN.top + innerH / 2}
          textAnchor="middle"
          transform={`rotate(-90 16 ${MARGIN.top + innerH / 2})`}
          className="pisp-inventory-chart-caption"
        >
          Planned Inventory
        </text>
      </svg>
      <div className="pisp-inventory-chart-legend">
        <span className="pisp-inventory-chart-legend-line">库存曲线</span>
        <span className="pisp-inventory-chart-legend-shortage">缺货风险点</span>
      </div>
    </div>
  );
}
