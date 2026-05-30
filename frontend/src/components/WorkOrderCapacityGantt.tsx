import { useMemo } from 'react';
import type {
  WorkOrderCapacityBucket,
  WorkOrderCapacityGantt as WorkOrderCapacityGanttModel,
  WorkOrderCapacityOperation,
} from '../types/api';
import {
  UTILIZATION_BAND_ORDER,
  utilizationBand,
  utilizationBandLabel,
} from '../utils/capacityUtilization';
import './WorkOrderCapacityGantt.css';

const LABEL_W = 148;
const CELL_W = 76;
const ROW_H = 36;
const HEADER_H = 31;

interface WorkOrderCapacityGanttProps {
  data: WorkOrderCapacityGanttModel | null;
}

interface ResourceRow {
  resourceId: string;
  operations: WorkOrderCapacityOperation[];
  cellsByDate: Map<string, WorkOrderCapacityBucket>;
}

function fmtTime(ts: string): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function buildModel(data: WorkOrderCapacityGanttModel): {
  rows: ResourceRow[];
  columns: { date: string; label: string }[];
  startMs: number;
  endMs: number;
} {
  const opsByResource = new Map<string, WorkOrderCapacityOperation[]>();
  for (const op of data.operations) {
    const list = opsByResource.get(op.resourceId) ?? [];
    list.push(op);
    opsByResource.set(op.resourceId, list);
  }

  const cellsByResource = new Map<string, Map<string, WorkOrderCapacityBucket>>();
  for (const bucket of data.resourceBuckets) {
    const map = cellsByResource.get(bucket.resourceId) ?? new Map<string, WorkOrderCapacityBucket>();
    map.set(bucket.date, bucket);
    cellsByResource.set(bucket.resourceId, map);
  }

  const allDates = new Set<string>();
  for (const b of data.resourceBuckets) {
    allDates.add(b.date);
  }
  const planStart = new Date(data.plannedStartTs);
  const planEnd = new Date(data.plannedEndTs);
  for (
    let d = new Date(planStart.getFullYear(), planStart.getMonth(), planStart.getDate());
    d <= planEnd;
    d.setDate(d.getDate() + 1)
  ) {
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    allDates.add(iso);
  }

  const dates = [...allDates].sort();
  const columns = dates.map((date) => {
    const d = new Date(date + 'T00:00:00');
    return { date, label: `${d.getMonth() + 1}/${d.getDate()}` };
  });

  const rows: ResourceRow[] = [];
  const seenResources = new Set<string>();
  for (const op of data.operations) {
    if (seenResources.has(op.resourceId)) continue;
    seenResources.add(op.resourceId);
    rows.push({
      resourceId: op.resourceId,
      operations: opsByResource.get(op.resourceId) ?? [],
      cellsByDate: cellsByResource.get(op.resourceId) ?? new Map(),
    });
  }

  const firstDate = dates.length > 0 ? new Date(dates[0] + 'T00:00:00') : planStart;
  const lastDate = dates.length > 0 ? new Date(dates[dates.length - 1] + 'T00:00:00') : planEnd;
  lastDate.setDate(lastDate.getDate() + 1);

  return {
    rows,
    columns,
    startMs: firstDate.getTime(),
    endMs: lastDate.getTime(),
  };
}

