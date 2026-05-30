import { useMemo } from 'react';
import type { WorkOrderScheduleOperation } from '../types/api';
import {
  barColorForScope,
  buildWorkOrderScheduleModel,
  fmtScheduleTs,
} from '../utils/workOrderScheduleModel';
import './WorkOrderScheduleGantt.css';

const LABEL_W = 120;
const ROW_H = 31;
const HEADER_H = 28;

interface WorkOrderScheduleGanttProps {
  operations: WorkOrderScheduleOperation[];
  className?: string;
}

export function WorkOrderScheduleGantt({ operations, className = '' }: WorkOrderScheduleGanttProps) {
  const model = useMemo(() => buildWorkOrderScheduleModel(operations), [operations]);

  if (!model || model.rows.length === 0) {
    return <p className="wo-sched-gantt-empty">暂无工序排程反馈，请先完成排程或写入反馈</p>;
  }

  const spanMs = Math.max(1, model.maxMs - model.minMs);
  const dayMs = 86_400_000;
  const days = Math.max(1, Math.ceil(spanMs / dayMs) + 1);
  const trackWidth = days * 96;
  const totalWidth = LABEL_W + trackWidth;

  const leftFor = (ms: number) => ((ms - model.minMs) / spanMs) * trackWidth;
  const widthFor = (startMs: number, endMs: number) =>
    Math.max(6, ((endMs - startMs) / spanMs) * trackWidth);

  const dayColumns = Array.from({ length: days }, (_, i) => {
    const d = new Date(model.horizonStart);
    d.setDate(d.getDate() + i);
    return d;
  });

  return (
    <section className={`wo-sched-gantt ${className}`.trim()}>
      <div className="wo-sched-gantt-legend">
        <span className="wo-sched-leg frozen">冻结（FROZEN）</span>
        <span className="wo-sched-leg suggestion">建议（SUGGESTION）</span>
      </div>
      <div className="wo-sched-gantt-scroll">
        <div className="wo-sched-gantt-grid" style={{ minWidth: totalWidth }}>
          <div
            className="wo-sched-gantt-header"
            style={{ gridTemplateColumns: `${LABEL_W}px ${trackWidth}px`, height: HEADER_H }}
          >
            <div className="wo-sched-corner">工序</div>
            <div className="wo-sched-time-axis" style={{ width: trackWidth }}>
              {dayColumns.map((d) => (
                <div
                  key={d.toISOString()}
                  className="wo-sched-day-tick"
                  style={{ width: 96 }}
                >
                  {d.getMonth() + 1}/{d.getDate()}
                </div>
              ))}
            </div>
          </div>
          {model.rows.map((row) => (
            <div
              key={row.resourceId}
              className="wo-sched-gantt-row"
              style={{ gridTemplateColumns: `${LABEL_W}px ${trackWidth}px`, minHeight: ROW_H }}
            >
              <div className="wo-sched-row-label" title={row.resourceId}>
                {row.resourceId}
              </div>
              <div className="wo-sched-track" style={{ width: trackWidth, height: ROW_H }}>
                {row.operations.map((op) => {
                  const startMs = new Date(op.plannedStart).getTime();
                  const endMs = new Date(op.plannedEnd).getTime();
                  return (
                    <div
                      key={op.operationId}
                      className="wo-sched-bar"
                      style={{
                        left: leftFor(startMs),
                        width: widthFor(startMs, endMs),
                        background: barColorForScope(op.scope),
                      }}
                      title={`${op.operationName} · ${fmtScheduleTs(op.plannedStart)} → ${fmtScheduleTs(op.plannedEnd)} · ${op.scope}`}
                    >
                      <span className="wo-sched-bar-label">{op.operationName}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
