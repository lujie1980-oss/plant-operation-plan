import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { DetailScheduleOperation } from '../types/api';
import {
  buildMachineScheduleModel,
  buildTicksForTimeScale,
  clampZoom,
  MACHINE_SCHEDULE_HEADER_H,
  MACHINE_SCHEDULE_LABEL_W,
  MACHINE_SCHEDULE_ROW_H,
  MACHINE_SCHEDULE_ZOOM_DEFAULT,
  MACHINE_SCHEDULE_ZOOM_STEP,
  minuteWidthForTimeScale,
  TIME_SCALE_LABELS,
  type MachineScheduleTask,
  type TimeScalePreset,
  changeoverBarStyle,
  taskBarStyle,
} from '../utils/machineScheduleModel';
import { GANTT_PHASE_LABEL, type GanttTaskDisplayPhase } from '../utils/ganttTaskDisplay';
import {
  buildRoutingProcessChain,
  findRoutingTimeViolations,
} from '../utils/operationRoutingSeq';
import {
  minuteFromTrackX,
  sequenceOnLineFromDropMinute,
  type GanttDragCommit,
} from '../utils/ganttDragDrop';
import { BATCH_DRAG_MIME, parseBatchDragPayload } from '../utils/scheduleSessionInsert';
import './MachineScheduleGantt.css';

interface MachineScheduleGanttProps {
  operations: DetailScheduleOperation[];
  className?: string;
  /** 启用拖拽改线/顺序（mouseup 后回调，由父组件触发推演） */
  editable?: boolean;
  onDragCommit?: (patch: GanttDragCommit) => void;
  /** 接受待排批次拖入甘特产线 */
  acceptBatchDrop?: boolean;
  onBatchDrop?: (payload: { batchNo: string; lineId: string; dropMinute: number }) => void;
}

interface TaskCenter {
  x: number;
  y: number;
}

const TIME_SCALE_OPTIONS: TimeScalePreset[] = ['1d', '2d', '4d', '1w', 'fit'];

function useTrackViewportWidth(scrollRef: React.RefObject<HTMLDivElement | null>) {
  const [width, setWidth] = useState(720);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    const measure = () => {
      setWidth(Math.max(320, el.clientWidth - MACHINE_SCHEDULE_LABEL_W));
    };

    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [scrollRef]);

  return width;
}