export function WorkOrderCapacityGantt({ data }: WorkOrderCapacityGanttProps) {
  const model = useMemo(() => (data ? buildModel(data) : null), [data]);

  if (!data || !model) {
    return <p className="wo-cap-empty">请先选择一个工单</p>;
  }

  if (model.rows.length === 0) {
    return <p className="wo-cap-empty">该工单暂无工序数据</p>;
  }

  const totalSpan = Math.max(1, model.endMs - model.startMs);
  const totalWidth = model.columns.length * CELL_W;

  const positionFor = (tsIso: string): number => {
    const ts = new Date(tsIso).getTime();
    return ((ts - model.startMs) / totalSpan) * totalWidth;
  };

  const widthFor = (startIso: string, endIso: string): number => {
    const s = new Date(startIso).getTime();
    const e = new Date(endIso).getTime();
    return Math.max(6, ((e - s) / totalSpan) * totalWidth);
  };

  const planBarLeft = positionFor(data.plannedStartTs);
  const planBarWidth = widthFor(data.plannedStartTs, data.plannedEndTs);

  return (
    <div className="wo-cap-gantt">
      <div className="wo-cap-legend">
        <span className="wo-cap-legend-info">
          计划窗口 {fmtTime(data.plannedStartTs)} → {fmtTime(data.plannedEndTs)} · 共 {data.totalDurationMinutes} 分钟
        </span>
        {UTILIZATION_BAND_ORDER.map((band) => (
          <span key={band} className={`cap-leg band-${band}`}>
            {utilizationBandLabel(band)}
          </span>
        ))}
        <span className="wo-cap-legend-plan">
          <i className="wo-cap-plan-dot" /> 工单计划时间
        </span>
      </div>
      <div className="wo-cap-scroll">
        <div
          className="wo-cap-grid"
          style={{ minWidth: LABEL_W + totalWidth }}
        >
          <div
            className="wo-cap-header-row"
            style={{ gridTemplateColumns: `${LABEL_W}px repeat(${model.columns.length}, ${CELL_W}px)`, height: HEADER_H }}
          >
            <div className="wo-cap-corner">工序 · 机台</div>
            {model.columns.map((col) => (
              <div key={col.date} className="wo-cap-col-head" title={col.date}>
                <span>{col.label}</span>
              </div>
            ))}
          </div>
          {model.rows.map((row) => (
            <div
              key={row.resourceId}
              className="wo-cap-body-row"
              style={{
                gridTemplateColumns: `${LABEL_W}px repeat(${model.columns.length}, ${CELL_W}px)`,
                height: ROW_H,
              }}
            >
              <div className="wo-cap-row-label" title={row.resourceId}>
                <div className="wo-cap-row-resource">{row.resourceId}</div>
                <div className="wo-cap-row-ops">
                  {row.operations.map((op) => (
                    <span key={op.operationId} className="wo-cap-row-op-name">
                      {op.sequenceNo} · {op.operationName}
                    </span>
                  ))}
                </div>
              </div>
              <div className="wo-cap-row-track" style={{ width: totalWidth }}>
                {model.columns.map((col) => {
                  const bucket = row.cellsByDate.get(col.date);
                  if (!bucket) {
                    return <div key={col.date} className="wo-cap-cell wo-cap-cell-empty" style={{ width: CELL_W }} />;
                  }
                  const band = utilizationBand(bucket.utilizationPct);
                  return (
                    <div
                      key={col.date}
                      className={`wo-cap-cell band-${band}`}
                      style={{ width: CELL_W }}
                      title={`${row.resourceId} · ${col.date}：利用率 ${bucket.utilizationPct}% (${bucket.demandMinutes}/${bucket.availableMinutes}分)`}
                    >
                      <span className="wo-cap-cell-pct">{bucket.utilizationPct}%</span>
                      <span className="wo-cap-cell-sub">
                        {bucket.demandMinutes}/{bucket.availableMinutes}
                      </span>
                    </div>
                  );
                })}
                <div
                  className="wo-cap-plan-bar"
                  style={{ left: planBarLeft, width: planBarWidth }}
                  title={`工单计划时间 ${fmtTime(data.plannedStartTs)} → ${fmtTime(data.plannedEndTs)}`}
                />
                {row.operations.map((op) => {
                  const left = positionFor(op.plannedStartTs);
                  const width = widthFor(op.plannedStartTs, op.plannedEndTs);
                  return (
                    <div
                      key={op.operationId}
                      className="wo-cap-op-bar"
                      style={{ left, width }}
                      title={`${op.operationName} · ${op.resourceId} ${fmtTime(op.plannedStartTs)} → ${fmtTime(op.plannedEndTs)}（${op.durationMinutes} 分钟）`}
                    >
                      <span className="wo-cap-op-bar-label">{op.operationName}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
