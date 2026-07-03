import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Group, Layer, Line, Rect, Stage, Text } from 'react-konva';
import type { KonvaEventObject } from 'konva/lib/Node';
import type { ChildSlittingOrder, MasterRoll, SlittingAssignment, SlittingRollNode } from '../../../types/slitting';
import { nodeLength, nodeWidth } from '../../../utils/slitting/studioGeometry';
import { assignmentForNode } from '../../../utils/slitting/studioLock';
import { placementsOnMaster, masterCanvasMapping, mapPlacementToDisplay } from '../../../utils/slitting/studioCanvasLayout';
import { slittingNodeLabel } from '../../../utils/slitting/display';
import {
  formatCanvasLabels,
} from '../../../utils/slitting/canvasDisplayConfig';
import { ORDER_DRAG_TYPE, parseOrderDrag } from './OrderPool';
import { CanvasLabelSettings, useCanvasLabelKeys } from './CanvasLabelSettings';

const RULER_SIZE = 36;
const LEFT_PAD = 8;
const FILL_RATIO = 0.8;
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 4;
const ZOOM_STEP = 0.15;
const WHEEL_ZOOM_STEP = 0.08;

type Props = {
  masterNode: SlittingRollNode | null;
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  selectedNodeId: string | null;
  allOrders: ChildSlittingOrder[];
  allMasters: MasterRoll[];
  onSelectNode: (nodeId: string | null) => void;
  onOrderDropOnMaster: (orderCode: string, masterNodeId: string) => void;
};

function pickTickStepMm(spanMm: number, targetTicks: number): number {
  const raw = spanMm / targetTicks;
  const candidates = [10, 20, 50, 100, 200, 500, 1000, 2000, 5000];
  for (const c of candidates) {
    if (c >= raw) return c;
  }
  return candidates[candidates.length - 1];
}

function orderForChildNode(node: SlittingRollNode, orders: ChildSlittingOrder[]): ChildSlittingOrder | undefined {
  if (node.nodeType !== 'CHILD') return undefined;
  const m = node.nodeId.match(/^CHILD-(.+)-(\d+)$/);
  if (!m) return undefined;
  return orders.find((o) => o.orderCode === m[1]);
}

