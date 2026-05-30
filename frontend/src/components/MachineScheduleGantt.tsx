import { useCallback, useMemo, useRef, useState } from 'react';
import type { DetailScheduleOperation } from '../types/api';
import {
  buildMachineScheduleModel,
  buildTicksForRange,
  clampZoom,
  MACHINE_SCHEDULE_HEADER_H,
  MACHINE_SCHEDULE_LABEL_W,
  MACHINE_SCHEDULE_MINUTE_W,
  MACHINE_SCHEDULE_ROW_H,
  MACHINE_SCHEDULE_ZOOM_DEFAULT,
  MACHINE_SCHEDULE_ZOOM_STEP,
  taskBarColor,
  tickStepForZoom,
} from '../utils/machineScheduleModel';
import './MachineScheduleGantt.css';

interface MachineScheduleGanttProps {
  operations: DetailScheduleOperation[];
  className?: string;
}

export function MachineScheduleGantt({ operations, className = '' }: MachineScheduleGanttProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(MACHINE_SCHEDULE_ZOOM_DEFAULT);

  const model = useMemo(() => buildMachineScheduleModel(operations), [operations]);

  const minuteWidth = MACHINE_SCHEDULE_MINUTE_W * zoom;

  const ticks = useMemo(() => {
    if (!model) return [];
    const span = model.maxMinute - model.minMinute;
    const step = tickStepForZoom(span, zoom);
    return buildTicksForRange(model.horizonStart, model.minMinute, model.maxMinute, step);
  }, [model, zoom]);

  const zoomIn = useCallback(() => {
    setZoom((z) => clampZoom(Math.round((z + MACHINE_SCHEDULE_ZOOM_STEP) * 100) / 100));
  }, []);

  const zoomOut = useCallback(() => {
    setZoom((z) => clampZoom(Math.round((z - MACHINE_SCHEDULE_ZOOM_STEP) * 100) / 100));
  }, []);

  const zoomReset = useCallback(() => {
    setZoom(MACHINE_SCHEDULE_ZOOM_DEFAULT);
  }, []);

  const onWheel = useCallback((e: React.WheelEvent<HTMLDivElement>) => {
    if (!e.ctrlKey) return;
    e.preventDefault();
    const delta = e.deltaY > 0 ? -MACHINE_SCHEDULE_ZOOM_STEP : MACHINE_SCHEDULE_ZOOM_STEP;
    setZoom((z) => clampZoom(Math.round((z + delta) * 100) / 100));
  }, []);

  if (!model || model.rows.length === 0) {
    return <p className="ms-gantt-empty">暂无排程数据，请先求解排程</p>;
  }

  const span = Math.max(1, model.maxMinute - model.minMinute);
  const trackWidth = span * minuteWidth;
  const totalWidth = MACHINE_SCHEDULE_LABEL_W + trackWidth;

  const leftFor = (minute: number) => ((minute - model.minMinute) / span) * trackWidth;
  const widthFor = (start: number, end: number) =>
    Math.max(8, ((end - start) / span) * trackWidth);

  const zoomPct = Math.round(zoom * 100);

  return (
    <section className={`card ms-gantt ${className}`.trim()}>
      <div className="ms-gantt-toolbar">
        <h3>机台排程甘特图</h3>
        <span className="ms-gantt-hint">每台机台一行 · Ctrl+滚轮 缩放</span>
        <div className="ms-gantt-zoom">
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomOut} title="缩小">
            −
          </button>
          <span className="ms-gantt-zoom-label">{zoomPct}%</span>
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomIn} title="放大">
            +
          </button>
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomReset} title="重置缩放">
            重置
          </button>
        </div>
      </div>
      <div className="ms-gantt-scroll" ref={scrollRef} onWheel={onWheel}>
        <div className="ms-gantt-grid" style={{ minWidth: totalWidth }}>
          <div
            className="ms-gantt-header"
            style={{
              gridTemplateColumns: `${MACHINE_SCHEDULE_LABEL_W}px ${trackWidth}px`,
              height: MACHINE_SCHEDULE_HEADER_H,
            }}
          >
            <div className="ms-gantt-corner">机台</div>
            <div className="ms-gantt-time-axis" style={{ width: trackWidth }}>
              {ticks.map((tick) => (
                <span
                  key={tick.minute}
                  className="ms-gantt-tick"
                  style={{ left: leftFor(tick.minute) }}
                >
                  {tick.label}
                </span>
              ))}
            </div>
          </div>

          {model.rows.map((row) => (
            <div
              key={row.machineId}
              className="ms-gantt-row"
              style={{
                gridTemplateColumns: `${MACHINE_SCHEDULE_LABEL_W}px ${trackWidth}px`,
                height: MACHINE_SCHEDULE_ROW_H,
              }}
            >
              <div className="ms-gantt-machine-label" title={row.machineId}>
                {row.machineId}
                <span className="ms-gantt-task-count">{row.tasks.length} 项</span>
              </div>
              <div className="ms-gantt-track" style={{ width: trackWidth }}>
                {ticks.map((tick) => (
                  <div
                    key={tick.minute}
                    className="ms-gantt-grid-line"
                    style={{ left: leftFor(tick.minute) }}
                  />
                ))}
                {row.tasks.map((task) => {
                  const left = leftFor(task.startMinute);
                  const width = widthFor(task.startMinute, task.endMinute);
                  const bg = taskBarColor(task);
                  const startLabel = fmtMinute(model.horizonStart, task.startMinute);
                  const endLabel = fmtMinute(model.horizonStart, task.endMinute);
                  return (
                    <div
                      key={task.operationId}
                      className={`ms-gantt-bar ${task.pinned ? 'pinned' : ''}`}
                      style={{ left, width, backgroundColor: bg, borderColor: bg }}
                      title={`#${task.sequenceIndex} ${task.workOrderNo} · ${task.productCode}\n${startLabel} → ${endLabel}`}
                    >
                      <span className="ms-gantt-bar-text">
                        #{task.sequenceIndex} {task.workOrderNo}
                      </span>
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

function fmtMinute(horizonStart: Date, minute: number): string {
  const d = new Date(horizonStart.getTime() + minute * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
