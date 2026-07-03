import { useEffect, useMemo, useState } from 'react';
import { ViewMode } from 'gantt-task-react';
import { api } from '../api/client';
import type { FulfillmentChainNode, SrpCapacityCell } from '../types/api';
import {
  UTILIZATION_BAND_ORDER,
  utilizationBand,
  utilizationBandLabel,
} from '../utils/capacityUtilization';
import { fmtShortTs } from '../utils/formatTiming';
import {
  buildPlanUnitGanttRows,
  type PlanUnitGanttRow,
} from '../utils/supplyOrderPlanUnitGantt';
import './SupplyOrderPlanUnitGantt.css';

const LABEL_W = 220;
const PU_ROW_H = 34;
const OP_ROW_H = 32;
const HEADER_H = 36;
const MS_PER_DAY = 86_400_000;

type GanttRowLayout = { row: PlanUnitGanttRow; top: number; height: number };

function parseTs(ts: string): number {
  return new Date(ts).getTime();
}

function fmtTime(ts: string): string {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '—';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function columnWidth(viewMode: ViewMode): number {
  if (viewMode === ViewMode.Hour) return 48;
  if (viewMode === ViewMode.Week) return 120;
  if (viewMode === ViewMode.Month) return 180;
  return 64;
}

function leftFor(ts: string, axisStartMs: number, span: number, chartWidth: number): number {
  const ms = parseTs(ts);
  if (Number.isNaN(ms)) return 0;
  return ((ms - axisStartMs) / span) * chartWidth;
}

function barGeom(
  startTs: string,
  endTs: string,
  axisStartMs: number,
  span: number,
  chartWidth: number,
): { left: number; width: number } {
  const start = parseTs(startTs);
  const end = parseTs(endTs);
  if (Number.isNaN(start) || Number.isNaN(end)) {
    return { left: 0, width: 4 };
  }
  const left = ((start - axisStartMs) / span) * chartWidth;
  const width = Math.max(4, ((Math.max(end, start + 60_000) - start) / span) * chartWidth);
  return { left, width };
}

function buildSrpCellsByResource(
  cells: SrpCapacityCell[],
): Map<string, Map<string, SrpCapacityCell>> {
  const map = new Map<string, Map<string, SrpCapacityCell>>();
  for (const cell of cells) {
    const byDate = map.get(cell.resourceId) ?? new Map<string, SrpCapacityCell>();
    byDate.set(cell.date, cell);
    map.set(cell.resourceId, byDate);
  }
  return map;
}

function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** 与本体默认 Period 序列（28 天）对齐的兜底计划期 */
function defaultPlanHorizon(): { start: string; end: string } {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 27);
  return { start: toIsoDate(start), end: toIsoDate(end) };
}

function dayCountInclusive(startIso: string, endIso: string): number {
  const startMs = new Date(`${startIso}T00:00:00`).getTime();
  const endMs = new Date(`${endIso}T00:00:00`).getTime();
  if (Number.isNaN(startMs) || Number.isNaN(endMs)) return 1;
  return Math.max(1, Math.round((endMs - startMs) / MS_PER_DAY) + 1);
}

interface SupplyOrderPlanUnitGanttProps {
  node: FulfillmentChainNode | null;
  viewMode: ViewMode;
  planVersionId?: string | null;
}

function TimingTreeBracket({
  rowTop,
  rowHeight,
  epsLeft,
  ldeLeft,
  chartWidth,
  chartHeight,
  epsLabel,
  ldeLabel,
}: {
  rowTop: number;
  rowHeight: number;
  epsLeft: number;
  ldeLeft: number;
  chartWidth: number;
  chartHeight: number;
  epsLabel: string;
  ldeLabel: string;
}) {
  const rowMid = rowTop + rowHeight / 2;
  const left = Math.min(epsLeft, ldeLeft);
  const right = Math.max(epsLeft, ldeLeft);
  const bracketY = rowMid;

  return (
    <svg
      className="so-pu-gantt-timing-tree"
      width={chartWidth}
      height={chartHeight}
      aria-hidden
    >
      <line x1={epsLeft} y1={0} x2={epsLeft} y2={chartHeight} className="timing-tree-vline" />
      <line x1={ldeLeft} y1={0} x2={ldeLeft} y2={chartHeight} className="timing-tree-vline" />
      <path
        d={`M ${epsLeft} ${bracketY} H ${left + 8} V ${rowTop + 4} H ${right - 8} V ${bracketY} H ${ldeLeft}`}
        className="timing-tree-bracket"
        fill="none"
      />
      <title>{`${epsLabel} · ${ldeLabel}`}</title>
    </svg>
  );
}