export function StudioCanvas({
  masterNode,
  nodes,
  assignments,
  selectedNodeId,
  allOrders,
  allMasters,
  onSelectNode,
  onOrderDropOnMaster,
}: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 640, h: 400 });
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [labelKeys, setLabelKeys] = useCanvasLabelKeys();
  const panDragRef = useRef<{ active: boolean; lastX: number; lastY: number }>({
    active: false,
    lastX: 0,
    lastY: 0,
  });

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const { width, height } = entries[0]?.contentRect ?? { width: 640, height: 400 };
      setSize({ w: Math.max(280, Math.floor(width)), h: Math.max(240, Math.floor(height)) });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const placements = useMemo(() => {
    if (!masterNode) return [];
    return placementsOnMaster(masterNode.nodeId, nodes, assignments);
  }, [masterNode, nodes, assignments]);

  const board = useMemo(() => {
    if (!masterNode) return null;
    const lengthMm = nodeLength(masterNode);
    const widthMm = nodeWidth(masterNode);
    return { lengthMm, widthMm, mapping: masterCanvasMapping(lengthMm, widthMm) };
  }, [masterNode]);

  const drawAreaH = Math.max(1, size.h - RULER_SIZE);
  const contentW = Math.max(1, size.w - RULER_SIZE);
  const contentH = drawAreaH;

  const baseScale = useMemo(() => {
    if (!board) return 1;
    const availW = contentW * FILL_RATIO;
    const availH = contentH * FILL_RATIO;
    const { displayLengthMm, displayWidthMm } = board.mapping;
    return Math.min(availW / displayLengthMm, availH / displayWidthMm);
  }, [board, contentW, contentH]);

  const scale = baseScale * zoom;

  const fitView = useCallback(() => {
    if (!board) return;
    const pxH = board.mapping.displayWidthMm * baseScale;
    const originX = RULER_SIZE + LEFT_PAD;
    // 在标尺内绘图区（y: 0 .. drawAreaH）垂直居中，勿再加 RULER_SIZE 偏移
    const originY = Math.max(0, (drawAreaH - pxH) / 2);
    setZoom(1);
    setPan({ x: originX, y: originY });
  }, [board, drawAreaH, baseScale]);

  useEffect(() => {
    fitView();
  }, [masterNode?.nodeId, size.w, size.h, fitView]);

  const onWheel = (e: KonvaEventObject<WheelEvent>) => {
    e.evt.preventDefault();
    const delta = e.evt.deltaY > 0 ? -WHEEL_ZOOM_STEP : WHEEL_ZOOM_STEP;
    setZoom((z) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z + delta)));
  };

  const masterRoll = useMemo(() => {
    if (!masterNode || masterNode.nodeType !== 'MASTER') return undefined;
    const rollCode = masterNode.nodeId.replace(/^MASTER-/, '');
    return allMasters.find((m) => m.rollCode === rollCode);
  }, [masterNode, allMasters]);

  const masterLabel = useMemo(() => {
    if (!masterNode || !board) return '';
    return formatCanvasLabels(labelKeys, {
      orderCode: masterRoll?.rollCode,
      productCode: masterRoll?.productCode ?? masterRoll?.materialCode,
      widthMm: board.widthMm,
      lengthMm: board.lengthMm,
      thicknessMm: masterRoll?.thicknessMm ?? masterNode.thicknessMm,
      nodeLabel: slittingNodeLabel(masterNode),
    });
  }, [masterNode, masterRoll, board, labelKeys]);

  const displayPlacements = useMemo(() => {
    if (!board) return [];
    return placements.map((p) => mapPlacementToDisplay(p, board.mapping));
  }, [placements, board]);

  const xTickStep = board ? pickTickStepMm(board.lengthMm, 8) : 100;
  const yTickStep = board ? pickTickStepMm(board.widthMm, 6) : 100;

  const mapXToScreen = (realMm: number) =>
    board ? pan.x + realMm * board.mapping.compressX * scale : 0;
  const mapYToScreen = (realMm: number) =>
    board ? pan.y + realMm * board.mapping.compressY * scale : 0;

  if (!masterNode || !board) {
    return (
      <section className="slitting-studio-panel slitting-studio-panel--canvas">
        <h3 className="slitting-panel-title">图形化区域</h3>
        <div className="slitting-canvas-empty">
          <strong>请先加入母卷</strong>
          <span>从左上选择库存母卷并拖入分切树，或选中已在树中的母卷</span>
          <span>横轴 = 长度 (mm)，纵轴 = 宽度 (mm)</span>
        </div>
      </section>
    );
  }

  const xTicks: number[] = [];
  for (let mm = 0; mm <= board.lengthMm; mm += xTickStep) {
    xTicks.push(mm);
  }
  const yTicks: number[] = [];
  for (let mm = 0; mm <= board.widthMm; mm += yTickStep) {
    yTicks.push(mm);
  }

  return (
    <section className="slitting-studio-panel slitting-studio-panel--canvas">
      <div className="slitting-canvas-head">
        <h3 className="slitting-panel-title">图形化区域</h3>
        <CanvasLabelSettings keys={labelKeys} onChange={setLabelKeys} />
      </div>
      <p className="slitting-panel-hint slitting-studio-axis">
        {slittingNodeLabel(masterNode)} · {board.lengthMm}×{board.widthMm} mm · 左对齐 · 占区域{' '}
        {Math.round(FILL_RATIO * 100)}%
        {board.mapping.aspectCompressed
          ? ` · 实际长宽比 ${board.mapping.realAspectRatio.toFixed(1)}:1，图形显示压缩至 5:1`
          : ''}
      </p>
      <div className="slitting-canvas-controls slitting-studio-canvas-controls">
        <button type="button" className="btn" onClick={() => setZoom((z) => Math.min(MAX_ZOOM, z + ZOOM_STEP))}>
          放大
        </button>
        <button type="button" className="btn" onClick={() => setZoom((z) => Math.max(MIN_ZOOM, z - ZOOM_STEP))}>
          缩小
        </button>
        <button type="button" className="btn" onClick={fitView}>
          适应视口
        </button>
        <span>{Math.round(zoom * 100)}% · 滚轮缩放 · Shift+拖动平移</span>
      </div>
      <div
        ref={wrapRef}
        className="slitting-canvas-wrap slitting-studio-canvas-wrap slitting-studio-canvas-wrap--ruler"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          const raw = e.dataTransfer.getData(ORDER_DRAG_TYPE);
          const p = parseOrderDrag(raw);
          if (!p) return;
          onOrderDropOnMaster(p.orderCode, masterNode.nodeId);
        }}
      >
        <Stage
          width={size.w}
          height={size.h}
          className={isPanning ? 'slitting-stage is-panning' : 'slitting-stage'}
          onWheel={onWheel}
          onMouseDown={(e) => {
            if (e.evt.shiftKey && e.target === e.target.getStage()) {
              panDragRef.current = { active: true, lastX: e.evt.clientX, lastY: e.evt.clientY };
              setIsPanning(true);
            } else if (e.target === e.target.getStage()) {
              onSelectNode(null);
            }
          }}
          onMouseMove={(e) => {
            if (!panDragRef.current.active) return;
            const dx = e.evt.clientX - panDragRef.current.lastX;
            const dy = e.evt.clientY - panDragRef.current.lastY;
            panDragRef.current.lastX = e.evt.clientX;
            panDragRef.current.lastY = e.evt.clientY;
            setPan((p) => ({ x: p.x + dx, y: p.y + dy }));
          }}
          onMouseUp={() => {
            panDragRef.current.active = false;
            setIsPanning(false);
          }}
        >
          <Layer listening={false}>
            <Rect x={0} y={0} width={RULER_SIZE} height={size.h} fill="#f8fafc" />
            <Rect x={0} y={size.h - RULER_SIZE} width={size.w} height={RULER_SIZE} fill="#f8fafc" />
            <Line
              points={[RULER_SIZE, 0, RULER_SIZE, size.h - RULER_SIZE]}
              stroke="#94a3b8"
              strokeWidth={1}
            />
            <Line
              points={[RULER_SIZE, size.h - RULER_SIZE, size.w, size.h - RULER_SIZE]}
              stroke="#94a3b8"
              strokeWidth={1}
            />
            {yTicks.map((mm) => {
              const y = mapYToScreen(mm);
              if (y < RULER_SIZE || y > size.h - RULER_SIZE) return null;
              return (
                <Group key={`yt-${mm}`}>
                  <Line points={[RULER_SIZE - 6, y, RULER_SIZE, y]} stroke="#64748b" strokeWidth={1} />
                  <Text
                    text={String(mm)}
                    x={2}
                    y={y - 5}
                    fontSize={9}
                    fill="#64748b"
                    width={RULER_SIZE - 10}
                    align="right"
                  />
                </Group>
              );
            })}
            {xTicks.map((mm) => {
              const x = mapXToScreen(mm);
              if (x < RULER_SIZE || x > size.w) return null;
              return (
                <Group key={`xt-${mm}`}>
                  <Line
                    points={[x, size.h - RULER_SIZE, x, size.h - RULER_SIZE + 6]}
                    stroke="#64748b"
                    strokeWidth={1}
                  />
                  <Text
                    text={String(mm)}
                    x={x - 16}
                    y={size.h - RULER_SIZE + 8}
                    fontSize={9}
                    fill="#64748b"
                    width={32}
                    align="center"
                  />
                </Group>
              );
            })}
            <Text text="Y→宽(mm)" x={4} y={4} fontSize={9} fill="#475569" />
            <Text text="X→长(mm)" x={RULER_SIZE + 4} y={size.h - RULER_SIZE + 20} fontSize={9} fill="#475569" />
          </Layer>

          <Layer>
            <Group x={pan.x} y={pan.y} scaleX={scale} scaleY={scale}>
              <Rect
                width={board.mapping.displayLengthMm}
                height={board.mapping.displayWidthMm}
                fill="#f1f5f9"
                stroke="#334155"
                strokeWidth={2 / scale}
                onClick={() => onSelectNode(masterNode.nodeId)}
              />
              {masterLabel ? (
                <Text
                  text={masterLabel}
                  x={6 / scale}
                  y={6 / scale}
                  fontSize={11 / scale}
                  fill="#334155"
                  lineHeight={1.25 / scale}
                  listening={false}
                />
              ) : null}
              <Line
                points={[0, board.mapping.displayWidthMm / 2, board.mapping.displayLengthMm, board.mapping.displayWidthMm / 2]}
                stroke="#94a3b8"
                strokeWidth={1 / scale}
                dash={[6 / scale, 4 / scale]}
                listening={false}
              />
              {displayPlacements.map((p) => {
                const node = nodes.find((n) => n.nodeId === p.nodeId);
                const selected = selectedNodeId === p.nodeId;
                const isRegion = p.nodeType === 'INTERMEDIATE';
                const rawPlacement = placements.find((x) => x.nodeId === p.nodeId);
                const placementAssignment = assignmentForNode(p.nodeId, assignments);
                const locked = Boolean(placementAssignment?.pinned);
                const order = node ? orderForChildNode(node, allOrders) : undefined;
                const label = node
                  ? formatCanvasLabels(labelKeys, {
                      orderCode: order?.orderCode,
                      productCode: order?.productCode,
                      widthMm: rawPlacement?.h ?? p.h,
                      lengthMm: rawPlacement?.w ?? p.w,
                      thicknessMm: order?.thicknessMm ?? node.thicknessMm,
                      salesOrderNo: order?.salesOrderNo,
                      nodeLabel: slittingNodeLabel(node),
                    })
                  : '';
                return (
                  <Group key={p.nodeId}>
                    <Rect
                      x={p.x}
                      y={p.y}
                      width={p.w}
                      height={p.h}
                      fill={isRegion ? 'rgba(15,118,110,0.18)' : 'rgba(147,197,253,0.85)'}
                      stroke={locked ? '#7c3aed' : selected ? '#b45309' : isRegion ? '#0f766e' : '#1d4ed8'}
                      strokeWidth={(selected ? 4 : locked ? 3 : 2) / scale}
                      dash={locked || (selected && isRegion) ? [8 / scale, 4 / scale] : undefined}
                      onClick={(e) => {
                        e.cancelBubble = true;
                        onSelectNode(p.nodeId);
                      }}
                      onTap={() => onSelectNode(p.nodeId)}
                    />
                    {label ? (
                      <Text
                        text={label}
                        x={p.x + 4 / scale}
                        y={p.y + 4 / scale}
                        fontSize={10 / scale}
                        fill="#1e293b"
                        lineHeight={1.2 / scale}
                        width={Math.max(0, p.w - 8 / scale)}
                        listening={false}
                      />
                    ) : null}
                  </Group>
                );
              })}
            </Group>
          </Layer>
        </Stage>
      </div>
      <p className="slitting-panel-hint">点击图形可选中；拖订单到画板将按母卷流程处理。</p>
    </section>
  );
}
