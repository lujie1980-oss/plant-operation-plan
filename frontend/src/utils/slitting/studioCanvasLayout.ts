import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import { findMasterForNode, nodeLength, nodeWidth } from './studioGeometry';

export type CanvasPlacement = {
  nodeId: string;
  nodeType: SlittingRollNode['nodeType'];
  x: number;
  y: number;
  w: number;
  h: number;
  rotated: boolean;
};

export { findMasterForNode };

/** 图形区域母卷长宽比上限（长边:短边） */
export const MAX_MASTER_BOARD_ASPECT_RATIO = 5;

export type MasterCanvasMapping = {
  realLengthMm: number;
  realWidthMm: number;
  displayLengthMm: number;
  displayWidthMm: number;
  compressX: number;
  compressY: number;
  aspectCompressed: boolean;
  realAspectRatio: number;
};

/** 将真实母卷尺寸映射为不超过 5:1 的显示尺寸（仅影响图形，不改变排样数据） */
export function masterCanvasMapping(lengthMm: number, widthMm: number): MasterCanvasMapping {
  const realLength = Math.max(lengthMm, 1);
  const realWidth = Math.max(widthMm, 1);
  let displayLength = realLength;
  let displayWidth = realWidth;
  let compressX = 1;
  let compressY = 1;

  if (realLength / realWidth > MAX_MASTER_BOARD_ASPECT_RATIO) {
    displayLength = realWidth * MAX_MASTER_BOARD_ASPECT_RATIO;
    compressX = displayLength / realLength;
  } else if (realWidth / realLength > MAX_MASTER_BOARD_ASPECT_RATIO) {
    displayWidth = realLength * MAX_MASTER_BOARD_ASPECT_RATIO;
    compressY = displayWidth / realWidth;
  }

  const ratio = Math.max(realLength / realWidth, realWidth / realLength);

  return {
    realLengthMm: realLength,
    realWidthMm: realWidth,
    displayLengthMm: displayLength,
    displayWidthMm: displayWidth,
    compressX,
    compressY,
    aspectCompressed: ratio > MAX_MASTER_BOARD_ASPECT_RATIO,
    realAspectRatio: realLength / realWidth,
  };
}

export function mapPlacementToDisplay(
  p: CanvasPlacement,
  mapping: MasterCanvasMapping,
): CanvasPlacement {
  return {
    ...p,
    x: p.x * mapping.compressX,
    y: p.y * mapping.compressY,
    w: p.w * mapping.compressX,
    h: p.h * mapping.compressY,
  };
}

/** 在母卷画板坐标系下汇总区域与子卷矩形 */
export function placementsOnMaster(
  masterId: string,
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
): CanvasPlacement[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const onMaster = assignments.filter((a) => a.parentNodeId === masterId);
  const out: CanvasPlacement[] = [];

  for (const a of onMaster) {
    const n = nodeById.get(a.childNodeId);
    if (!n) continue;
    if (n.nodeType === 'INTERMEDIATE') {
      out.push({
        nodeId: n.nodeId,
        nodeType: n.nodeType,
        x: a.posXMm,
        y: a.posYMm,
        w: nodeLength(n),
        h: nodeWidth(n),
        rotated: false,
      });
    }
  }

  for (const a of assignments) {
    const child = nodeById.get(a.childNodeId);
    const parent = nodeById.get(a.parentNodeId);
    if (!child || child.nodeType !== 'CHILD' || !parent || parent.nodeType !== 'INTERMEDIATE') continue;
    const regionAssign = onMaster.find((x) => x.childNodeId === parent.nodeId);
    if (!regionAssign) continue;
    const w = a.rotated ? child.widthMm : child.lengthMm;
    const h = a.rotated ? child.lengthMm : child.widthMm;
    out.push({
      nodeId: child.nodeId,
      nodeType: 'CHILD',
      x: regionAssign.posXMm + a.posXMm,
      y: regionAssign.posYMm + a.posYMm,
      w,
      h,
      rotated: a.rotated,
    });
  }

  return out;
}