export function MachineScheduleGantt({
  operations,
  className = '',
  editable = false,
  onDragCommit,
  acceptBatchDrop = false,
  onBatchDrop,
}: MachineScheduleGanttProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const dragStateRef = useRef<{
    operationId: string;
    sourceLineId: string;
    startClientX: number;
    startClientY: number;
    barEl: HTMLDivElement;
    moved: boolean;
  } | null>(null);
  const rafRef = useRef<number | null>(null);
  const suppressClickRef = useRef(false);
  const [timeScale, setTimeScale] = useState<TimeScalePreset>('fit');
  const [zoom, setZoom] = useState(MACHINE_SCHEDULE_ZOOM_DEFAULT);
  const [resourceFilter, setResourceFilter] = useState('');
  const [onlyLinesWithTasks, setOnlyLinesWithTasks] = useState(true);
  const [allLineIds, setAllLineIds] = useState<string[]>([]);
  const [selectedOperationId, setSelectedOperationId] = useState<string | null>(null);
  const [batchDropLineId, setBatchDropLineId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void api.masterData.lines
      .list()
      .then((lines) => {
        if (cancelled) return;
        setAllLineIds(
          lines
            .map((l) => l.lineId?.trim())
            .filter((id): id is string => Boolean(id))
            .sort((a, b) => a.localeCompare(b, 'zh-CN')),
        );
      })
      .catch(() => {
        if (!cancelled) setAllLineIds([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const resourceOptions = useMemo(() => {
    const ids = new Set<string>();
    for (const op of operations) {
      const id = op.resourceId?.trim();
      if (id) ids.add(id);
    }
    return [...ids].sort((a, b) => a.localeCompare(b, 'zh-CN'));
  }, [operations]);

  const filteredOperations = useMemo(() => {
    if (!resourceFilter) return operations;
    return operations.filter((op) => op.resourceId === resourceFilter);
  }, [operations, resourceFilter]);

  const model = useMemo(
    () =>
      buildMachineScheduleModel(filteredOperations, undefined, {
        allLineIds: onlyLinesWithTasks ? undefined : allLineIds,
        includeEmptyLines: !onlyLinesWithTasks,
      }),
    [filteredOperations, onlyLinesWithTasks, allLineIds],
  );

  const operationById = useMemo(() => {
    const map = new Map<string, DetailScheduleOperation>();
    for (const op of filteredOperations) {
      map.set(op.operationId, op);
    }
    return map;
  }, [filteredOperations]);

  const routingChainState = useMemo(() => {
    if (!selectedOperationId) {
      return {
        chainIds: new Set<string>(),
        violationIds: new Set<string>(),
      };
    }
    const selected = operationById.get(selectedOperationId);
    if (!selected) {
      return { chainIds: new Set<string>(), violationIds: new Set<string>() };
    }
    const chain = buildRoutingProcessChain(filteredOperations, selected);
    const violations = findRoutingTimeViolations(chain);
    const violationIds = new Set<string>();
    for (const v of violations) {
      violationIds.add(v.earlier.operationId);
      violationIds.add(v.later.operationId);
    }
    return {
      chainIds: new Set(chain.map((op) => op.operationId)),
      violationIds,
    };
  }, [selectedOperationId, operationById, filteredOperations]);

  const viewportTrackWidth = useTrackViewportWidth(scrollRef);

  const minuteWidth = useMemo(
    () => minuteWidthForTimeScale(timeScale, viewportTrackWidth, zoom),
    [timeScale, viewportTrackWidth, zoom],
  );

  const ticks = useMemo(() => {
    if (!model) return [];
    return buildTicksForTimeScale(
      model.horizonStart,
      model.minMinute,
      model.maxMinute,
      timeScale,
      zoom,
    );
  }, [model, timeScale, zoom]);

  const zoomIn = useCallback(() => {
    setZoom((z) => clampZoom(Math.round((z + MACHINE_SCHEDULE_ZOOM_STEP) * 100) / 100));
  }, []);

  const zoomOut = useCallback(() => {
    setZoom((z) => clampZoom(Math.round((z - MACHINE_SCHEDULE_ZOOM_STEP) * 100) / 100));
  }, []);

  const zoomReset = useCallback(() => {
    setTimeScale('fit');
    setZoom(MACHINE_SCHEDULE_ZOOM_DEFAULT);
  }, []);

  const selectTimeScale = useCallback((preset: TimeScalePreset) => {
    setTimeScale(preset);
    setZoom(MACHINE_SCHEDULE_ZOOM_DEFAULT);
  }, []);

  const onWheel = useCallback((e: React.WheelEvent<HTMLDivElement>) => {
    if (!e.ctrlKey) return;
    e.preventDefault();
    const delta = e.deltaY > 0 ? -MACHINE_SCHEDULE_ZOOM_STEP : MACHINE_SCHEDULE_ZOOM_STEP;
    setZoom((z) => clampZoom(Math.round((z + delta) * 100) / 100));
  }, []);

  const onBarClick = useCallback((operationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedOperationId((prev) => (prev === operationId ? null : operationId));
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedOperationId(null);
  }, []);

  const onTrackDragOver = useCallback(
    (lineId: string, e: React.DragEvent) => {
      if (!acceptBatchDrop || !onBatchDrop) return;
      if (!e.dataTransfer.types.includes(BATCH_DRAG_MIME)) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      setBatchDropLineId(lineId);
    },
    [acceptBatchDrop, onBatchDrop],
  );

  const onTrackDragLeave = useCallback(() => {
    setBatchDropLineId(null);
  }, []);

  const onTrackDrop = useCallback(
    (lineId: string, e: React.DragEvent) => {
      setBatchDropLineId(null);
      if (!acceptBatchDrop || !onBatchDrop || !model) return;
      const batch = parseBatchDragPayload(e.dataTransfer);
      if (!batch) return;
      e.preventDefault();
      e.stopPropagation();
      const trackEl = e.currentTarget as HTMLElement;
      const trackRect = trackEl.getBoundingClientRect();
      const spanMin = Math.max(1, model.maxMinute - model.minMinute);
      const dropMinute = minuteFromTrackX(
        e.clientX,
        trackRect,
        model.minMinute,
        spanMin,
      );
      onBatchDrop({ batchNo: batch.batchNo, lineId, dropMinute });
    },
    [acceptBatchDrop, onBatchDrop, model],
  );

  const finishDrag = useCallback(
    (clientX: number, clientY: number) => {
      const drag = dragStateRef.current;
      dragStateRef.current = null;
      if (!drag || !model || !editable || !onDragCommit) {
        return;
      }
      const bar = drag.barEl;
      bar.classList.remove('dragging');
      bar.style.transform = '';

      if (!drag.moved) {
        return;
      }
      suppressClickRef.current = true;

      const rowEl = document
        .elementFromPoint(clientX, clientY)
        ?.closest('[data-gantt-line-id]') as HTMLElement | null;
      const targetLineId =
        rowEl?.getAttribute('data-gantt-line-id')?.trim() || drag.sourceLineId;
      const trackEl = rowEl?.querySelector('[data-gantt-track]') as HTMLElement | null;
      if (!trackEl) {
        return;
      }
      const trackRect = trackEl.getBoundingClientRect();
      const spanMin = Math.max(1, model.maxMinute - model.minMinute);
      const dropMinute = minuteFromTrackX(clientX, trackRect, model.minMinute, spanMin);
      const row = model.rows.find((r) => r.lineId === targetLineId);
      const sequenceOnLine = row
        ? sequenceOnLineFromDropMinute(row.tasks, dropMinute, drag.operationId)
        : 1;

      onDragCommit({
        operationId: drag.operationId,
        lineId: targetLineId,
        sequenceOnLine,
      });
    },
    [editable, model, onDragCommit],
  );

  const onBarPointerDown = useCallback(
    (task: MachineScheduleTask, sourceLineId: string, e: React.PointerEvent<HTMLDivElement>) => {
      if (!editable || task.pinned || e.button !== 0) {
        return;
      }
      e.stopPropagation();
      e.preventDefault();
      const bar = e.currentTarget;
      bar.setPointerCapture(e.pointerId);
      bar.classList.add('dragging');
      dragStateRef.current = {
        operationId: task.operationId,
        sourceLineId,
        startClientX: e.clientX,
        startClientY: e.clientY,
        barEl: bar,
        moved: false,
      };

      const onMove = (ev: PointerEvent) => {
        const d = dragStateRef.current;
        if (!d) return;
        const dx = ev.clientX - d.startClientX;
        const dy = ev.clientY - d.startClientY;
        if (!d.moved && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
          d.moved = true;
        }
        if (rafRef.current != null) {
          cancelAnimationFrame(rafRef.current);
        }
        rafRef.current = requestAnimationFrame(() => {
          d.barEl.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;
          rafRef.current = null;
        });
      };

      const onUp = (ev: PointerEvent) => {
        bar.releasePointerCapture(ev.pointerId);
        bar.removeEventListener('pointermove', onMove);
        bar.removeEventListener('pointerup', onUp);
        bar.removeEventListener('pointercancel', onUp);
        if (rafRef.current != null) {
          cancelAnimationFrame(rafRef.current);
          rafRef.current = null;
        }
        finishDrag(ev.clientX, ev.clientY);
      };

      bar.addEventListener('pointermove', onMove);
      bar.addEventListener('pointerup', onUp);
      bar.addEventListener('pointercancel', onUp);
    },
    [editable, finishDrag],
  );

  const span = model ? Math.max(1, model.maxMinute - model.minMinute) : 1;
  const trackWidth = span * minuteWidth;

  const leftFor = useCallback(
    (minute: number) => ((minute - (model?.minMinute ?? 0)) / span) * trackWidth,
    [model?.minMinute, span, trackWidth],
  );

  const widthFor = useCallback(
    (start: number, end: number) => Math.max(8, ((end - start) / span) * trackWidth),
    [span, trackWidth],
  );

  const taskPositionIndex = useMemo(() => {
    const map = new Map<string, { rowIndex: number; task: MachineScheduleTask }>();
    if (!model) return map;
    model.rows.forEach((row, rowIndex) => {
      for (const task of row.tasks) {
        map.set(task.operationId, { rowIndex, task });
      }
    });
    return map;
  }, [model]);

  const arrowSegments = useMemo(() => {
    if (!selectedOperationId || !model) return [];
    const selected = operationById.get(selectedOperationId);
    const selectedPos = taskPositionIndex.get(selectedOperationId);
    if (!selected || !selectedPos) return [];

    const centerFor = (rowIndex: number, task: MachineScheduleTask): TaskCenter => ({
      x:
        MACHINE_SCHEDULE_LABEL_W +
        leftFor(task.startMinute) +
        widthFor(task.startMinute, task.endMinute) / 2,
      y: MACHINE_SCHEDULE_HEADER_H + rowIndex * MACHINE_SCHEDULE_ROW_H + MACHINE_SCHEDULE_ROW_H / 2,
    });

    const chain = buildRoutingProcessChain(filteredOperations, selected);
    const segments: { from: TaskCenter; to: TaskCenter }[] = [];

    for (let i = 0; i < chain.length - 1; i++) {
      const fromPos = taskPositionIndex.get(chain[i].operationId);
      const toPos = taskPositionIndex.get(chain[i + 1].operationId);
      if (!fromPos || !toPos) continue;
      segments.push({
        from: centerFor(fromPos.rowIndex, fromPos.task),
        to: centerFor(toPos.rowIndex, toPos.task),
      });
    }
    return segments;
  }, [
    selectedOperationId,
    model,
    operationById,
    taskPositionIndex,
    filteredOperations,
    leftFor,
    widthFor,
  ]);

  if (!model || model.rows.length === 0) {
    return <p className="ms-gantt-empty">暂无排程数据，请先求解排程</p>;
  }

  const totalWidth = MACHINE_SCHEDULE_LABEL_W + trackWidth;
  const gridHeight = MACHINE_SCHEDULE_HEADER_H + model.rows.length * MACHINE_SCHEDULE_ROW_H;

  const zoomPct = Math.round(zoom * 100);
  const scaleHint =
    timeScale === 'fit'
      ? `全部 · ${zoomPct}%`
      : `${TIME_SCALE_LABELS[timeScale]} · ${zoomPct}%`;

  return (
    <section className={`card ms-gantt ${className}`.trim()}>
      <div className="ms-gantt-toolbar">
        <div className="ms-gantt-filters">
          <label className="ms-gantt-filter">
            <span>资源</span>
            <select
              className="input ms-gantt-select"
              value={resourceFilter}
              onChange={(e) => {
                setResourceFilter(e.target.value);
                setSelectedOperationId(null);
              }}
            >
              <option value="">全部</option>
              {resourceOptions.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
          <label className="ms-gantt-filter ms-gantt-check">
            <input
              type="checkbox"
              checked={onlyLinesWithTasks}
              onChange={(e) => setOnlyLinesWithTasks(e.target.checked)}
            />
            仅显示有任务的产线
          </label>
        </div>
        <div className="ms-gantt-legend" aria-label="甘特图例">
          {(['scheduled', 'released', 'feedback'] as GanttTaskDisplayPhase[]).map((phase) => (
            <span key={phase} className={`ms-gantt-leg ms-gantt-leg-${phase}`}>
              {GANTT_PHASE_LABEL[phase]}
            </span>
          ))}
          <span className="ms-gantt-leg ms-gantt-leg-changeover">换型时间</span>
        </div>
        <span className="ms-gantt-hint">
          {editable
            ? '拖拽任务或批次改产线/顺序，松手后增量推演 · Ctrl+滚轮缩放'
            : '点击任务显示工艺链 · 紫框为时间倒挂 · Ctrl+滚轮微调'}
        </span>
        <div className="ms-gantt-zoom">
          <div className="ms-gantt-timescale" role="group" aria-label="时间尺度">
            {TIME_SCALE_OPTIONS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`btn ms-gantt-timescale-btn ${timeScale === preset ? 'active' : ''}`}
                onClick={() => selectTimeScale(preset)}
                title={
                  preset === 'fit'
                    ? '显示全部排程范围'
                    : `可视区域按 ${TIME_SCALE_LABELS[preset]} 宽度缩放`
                }
              >
                {TIME_SCALE_LABELS[preset]}
              </button>
            ))}
          </div>
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomOut} title="缩小">
            −
          </button>
          <span className="ms-gantt-zoom-label" title="当前尺度与微调比例">
            {scaleHint}
          </span>
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomIn} title="放大">
            +
          </button>
          <button type="button" className="btn ms-gantt-zoom-btn" onClick={zoomReset} title="重置为全部">
            重置
          </button>
        </div>
      </div>
      <div className="ms-gantt-scroll" ref={scrollRef} onWheel={onWheel} onClick={clearSelection}>
        <div
          className="ms-gantt-grid ms-gantt-grid-layered"
          style={{ minWidth: totalWidth, height: gridHeight }}
          onClick={(e) => e.stopPropagation()}
        >
          <div
            className="ms-gantt-header"
            style={{
              gridTemplateColumns: `${MACHINE_SCHEDULE_LABEL_W}px ${trackWidth}px`,
              height: MACHINE_SCHEDULE_HEADER_H,
            }}
          >
            <div className="ms-gantt-corner">产线</div>
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
              key={row.lineId}
              className={`ms-gantt-row ${batchDropLineId === row.lineId ? 'batch-drop-target' : ''}`}
              data-gantt-line-id={row.lineId}
              style={{
                gridTemplateColumns: `${MACHINE_SCHEDULE_LABEL_W}px ${trackWidth}px`,
                height: MACHINE_SCHEDULE_ROW_H,
              }}
            >
              <div className="ms-gantt-machine-label" title={row.lineId}>
                {row.lineId}
                <span className="ms-gantt-task-count">{row.tasks.length} 项</span>
              </div>
              <div
                className="ms-gantt-track"
                data-gantt-track
                style={{ width: trackWidth }}
                onDragOver={acceptBatchDrop ? (e) => onTrackDragOver(row.lineId, e) : undefined}
                onDragLeave={acceptBatchDrop ? onTrackDragLeave : undefined}
                onDrop={acceptBatchDrop ? (e) => onTrackDrop(row.lineId, e) : undefined}
              >
                {ticks.map((tick) => (
                  <div
                    key={tick.minute}
                    className="ms-gantt-grid-line"
                    style={{ left: leftFor(tick.minute) }}
                  />
                ))}
                {row.changeovers.map((gap, gapIdx) => {
                  const coStyle = changeoverBarStyle();
                  const left = leftFor(gap.startMinute);
                  const width = widthFor(gap.startMinute, gap.endMinute);
                  const coMin = gap.endMinute - gap.startMinute;
                  const startLabel = fmtMinute(model.horizonStart, gap.startMinute);
                  const endLabel = fmtMinute(model.horizonStart, gap.endMinute);
                  return (
                    <div
                      key={`co-${row.lineId}-${gapIdx}`}
                      className="ms-gantt-bar ms-gantt-changeover"
                      style={{
                        left,
                        width,
                        backgroundColor: coStyle.backgroundColor,
                        borderColor: coStyle.borderColor,
                      }}
                      title={`换型 ${coMin} 分 · ${startLabel} → ${endLabel}`}
                      aria-hidden
                    />
                  );
                })}
                {row.tasks.map((task) => {
                  const left = leftFor(task.startMinute);
                  const width = widthFor(task.startMinute, task.endMinute);
                  const barStyle = taskBarStyle(task);
                  const startLabel = fmtMinute(model.horizonStart, task.startMinute);
                  const endLabel = fmtMinute(model.horizonStart, task.endMinute);
                  const isSelected = task.operationId === selectedOperationId;
                  const inRoutingChain = routingChainState.chainIds.has(task.operationId);
                  const routingViolation = routingChainState.violationIds.has(task.operationId);
                  return (
                    <div
                      key={task.operationId}
                      role="button"
                      tabIndex={0}
                      className={`ms-gantt-bar phase-${task.displayPhase} ${task.pinned ? 'pinned' : ''} ${isSelected ? 'selected' : ''} ${inRoutingChain ? 'downstream' : ''} ${routingViolation ? 'routing-violation' : ''} ${editable && !task.pinned ? 'editable' : ''}`}
                      style={{
                        left,
                        width,
                        backgroundColor: barStyle.backgroundColor,
                        borderColor: barStyle.borderColor,
                      }}
                      title={`${GANTT_PHASE_LABEL[task.displayPhase]} · #${task.sequenceIndex} ${task.batchNo ?? task.workOrderNo} · ${task.productCode}${task.resourceId ? ` · ${task.resourceId}` : ''}\n${startLabel} → ${endLabel}${editable && !task.pinned ? '\n拖拽可调整' : ''}`}
                      onPointerDown={(e) => onBarPointerDown(task, row.lineId, e)}
                      onClick={(e) => {
                        if (suppressClickRef.current) {
                          suppressClickRef.current = false;
                          e.stopPropagation();
                          return;
                        }
                        onBarClick(task.operationId, e);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          onBarClick(task.operationId, e as unknown as React.MouseEvent);
                        }
                      }}
                    >
                      <span className="ms-gantt-bar-text">
                        #{task.sequenceIndex} {task.batchNo ?? task.workOrderNo}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}

          {arrowSegments.length > 0 && (
            <svg
              className="ms-gantt-arrows"
              width={totalWidth}
              height={gridHeight}
              aria-hidden
            >
              <defs>
                <marker
                  id="ms-gantt-arrowhead"
                  markerWidth="8"
                  markerHeight="8"
                  refX="7"
                  refY="4"
                  orient="auto"
                >
                  <path d="M0,0 L8,4 L0,8 Z" fill="#4f46e5" />
                </marker>
              </defs>
              {arrowSegments.map((seg, index) => (
                <line
                  key={`${seg.from.x}-${seg.from.y}-${seg.to.x}-${seg.to.y}-${index}`}
                  x1={seg.from.x}
                  y1={seg.from.y}
                  x2={seg.to.x}
                  y2={seg.to.y}
                  className="ms-gantt-arrow-line"
                  markerEnd="url(#ms-gantt-arrowhead)"
                />
              ))}
            </svg>
          )}
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
