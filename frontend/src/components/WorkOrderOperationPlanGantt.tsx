import { useMemo } from 'react';
import type { WorkOrderCapacityBucket, WorkOrderCapacityGantt, WorkOrderCapacityOperation } from '../types/api';
import {
  UTILIZATION_BAND_ORDER,
  utilizationBand,
  utilizationBandLabel,
} from '../utils/capacityUtilization';
import { fmtShortTs } from '../utils/formatTiming';
import './WorkOrderOperationPlanGantt.css';

const LABEL_W = 200;
const OP_ROW_H = 34;
const RESOURCE_ROW_H = 28;
const HEADER_H = 31;
const CELL_W = 76;
const DEFAULT_SHIFT_MINUTES = 480;

function toDateKey(ts: string): string {
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function fmtTime(ts: string): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export interface GanttTimingMarkerOptions {
  showLatestConstraints: boolean;
  showEarliestFeasible: boolean;
  showEarliestOwn: boolean;
}

interface WorkOrderOperationPlanGanttProps {
  data: WorkOrderCapacityGantt | null;
  loading?: boolean;
  markerOptions?: GanttTimingMarkerOptions;
}

interface OpBlock {
  op: WorkOrderCapacityOperation;
  resources: string[];
}

function resourcesForOp(op: WorkOrderCapacityOperation): string[] {
  const ids = op.allowedResourceIds?.filter(Boolean) ?? [];
  if (ids.length > 0) {
    return ids;
  }
  return op.resourceId ? [op.resourceId] : [];
}

function bucketForDate(
  cells: Map<string, WorkOrderCapacityBucket>,
  date: string,
): WorkOrderCapacityBucket | undefined {
  return (
    cells.get(`${date}|DAY`) ??
    cells.get(`${date}|WEEK`) ??
    [...cells.values()].find((c) => c.date === date)
  );
}

function shiftStartHour(shiftId: string | undefined): number {
  if (shiftId === 'S2' || shiftId === 'NIGHT') return 16;
  return 8;
}

function availableForDate(
  cells: Map<string, WorkOrderCapacityBucket>,
  date: string,
): { availableMinutes: number; shiftId: string } {
  const bucket = bucketForDate(cells, date);
  return {
    availableMinutes: bucket?.availableMinutes ?? DEFAULT_SHIFT_MINUTES,
    shiftId: bucket?.shiftId ?? 'DAY',
  };
}

/** 按「加工分钟 / 当日可用产能」比例绘制工序条，支持跨日拆段。 */
function barGeometryProportional(
  op: WorkOrderCapacityOperation,
  columns: { date: string; label: string }[],
  bucketsByResource: Map<string, Map<string, WorkOrderCapacityBucket>>,
  axisStartMs: number,
  spanMs: number,
  totalWidth: number,
): { left: number; width: number } {
  const startKey = toDateKey(op.plannedStartTs);
  const startIdx = columns.findIndex((c) => c.date === startKey);
  if (startIdx < 0) {
    const left = ((new Date(op.plannedStartTs).getTime() - axisStartMs) / spanMs) * totalWidth;
    const width = Math.max(
      4,
      ((new Date(op.plannedEndTs).getTime() - new Date(op.plannedStartTs).getTime()) / spanMs) * totalWidth,
    );
    return { left, width };
  }

  const cells = bucketsByResource.get(op.resourceId) ?? new Map();
  const { availableMinutes: startAvail, shiftId } = availableForDate(cells, startKey);
  const shiftHour = shiftStartHour(shiftId);
  const shiftStartMs = new Date(
    `${startKey}T${String(shiftHour).padStart(2, '0')}:00:00`,
  ).getTime();
  const startMs = new Date(op.plannedStartTs).getTime();
  let offsetInDay = Math.max(0, (startMs - shiftStartMs) / 60_000);
  if (offsetInDay >= startAvail) {
    offsetInDay = 0;
  }

  let remaining = Math.max(1, op.durationMinutes);
  let left = 0;
  let width = 0;
  let idx = startIdx;

  while (remaining > 0 && idx < columns.length) {
    const { availableMinutes } = availableForDate(cells, columns[idx].date);
    const avail = Math.max(1, availableMinutes);
    const spaceInDay = Math.max(0, avail - offsetInDay);
    if (spaceInDay <= 0) {
      idx += 1;
      offsetInDay = 0;
      continue;
    }
    const take = Math.min(remaining, spaceInDay);
    const segWidth = (take / avail) * CELL_W;
    if (width === 0) {
      left = idx * CELL_W + (offsetInDay / avail) * CELL_W;
    }
    width += segWidth;
    remaining -= take;
    idx += 1;
    offsetInDay = 0;
  }

  return { left, width: Math.max(4, width) };
}

function TimingVLine({
  left,
  variant,
  title,
}: {
  left: number;
  variant: 'red' | 'yellow' | 'gray';
  title: string;
}) {
  return (
    <div
      className={`wo-op-plan-vline wo-op-plan-vline--${variant}`}
      style={{ left }}
      title={title}
      aria-label={title}
    />
  );
}

function CapacityCells({
  resourceId,
  columns,
  cells,
  totalWidth,
}: {
  resourceId: string;
  columns: { date: string; label: string }[];
  cells: Map<string, WorkOrderCapacityBucket>;
  totalWidth: number;
}) {
  return (
    <div className="wo-op-plan-track wo-op-plan-track--resource" style={{ width: totalWidth, height: RESOURCE_ROW_H }}>
      {columns.map((col) => {
        const bucket = bucketForDate(cells, col.date);
        if (!bucket) {
          return (
            <div
              key={col.date}
              className="wo-op-plan-cell wo-op-plan-cell-empty"
              style={{ width: CELL_W }}
            />
          );
        }
        const band = utilizationBand(bucket.utilizationPct);
        return (
          <div
            key={col.date}
            className={`wo-op-plan-cell band-${band}`}
            style={{ width: CELL_W }}
            title={`${resourceId} · ${col.date}：${bucket.utilizationPct}% (${bucket.demandMinutes}/${bucket.availableMinutes}分)`}
          >
            <span className="wo-op-plan-cell-pct">{bucket.utilizationPct}%</span>
          </div>
        );
      })}
    </div>
  );
}

export function WorkOrderOperationPlanGantt({
  data,
  loading,
  markerOptions = {
    showLatestConstraints: true,
    showEarliestFeasible: true,
    showEarliestOwn: true,
  },
}: WorkOrderOperationPlanGanttProps) {
  const model = useMemo(() => {
    if (!data || data.operations.length === 0) return null;
    const ops = [...data.operations].sort((a, b) => a.sequenceNo - b.sequenceNo);
    const blocks: OpBlock[] = ops.map((op) => ({
      op,
      resources: resourcesForOp(op),
    }));

    let axisStartMs = new Date(data.horizonStartTs ?? data.plannedStartTs).getTime();
    let axisEndMs = new Date(data.horizonEndTs ?? data.plannedEndTs).getTime();
    const tw = data.timingWindow;
    if (tw) {
      const startMarks = [
        tw.earliestPossibleStartOwn,
        tw.earliestPossibleStart,
        tw.latestDesiredStart,
        tw.latestDesiredEnd,
      ];
      for (const ts of startMarks) {
        if (ts) {
          const m = new Date(ts).getTime();
          if (m < axisStartMs) axisStartMs = m;
        }
      }
      const endMarks = [
        tw.latestDesiredDelivery,
        tw.earliestPossibleDelivery,
        tw.earliestPossibleDeliveryOwn,
      ];
      for (const ts of endMarks) {
        if (ts) {
          const m = new Date(ts).getTime();
          if (m > axisEndMs) axisEndMs = m;
        }
      }
    }
    const startDay = new Date(axisStartMs);
    startDay.setHours(0, 0, 0, 0);
    axisStartMs = startDay.getTime();

    const bucketsByResource = new Map<string, Map<string, WorkOrderCapacityBucket>>();
    for (const b of data.resourceBuckets) {
      const map = bucketsByResource.get(b.resourceId) ?? new Map<string, WorkOrderCapacityBucket>();
      map.set(`${b.date}|${b.shiftId}`, b);
      bucketsByResource.set(b.resourceId, map);
    }

    const dates = new Set<string>();
    for (const b of data.resourceBuckets) {
      dates.add(b.date);
    }
    const startDate = new Date(axisStartMs);
    const endDate = new Date(axisEndMs);
    for (
      let d = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate());
      d <= endDate;
      d.setDate(d.getDate() + 1)
    ) {
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      dates.add(iso);
    }
    const columns = [...dates].sort().map((date) => {
      const d = new Date(date + 'T00:00:00');
      return { date, label: `${d.getMonth() + 1}/${d.getDate()}` };
    });

    const MS_PER_DAY = 86_400_000;
    const dayCount = Math.max(3, columns.length);
    const totalWidth = dayCount * CELL_W;
    const spanMs = dayCount * MS_PER_DAY;

    const chartHeight = blocks.reduce(
      (sum, b) => sum + OP_ROW_H + b.resources.length * RESOURCE_ROW_H,
      0,
    );

    return { blocks, axisStartMs, spanMs, totalWidth, columns, bucketsByResource, chartHeight };
  }, [data]);

  if (loading) {
    return <p className="wo-op-plan-empty">加载工序计划…</p>;
  }

  if (!data || !model) {
    return (
      <p className="wo-op-plan-empty">
        {data ? '该工单暂无工序路由数据' : '请先运行主计划并选择工单'}
      </p>
    );
  }

  const tw = data.timingWindow;
  const leftFor = (ts: string | null | undefined) => {
    if (!ts) return null;
    return ((new Date(ts).getTime() - model.axisStartMs) / model.spanMs) * model.totalWidth;
  };

  const totalWidth = LABEL_W + model.totalWidth;

  const markerLayer =
    tw &&
    (markerOptions.showLatestConstraints ||
      markerOptions.showEarliestFeasible ||
      markerOptions.showEarliestOwn) ? (
      <div
        className="wo-op-plan-marker-layer"
        style={{ left: LABEL_W, top: HEADER_H, width: model.totalWidth, height: model.chartHeight }}
      >
        {markerOptions.showEarliestOwn && (
          <>
            {leftFor(tw.earliestPossibleStartOwn) != null && (
              <TimingVLine
                left={leftFor(tw.earliestPossibleStartOwn)!}
                variant="gray"
                title={`自身最早可开始 ${fmtShortTs(tw.earliestPossibleStartOwn)}`}
              />
            )}
            {leftFor(tw.earliestPossibleDeliveryOwn) != null && (
              <TimingVLine
                left={leftFor(tw.earliestPossibleDeliveryOwn)!}
                variant="gray"
                title={`自身最早可交付 ${fmtShortTs(tw.earliestPossibleDeliveryOwn)}`}
              />
            )}
          </>
        )}
        {markerOptions.showEarliestFeasible && (
          <>
            {leftFor(tw.earliestPossibleStart) != null && (
              <TimingVLine
                left={leftFor(tw.earliestPossibleStart)!}
                variant="yellow"
                title={`最早可行开始 ${fmtShortTs(tw.earliestPossibleStart)}`}
              />
            )}
            {leftFor(tw.earliestPossibleDelivery) != null && (
              <TimingVLine
                left={leftFor(tw.earliestPossibleDelivery)!}
                variant="yellow"
                title={`最早可行交付 ${fmtShortTs(tw.earliestPossibleDelivery)}`}
              />
            )}
          </>
        )}
        {markerOptions.showLatestConstraints && (
          <>
            {leftFor(tw.latestDesiredEnd) != null && (
              <TimingVLine
                left={leftFor(tw.latestDesiredEnd)!}
                variant="red"
                title={`最晚要求完成 ${fmtShortTs(tw.latestDesiredEnd)}`}
              />
            )}
            {leftFor(tw.earliestPossibleStart) != null && (
              <TimingVLine
                left={leftFor(tw.earliestPossibleStart)!}
                variant="red"
                title={`最早可行开始 ${fmtShortTs(tw.earliestPossibleStart)}`}
              />
            )}
          </>
        )}
      </div>
    ) : null;

  return (
    <section className="wo-op-plan-gantt">
      <div className="wo-op-plan-meta">
        <span>
          计划 {fmtTime(data.plannedStartTs)} → {fmtTime(data.plannedEndTs)} · {data.totalDurationMinutes} 分钟
        </span>
        <span>
          时间轴 {fmtTime(data.horizonStartTs)} → {fmtTime(data.horizonEndTs)}
        </span>
      </div>

      <div className="wo-op-plan-legend">
        {UTILIZATION_BAND_ORDER.map((band) => (
          <span key={band} className={`wo-op-plan-leg band-${band}`}>
            {utilizationBandLabel(band)}
          </span>
        ))}
        <span className="wo-op-plan-leg wo-op-plan-leg-bar">工序计划条</span>
        {markerOptions.showLatestConstraints && (
          <span className="wo-op-plan-leg wo-op-plan-leg-line wo-op-plan-leg-line--red">最晚完成 / 可行开始</span>
        )}
        {markerOptions.showEarliestFeasible && (
          <span className="wo-op-plan-leg wo-op-plan-leg-line wo-op-plan-leg-line--yellow">可行开始 / 交付</span>
        )}
        {markerOptions.showEarliestOwn && (
          <span className="wo-op-plan-leg wo-op-plan-leg-line wo-op-plan-leg-line--gray">自身开始 / 交付</span>
        )}
      </div>

      <div className="wo-op-plan-scroll">
        <div className="wo-op-plan-grid" style={{ minWidth: totalWidth, position: 'relative' }}>
          {markerLayer}
          <div
            className="wo-op-plan-header"
            style={{ gridTemplateColumns: `${LABEL_W}px ${model.totalWidth}px`, height: HEADER_H }}
          >
            <div className="wo-op-plan-corner">工序（按顺序）</div>
            <div className="wo-op-plan-axis" style={{ width: model.totalWidth }}>
              {model.columns.map((col) => (
                <div
                  key={col.date}
                  className="wo-op-plan-day"
                  style={{ width: CELL_W }}
                  title={col.date}
                >
                  {col.label}
                </div>
              ))}
            </div>
          </div>

          {model.blocks.map(({ op, resources }) => {
            const bar = barGeometryProportional(
              op,
              model.columns,
              model.bucketsByResource,
              model.axisStartMs,
              model.spanMs,
              model.totalWidth,
            );
            return (
              <div key={op.operationId} className="wo-op-plan-op-block">
                <div
                  className="wo-op-plan-row wo-op-plan-row--operation"
                  style={{
                    gridTemplateColumns: `${LABEL_W}px ${model.totalWidth}px`,
                    minHeight: OP_ROW_H,
                  }}
                >
                  <div className="wo-op-plan-label wo-op-plan-label--operation" title={op.operationName}>
                    <span className="wo-op-plan-op-title">
                      {op.sequenceNo} - {op.operationName}
                    </span>
                  </div>
                  <div className="wo-op-plan-track wo-op-plan-track--operation" style={{ width: model.totalWidth, height: OP_ROW_H }}>
                    {model.columns.map((col) => (
                      <div
                        key={col.date}
                        className="wo-op-plan-grid-cell"
                        style={{ width: CELL_W }}
                      />
                    ))}
                    <div
                      className="wo-op-plan-bar"
                      style={{ left: bar.left, width: bar.width }}
                      title={`${op.operationName} · ${fmtTime(op.plannedStartTs)} → ${fmtTime(op.plannedEndTs)} · ${op.durationMinutes} 分钟 · ${op.resourceId}`}
                    >
                      <span className="wo-op-plan-bar-label">{op.durationMinutes}′</span>
                    </div>
                  </div>
                </div>

                {resources.map((resourceId) => {
                  const cells = model.bucketsByResource.get(resourceId) ?? new Map();
                  const assigned = resourceId === op.resourceId;
                  return (
                    <div
                      key={`${op.operationId}-${resourceId}`}
                      className="wo-op-plan-row wo-op-plan-row--resource"
                      style={{
                        gridTemplateColumns: `${LABEL_W}px ${model.totalWidth}px`,
                        minHeight: RESOURCE_ROW_H,
                      }}
                    >
                      <div className="wo-op-plan-label wo-op-plan-label--resource" title={resourceId}>
                        <span className="wo-op-plan-resource-prefix">└</span>
                        <span className={`wo-op-plan-resource${assigned ? ' is-assigned' : ''}`}>
                          {resourceId}
                          {assigned ? ' ★' : ''}
                        </span>
                      </div>
                      <CapacityCells
                        resourceId={resourceId}
                        columns={model.columns}
                        cells={cells}
                        totalWidth={model.totalWidth}
                      />
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
