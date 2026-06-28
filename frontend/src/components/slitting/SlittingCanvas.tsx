import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Group, Layer, Line, Rect, Stage, Text } from 'react-konva';
import type { KonvaEventObject } from 'konva/lib/Node';
import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle } from '../../utils/slitting/display';
import {
  assignmentRect,
  collidesWithAny,
  effectiveSize,
  type Rect as AabbRect,
} from '../../utils/slitting/satCollision';

const PADDING = 28;
const GRID_STEP_MM = 100;
const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;

type Props = {
  parentNode: SlittingRollNode | null;
  childNodes: Map<string, SlittingRollNode>;
  assignments: SlittingAssignment[];
  selectedAssignmentId: string | null;
  hoveredNodeId: string | null;
  sessionActive: boolean;
  onSelect: (assignmentId: string | null) => void;
  onHoverNode: (nodeId: string | null) => void;
  onMove: (assignmentId: string, x: number, y: number) => void;
};

function gridLines(w: number, h: number, step: number): number[] {
  const coords: number[] = [];
  for (let x = step; x < w; x += step) {
    coords.push(x, 0, x, h);
  }
  for (let y = step; y < h; y += step) {
    coords.push(0, y, w, y);
  }
  return coords;
}

export function SlittingCanvas({
  parentNode,
  childNodes,
  assignments,
  selectedAssignmentId,
  hoveredNodeId,
  sessionActive,
  onSelect,
  onHoverNode,
  onMove,
}: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const dragStart = useRef<Record<string, { x: number; y: number }>>({});
  const [size, setSize] = useState({ w: 640, h: 420 });
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: PADDING, y: PADDING });
  const [collisionId, setCollisionId] = useState<string | null>(null);
  const panDragRef = useRef<{ active: boolean; lastX: number; lastY: number }>({
    active: false,
    lastX: 0,
    lastY: 0,
  });

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const { width, height } = entries[0]?.contentRect ?? { width: 640, height: 420 };
      setSize({ w: Math.max(320, Math.floor(width)), h: Math.max(280, Math.floor(height)) });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const baseScale = useMemo(() => {
    if (!parentNode) return 1;
    return (
      Math.min((size.w - PADDING * 2) / parentNode.widthMm, (size.h - PADDING * 2) / parentNode.lengthMm) *
      0.92
    );
  }, [parentNode, size]);

  const fitView = useCallback(() => {
    setZoom(1);
    setPan({ x: PADDING, y: PADDING });
  }, []);

  useEffect(() => {
    fitView();
  }, [parentNode?.nodeId, fitView]);

  const visibleAssignments = useMemo(
    () => (parentNode ? assignments.filter((a) => a.parentNodeId === parentNode.nodeId) : []),
    [assignments, parentNode],
  );

  const parentBounds: AabbRect | null = parentNode
    ? { x: 0, y: 0, w: parentNode.widthMm, h: parentNode.lengthMm }
    : null;

  const otherRects = (excludeId: string): AabbRect[] =>
    visibleAssignments
      .filter((a) => a.assignmentId !== excludeId)
      .map((a) => {
        const n = childNodes.get(a.childNodeId);
        if (!n) return { x: 0, y: 0, w: 0, h: 0 };
        return assignmentRect(a.posXMm, a.posYMm, n.widthMm, n.lengthMm, a.rotated);
      });

  const handleDragEnd = (a: SlittingAssignment, e: KonvaEventObject<DragEvent>) => {
    if (!parentBounds) return;
    const node = childNodes.get(a.childNodeId);
    if (!node) return;
    const x = e.target.x();
    const y = e.target.y();
    const { w, h } = effectiveSize(node.widthMm, node.lengthMm, a.rotated);
    const candidate = { x, y, w, h };
    if (collidesWithAny(candidate, otherRects(a.assignmentId), parentBounds)) {
      setCollisionId(a.assignmentId);
      e.target.position({
        x: dragStart.current[a.assignmentId]?.x ?? a.posXMm,
        y: dragStart.current[a.assignmentId]?.y ?? a.posYMm,
      });
      return;
    }
    setCollisionId(null);
    onMove(a.assignmentId, x, y);
  };

  const onWheel = (e: KonvaEventObject<WheelEvent>) => {
    e.evt.preventDefault();
    const delta = e.evt.deltaY > 0 ? -0.08 : 0.08;
    setZoom((z) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z + delta)));
  };

  if (!parentNode) {
    return (
      <div className="slitting-canvas-empty">
        <strong>尚未加载画板</strong>
        <span>请在工具栏选择方案，或从「分切方案」页带 plan 参数进入。</span>
        <span>在中间卷节点上钻取后可编辑该层排样。</span>
      </div>
    );
  }

  const scale = baseScale * zoom;
  const gridPoints = gridLines(parentNode.widthMm, parentNode.lengthMm, GRID_STEP_MM);

  return (
    <div className="slitting-workbench-center">
      <div className="slitting-legend" aria-label="图例">
        <span className="slitting-legend-item">
          <span className="slitting-legend-swatch slitting-legend-swatch--child" /> 子卷
        </span>
        <span className="slitting-legend-item">
          <span className="slitting-legend-swatch slitting-legend-swatch--inter" /> 中间卷
        </span>
        <span className="slitting-legend-item">
          <span className="slitting-legend-swatch slitting-legend-swatch--collision" /> 碰撞
        </span>
        {sessionActive ? (
          <span className="slitting-legend-item">
            <span className="slitting-legend-swatch slitting-legend-swatch--pinned" /> 已锁定
          </span>
        ) : null}
        <span className="slitting-legend-item">滚轮缩放 · Shift+拖动画布平移</span>
      </div>
      <div className="slitting-canvas-controls">
        <button type="button" className="btn" onClick={() => setZoom((z) => Math.min(MAX_ZOOM, z + 0.15))}>
          放大
        </button>
        <button type="button" className="btn" onClick={() => setZoom((z) => Math.max(MIN_ZOOM, z - 0.15))}>
          缩小
        </button>
        <button type="button" className="btn" onClick={fitView}>
          适应视口
        </button>
        <span>
          {Math.round(zoom * 100)}% · {slittingNodeLabel(parentNode)} ({slittingNodeSubtitle(parentNode)})
        </span>
      </div>
      <div ref={wrapRef} className="slitting-canvas-wrap">
        <Stage
          width={size.w}
          height={size.h}
          className={panDragRef.current.active ? 'slitting-stage is-panning' : 'slitting-stage'}
          onWheel={onWheel}
          onMouseDown={(e) => {
            const shift = e.evt.shiftKey;
            const isPanSurface = e.target.name() === 'slitting-pan-surface';
            if (shift && (e.target === e.target.getStage() || isPanSurface)) {
              panDragRef.current = { active: true, lastX: e.evt.clientX, lastY: e.evt.clientY };
            } else if (e.target === e.target.getStage() || isPanSurface) {
              onSelect(null);
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
          }}
        >
          <Layer>
            <Group x={pan.x} y={pan.y} scaleX={scale} scaleY={scale}>
              <Rect
                name="slitting-pan-surface"
                width={parentNode.widthMm}
                height={parentNode.lengthMm}
                stroke="#334155"
                strokeWidth={2 / scale}
                fill="#f1f5f9"
              />
              <Line points={gridPoints} stroke="rgba(15,118,110,0.15)" strokeWidth={1 / scale} listening={false} />
              <Line
                points={[2, 2, parentNode.widthMm - 2, 2, parentNode.widthMm - 2, parentNode.lengthMm - 2, 2, parentNode.lengthMm - 2, 2, 2]}
                stroke="#0f766e"
                strokeWidth={1 / scale}
                dash={[4 / scale, 4 / scale]}
                listening={false}
              />
              <Text
                text={`${slittingNodeLabel(parentNode)} · ${parentNode.widthMm}×${parentNode.lengthMm} mm`}
                x={6}
                y={6}
                fontSize={13 / scale}
                fill="#475569"
                listening={false}
              />
              {visibleAssignments.map((a) => {
                const child = childNodes.get(a.childNodeId);
                if (!child) return null;
                const { w, h } = effectiveSize(child.widthMm, child.lengthMm, a.rotated);
                const isCollision = collisionId === a.assignmentId;
                const isSelected = selectedAssignmentId === a.assignmentId;
                const isHovered =
                  hoveredNodeId === a.childNodeId || hoveredNodeId === child.nodeId;
                const pinned = Boolean(a.pinned);
                const fill = isCollision
                  ? '#fecaca'
                  : pinned
                    ? '#cbd5e1'
                    : child.nodeType === 'CHILD'
                      ? '#93c5fd'
                      : '#fcd34d';
                const stroke = isCollision
                  ? '#dc2626'
                  : isSelected
                    ? '#0f766e'
                    : isHovered
                      ? '#115e59'
                      : '#1d4ed8';
                const strokeW = (isSelected || isHovered ? 3 : 2) / scale;
                return (
                  <Rect
                    key={a.assignmentId}
                    x={a.posXMm}
                    y={a.posYMm}
                    width={w}
                    height={h}
                    fill={fill}
                    opacity={pinned ? 0.88 : 1}
                    stroke={stroke}
                    strokeWidth={strokeW}
                    dash={pinned ? [6 / scale, 4 / scale] : undefined}
                    draggable={!pinned}
                    onClick={(e) => {
                      e.cancelBubble = true;
                      onSelect(a.assignmentId);
                    }}
                    onMouseEnter={() => onHoverNode(child.nodeId)}
                    onMouseLeave={() => onHoverNode(null)}
                    onDragStart={(e) => {
                      e.cancelBubble = true;
                      dragStart.current[a.assignmentId] = { x: a.posXMm, y: a.posYMm };
                      onSelect(a.assignmentId);
                    }}
                    onDragEnd={(e) => {
                      e.cancelBubble = true;
                      handleDragEnd(a, e);
                    }}
                  />
                );
              })}
            </Group>
          </Layer>
        </Stage>
      </div>
    </div>
  );
}
