import { useMemo } from 'react';
import type { WorkOrderCapacityBucket, WorkOrderCapacityGantt } from '../types/api';
import {
  UTILIZATION_BAND_ORDER,
  utilizationBand,
  utilizationBandLabel,
} from '../utils/capacityUtilization';
import { fmtShortTs } from '../utils/formatTiming';
import './WorkOrderOperationPlanGantt.css';

const LABEL_W = 200;
const ROW_H = 34;
const HEADER_H = 31;
const CELL_W = 76;

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
    // 轴起点对齐到当天 0 点，使位于起点附近的时间线不会被裁到可视区外。
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

    // 工序条与日列必须共用同一宽度：每列固定 CELL_W，时间轴按整天线性映射。
    const MS_PER_DAY = 86_400_000;
    const dayCount = Math.max(3, columns.length);
    const totalWidth = dayCount * CELL_W;
    const spanMs = dayCount * MS_PER_DAY;

    return { ops, axisStartMs, spanMs, totalWidth, columns, bucketsByResource };
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
  const widthFor = (start: string, end: string) =>
    Math.max(8, ((new Date(end).getTime() - new Date(start).getTime()) / model.spanMs) * model.totalWidth);

  /** 主计划 DAY 槽位甘特：工序条按「日列」对齐，宽度 = 占用的日历天数 × CELL_W */
  const barGeometry = (startTs: string, endTs: string) => {
    const startKey = toDateKey(startTs);
    const endKey = toDateKey(endTs);
    let startIdx = model.columns.findIndex((c) => c.date === startKey);
    let endIdx = model.columns.findIndex((c) => c.date === endKey);
    if (startIdx >= 0 && endIdx >= 0) {
      if (endIdx < startIdx) endIdx = startIdx;
      return {
        left: startIdx * CELL_W,
        width: Math.max(CELL_W, (endIdx - startIdx + 1) * CELL_W),
      };
    }
    const left = leftFor(startTs) ?? 0;
    return { left, width: widthFor(startTs, endTs) };
  };

  const totalWidth = LABEL_W + model.totalWidth;
  const chartHeight = model.ops.length * ROW_H;

  const markerLayer =
    tw &&
    (markerOptions.showLatestConstraints ||
      markerOptions.showEarliestFeasible ||
      markerOptions.showEarliestOwn) ? (
      <div
        className="wo-op-plan-marker-layer"
        style={{ left: LABEL_W, top: HEADER_H, width: model.totalWidth, height: chartHeight }}
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
          {model.ops.map((op) => {
            const cells = model.bucketsByResource.get(op.resourceId) ?? new Map();
            const bar = barGeometry(op.plannedStartTs, op.plannedEndTs);
            return (
              <div
                key={op.operationId}
                className="wo-op-plan-row"
                style={{
                  gridTemplateColumns: `${LABEL_W}px ${model.totalWidth}px`,
                  minHeight: ROW_H,
                }}
              >
                <div className="wo-op-plan-label" title={op.resourceId}>
                  <span className="wo-op-plan-seq">{op.sequenceNo}</span>
                  <span className="wo-op-plan-name">{op.operationName}</span>
                  <span className="wo-op-plan-resource">{op.resourceId}</span>
                </div>
                <div className="wo-op-plan-track" style={{ width: model.totalWidth, height: ROW_H }}>
                  {model.columns.map((col) => {
                    const bucket =
                      cells.get(`${col.date}|DAY`) ??
                      cells.get(`${col.date}|WEEK`) ??
                      [...cells.values()].find((c) => c.date === col.date);
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
                        title={`${op.resourceId} · ${col.date}：${bucket.utilizationPct}%`}
                      >
                        <span className="wo-op-plan-cell-pct">{bucket.utilizationPct}%</span>
                      </div>
                    );
                  })}
                  <div
                    className="wo-op-plan-bar"
                    style={{
                      left: bar.left,
                      width: bar.width,
                    }}
                    title={`${op.operationName} · ${fmtTime(op.plannedStartTs)} → ${fmtTime(op.plannedEndTs)} · ${op.durationMinutes} 分钟`}
                  >
                    <span className="wo-op-plan-bar-label">{op.durationMinutes}′</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