function CapacityCells({
  resourceId,
  columns,
  cells,
  cellW,
}: {
  resourceId: string;
  columns: { date: string; label: string }[];
  cells: Map<string, SrpCapacityCell>;
  cellW: number;
}) {
  return (
    <div className="so-pu-gantt-cap-layer" aria-hidden>
      {columns.map((col) => {
        const cell = cells.get(col.date);
        if (!cell) {
          return (
            <div
              key={col.date}
              className="so-pu-gantt-cap-cell so-pu-gantt-cap-cell--empty"
              style={{ width: cellW }}
            />
          );
        }
        const band = utilizationBand(cell.utilizationPct);
        return (
          <div
            key={col.date}
            className={`so-pu-gantt-cap-cell band-${band}`}
            style={{ width: cellW }}
            title={`${resourceId} · ${col.date} · SRP 利用率 ${cell.utilizationPct}%（占用 ${cell.reservedMinutes} / 可用 ${cell.availableMinutes} 分）`}
          >
            <span className="so-pu-gantt-cap-pct">{cell.utilizationPct}%</span>
          </div>
        );
      })}
    </div>
  );
}

export function SupplyOrderPlanUnitGantt({ node, viewMode, planVersionId }: SupplyOrderPlanUnitGanttProps) {
  const [selectedOpId, setSelectedOpId] = useState<string | null>(null);
  const [srpCellsByResource, setSrpCellsByResource] = useState<
    Map<string, Map<string, SrpCapacityCell>>
  >(() => new Map());
  const [planHorizon, setPlanHorizon] = useState<{ start: string; end: string } | null>(null);
  const [capacityLoading, setCapacityLoading] = useState(false);

  const rows = useMemo(() => buildPlanUnitGanttRows(node), [node]);

  useEffect(() => {
    if (!planVersionId) {
      setSrpCellsByResource(new Map());
      setPlanHorizon(null);
      return;
    }
    let cancelled = false;
    setCapacityLoading(true);
    void api
      .ontologySrpCapacityGantt(planVersionId)
      .then((data) => {
        if (!cancelled) {
          setSrpCellsByResource(buildSrpCellsByResource(data.cells));
          if (data.horizonStart && data.horizonEnd) {
            setPlanHorizon({ start: data.horizonStart, end: data.horizonEnd });
          } else {
            setPlanHorizon(defaultPlanHorizon());
          }
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSrpCellsByResource(new Map());
          setPlanHorizon(defaultPlanHorizon());
        }
      })
      .finally(() => {
        if (!cancelled) {
          setCapacityLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [planVersionId]);

  const model = useMemo(() => {
    if (rows.length === 0) return null;

    const horizon = planHorizon ?? defaultPlanHorizon();
    const axisStartMs = new Date(`${horizon.start}T00:00:00`).getTime();
    const dayCount = dayCountInclusive(horizon.start, horizon.end);
    const cellW = columnWidth(viewMode);
    const span = dayCount * MS_PER_DAY;
    const chartWidth = dayCount * cellW;

    const columns: { date: string; label: string }[] = [];
    for (let i = 0; i < dayCount; i++) {
      const d = new Date(axisStartMs + i * MS_PER_DAY);
      const iso = toIsoDate(d);
      columns.push({ date: iso, label: `${d.getMonth() + 1}/${d.getDate()}` });
    }

    let y = HEADER_H;
    const rowLayouts: GanttRowLayout[] = [];
    for (const row of rows) {
      const h = row.rowType === 'plan_unit' ? PU_ROW_H : OP_ROW_H;
      rowLayouts.push({ row, top: y, height: h });
      y += h;
    }

    return { axisStartMs, span, chartWidth, cellW, columns, rowLayouts, chartHeight: y, horizon };
  }, [rows, viewMode, planHorizon]);

  if (!node) {
    return (
      <p className="so-pu-gantt-empty">请在上方满足链中选择供应订单（工单）查看工序甘特</p>
    );
  }

  if (!model || rows.length === 0) {
    return <p className="so-pu-gantt-empty">该供应订单暂无 PlanUnit / 工序计划</p>;
  }

  const selectedRow = model.rowLayouts.find(
    (l) => l.row.rowType === 'operation' && l.row.operation?.operationId === selectedOpId,
  );
  const selectedOp = selectedRow?.row.operation;
  const epsLeft =
    selectedOp?.earliestPossibleStartTotal != null
      ? leftFor(selectedOp.earliestPossibleStartTotal, model.axisStartMs, model.span, model.chartWidth)
      : null;
  const ldeLeft =
    selectedOp?.latestDesiredEnd != null
      ? leftFor(selectedOp.latestDesiredEnd, model.axisStartMs, model.span, model.chartWidth)
      : null;

  const totalWidth = LABEL_W + model.chartWidth;

  return (
    <section className="so-pu-gantt">
      <div className="so-pu-gantt-meta">
        <span className="so-pu-gantt-title">{node.label}</span>
        <span className="so-pu-gantt-sub">
          {node.productCode} · {fmtTime(node.startTs)} → {fmtTime(node.endTs)}
          {model.horizon ? ` · 计划期 ${model.horizon.start} → ${model.horizon.end}` : ''}
          {capacityLoading ? ' · 产能加载中…' : ''}
        </span>
      </div>
      <div className="so-pu-gantt-legend">
        <span className="so-pu-gantt-leg so-pu-gantt-leg--pu">PlanUnit 计划条</span>
        <span className="so-pu-gantt-leg so-pu-gantt-leg--op">工序计划条</span>
        <span className="so-pu-gantt-leg so-pu-gantt-leg--tree">选中工序 · 最早可开始 / 最晚要求完成</span>
        {UTILIZATION_BAND_ORDER.map((band) => (
          <span key={band} className={`so-pu-gantt-leg so-pu-gantt-leg--cap band-${band}`}>
            {utilizationBandLabel(band)}
          </span>
        ))}
      </div>
      <div className="so-pu-gantt-scroll panel-scroll">
        <div className="so-pu-gantt-grid" style={{ minWidth: totalWidth, position: 'relative' }}>
          {selectedRow && epsLeft != null && ldeLeft != null && (
            <div
              className="so-pu-gantt-marker-layer"
              style={{
                left: LABEL_W,
                top: 0,
                width: model.chartWidth,
                height: model.chartHeight,
              }}
            >
              <TimingTreeBracket
                rowTop={selectedRow.top}
                rowHeight={selectedRow.height}
                epsLeft={epsLeft}
                ldeLeft={ldeLeft}
                chartWidth={model.chartWidth}
                chartHeight={model.chartHeight}
                epsLabel={`最早可开始 ${fmtShortTs(selectedOp!.earliestPossibleStartTotal!)}`}
                ldeLabel={`最晚要求完成 ${fmtShortTs(selectedOp!.latestDesiredEnd!)}`}
              />
            </div>
          )}

          <div
            className="so-pu-gantt-header"
            style={{ gridTemplateColumns: `${LABEL_W}px ${model.chartWidth}px`, height: HEADER_H }}
          >
            <div className="so-pu-gantt-corner">PlanUnit / 工序</div>
            <div className="so-pu-gantt-axis" style={{ width: model.chartWidth }}>
              {model.columns.map((col) => (
                <div key={col.date} className="so-pu-gantt-day" style={{ width: model.cellW }} title={col.date}>
                  {col.label}
                </div>
              ))}
            </div>
          </div>

          {model.rowLayouts.map(({ row, height }) => {
            const bar = barGeom(row.startTs, row.endTs, model.axisStartMs, model.span, model.chartWidth);
            const isOp = row.rowType === 'operation';
            const isSelected = isOp && row.operation?.operationId === selectedOpId;
            const resourceId = row.operation?.resourceId;
            const showCapacity =
              isOp && resourceId != null && resourceId !== 'UNASSIGNED';
            const capCells = showCapacity
              ? (srpCellsByResource.get(resourceId) ?? new Map<string, SrpCapacityCell>())
              : null;
            return (
              <div
                key={row.rowId}
                className={`so-pu-gantt-row ${isOp ? 'is-operation' : 'is-plan-unit'} ${isSelected ? 'is-selected' : ''}`}
                style={{
                  gridTemplateColumns: `${LABEL_W}px ${model.chartWidth}px`,
                  minHeight: height,
                }}
                onClick={isOp ? () => setSelectedOpId(row.operation!.operationId) : undefined}
              >
                <div className={`so-pu-gantt-label ${isOp ? 'is-op' : 'is-pu'}`} title={row.label}>
                  {row.label}
                  {isOp && resourceId && (
                    <small className="so-pu-gantt-resource">{resourceId}</small>
                  )}
                </div>
                <div
                  className={`so-pu-gantt-track ${showCapacity ? 'is-stacked' : ''}`}
                  style={{ width: model.chartWidth, height }}
                >
                  {showCapacity && capCells && (
                    <CapacityCells
                      resourceId={resourceId}
                      columns={model.columns}
                      cells={capCells}
                      cellW={model.cellW}
                    />
                  )}
                  {!showCapacity &&
                    model.columns.map((col) => (
                      <div
                        key={col.date}
                        className="so-pu-gantt-grid-cell"
                        style={{ width: model.cellW }}
                      />
                    ))}
                  <div
                    className={`so-pu-gantt-bar ${isOp ? 'is-op' : 'is-pu'}`}
                    style={{ left: bar.left, width: bar.width }}
                    title={`${row.label} · ${fmtTime(row.startTs)} → ${fmtTime(row.endTs)}`}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
