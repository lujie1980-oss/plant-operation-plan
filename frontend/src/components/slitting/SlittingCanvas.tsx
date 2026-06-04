import { useMemo, useRef, useState } from 'react';
import { Layer, Rect, Stage, Text } from 'react-konva';
import type { KonvaEventObject } from 'konva/lib/Node';
import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import {
  assignmentRect,
  collidesWithAny,
  effectiveSize,
  type Rect as AabbRect,
} from '../../utils/slitting/satCollision';

const STAGE_W = 720;
const STAGE_H = 480;

type Props = {
  parentNode: SlittingRollNode | null;
  childNodes: Map<string, SlittingRollNode>;
  assignments: SlittingAssignment[];
  onMove: (assignmentId: string, x: number, y: number) => void;
};

export function SlittingCanvas({ parentNode, childNodes, assignments, onMove }: Props) {
  const dragStart = useRef<Record<string, { x: number; y: number }>>({});
  const [collisionId, setCollisionId] = useState<string | null>(null);

  const scale = useMemo(() => {
    if (!parentNode) return 1;
    return Math.min((STAGE_W - 40) / parentNode.widthMm, (STAGE_H - 40) / parentNode.lengthMm) * 0.9;
  }, [parentNode]);

  if (!parentNode) {
    return <div className="slitting-canvas-empty">请选择方案或中间卷节点以查看画板</div>;
  }

  const parentBounds: AabbRect = { x: 0, y: 0, w: parentNode.widthMm, h: parentNode.lengthMm };

  const visibleAssignments = assignments.filter((a) => a.parentNodeId === parentNode.nodeId);

  const otherRects = (excludeId: string): AabbRect[] =>
    visibleAssignments
      .filter((a) => a.assignmentId !== excludeId)
      .map((a) => {
        const n = childNodes.get(a.childNodeId);
        if (!n) return { x: 0, y: 0, w: 0, h: 0 };
        return assignmentRect(a.posXMm, a.posYMm, n.widthMm, n.lengthMm, a.rotated);
      });

  const handleDragEnd = (a: SlittingAssignment, e: KonvaEventObject<DragEvent>) => {
    const node = childNodes.get(a.childNodeId);
    if (!node) return;
    const x = e.target.x() / scale;
    const y = e.target.y() / scale;
    const { w, h } = effectiveSize(node.widthMm, node.lengthMm, a.rotated);
    const candidate = { x, y, w, h };
    if (collidesWithAny(candidate, otherRects(a.assignmentId), parentBounds)) {
      setCollisionId(a.assignmentId);
      e.target.position({
        x: (dragStart.current[a.assignmentId]?.x ?? a.posXMm) * scale,
        y: (dragStart.current[a.assignmentId]?.y ?? a.posYMm) * scale,
      });
      return;
    }
    setCollisionId(null);
    onMove(a.assignmentId, x, y);
  };

  return (
    <Stage width={STAGE_W} height={STAGE_H} className="slitting-stage">
      <Layer scaleX={scale} scaleY={scale} x={20} y={20}>
        <Rect
          width={parentNode.widthMm}
          height={parentNode.lengthMm}
          stroke="#334155"
          strokeWidth={2 / scale}
          fill="#f8fafc"
        />
        <Text text={parentNode.nodeId} x={4} y={4} fontSize={14 / scale} fill="#64748b" />
        {visibleAssignments.map((a) => {
          const child = childNodes.get(a.childNodeId);
          if (!child) return null;
          const { w, h } = effectiveSize(child.widthMm, child.lengthMm, a.rotated);
          const isCollision = collisionId === a.assignmentId;
          return (
            <Rect
              key={a.assignmentId}
              x={a.posXMm}
              y={a.posYMm}
              width={w}
              height={h}
              fill={isCollision ? '#fecaca' : child.nodeType === 'CHILD' ? '#93c5fd' : '#fcd34d'}
              stroke={isCollision ? '#dc2626' : '#1d4ed8'}
              strokeWidth={2 / scale}
              draggable
              onDragStart={() => {
                dragStart.current[a.assignmentId] = { x: a.posXMm, y: a.posYMm };
              }}
              onDragEnd={(e) => handleDragEnd(a, e)}
            />
          );
        })}
      </Layer>
    </Stage>
  );
}
