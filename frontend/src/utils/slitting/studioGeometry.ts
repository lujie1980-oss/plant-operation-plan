import type { OrderOrientation, SlitDirection } from '../../types/slittingStudio';
import type { MasterRoll, SlittingAssignment, SlittingRollNode } from '../../types/slitting';

export const DEFAULT_KERF = 2;

export function findMasterForNode(nodes: SlittingRollNode[], nodeId: string | null): SlittingRollNode | null {
  if (!nodeId) return nodes.find((n) => n.nodeType === 'MASTER') ?? null;
  const byId = new Map(nodes.map((n) => [n.nodeId, n]));
  let cur = byId.get(nodeId);
  while (cur) {
    if (cur.nodeType === 'MASTER') return cur;
    cur = cur.parentNodeId ? byId.get(cur.parentNodeId) : undefined;
  }
  return nodes.find((n) => n.nodeType === 'MASTER') ?? null;
}

export type StudioRect = { x: number; y: number; w: number; h: number };

/** 画板坐标下：X=长度方向余量，Y=宽度方向余量 */
export function kerfForRegion(
  nodes: SlittingRollNode[],
  regionId: string,
  allMasters: MasterRoll[],
): { kerfXMm: number; kerfYMm: number } {
  const master = findMasterForNode(nodes, regionId);
  if (master?.nodeType === 'MASTER') {
    const code = master.nodeId.replace(/^MASTER-/, '');
    const roll = allMasters.find((m) => m.rollCode === code);
    if (roll) {
      return { kerfXMm: roll.kerfLongitudinalMm, kerfYMm: roll.kerfTransverseMm };
    }
  }
  return { kerfXMm: DEFAULT_KERF, kerfYMm: DEFAULT_KERF };
}

/** 两矩形在切边余量下是否冲突（画板坐标：x=长度，y=宽度） */
export function rectsConflictWithKerf(
  a: StudioRect,
  b: StudioRect,
  kerfXMm: number,
  kerfYMm: number,
): boolean {
  const separated =
    a.x + a.w + kerfXMm <= b.x ||
    b.x + b.w + kerfXMm <= a.x ||
    a.y + a.h + kerfYMm <= b.y ||
    b.y + b.h + kerfYMm <= b.y;
  return !separated;
}

export function childPlacementsInRegion(
  regionId: string,
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
): StudioRect[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const rects: StudioRect[] = [];
  for (const a of assignments) {
    if (a.parentNodeId !== regionId) continue;
    const child = nodeById.get(a.childNodeId);
    if (!child || child.nodeType !== 'CHILD') continue;
    const { alongX, alongY } = orderFootprint(child.widthMm, child.lengthMm, a.rotated ? 'vertical' : 'horizontal');
    rects.push({ x: a.posXMm, y: a.posYMm, w: alongX, h: alongY });
  }
  return rects;
}

/** 在区域内为子订单寻找不重叠且满足切边余量的位置 */
/** 区域在父节点画板坐标系下可容纳的最大长×宽 */
export function maxRegionSizeInParent(
  parent: SlittingRollNode,
  posXMm: number,
  posYMm: number,
): { maxLengthMm: number; maxWidthMm: number } {
  return {
    maxLengthMm: Math.max(0, nodeLength(parent) - posXMm),
    maxWidthMm: Math.max(0, nodeWidth(parent) - posYMm),
  };
}

export function regionChildrenStillFit(
  region: SlittingRollNode,
  lengthMm: number,
  widthMm: number,
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
): boolean {
  const trial: SlittingRollNode = { ...region, lengthMm, widthMm };
  for (const a of assignments) {
    if (a.parentNodeId !== region.nodeId) continue;
    const child = nodes.find((n) => n.nodeId === a.childNodeId && n.nodeType === 'CHILD');
    if (!child) continue;
    const orient = a.rotated ? 'vertical' : ('horizontal' as OrderOrientation);
    if (!fitsInRegion(trial, child.widthMm, child.lengthMm, orient)) return false;
    const { alongX, alongY } = orderFootprint(child.widthMm, child.lengthMm, orient);
    if (a.posXMm + alongX > lengthMm + 0.01 || a.posYMm + alongY > widthMm + 0.01) return false;
  }
  return true;
}

export function findPlacementInRegion(
  region: SlittingRollNode,
  existing: StudioRect[],
  footprintW: number,
  footprintH: number,
  kerfXMm: number,
  kerfYMm: number,
): { x: number; y: number } | null {
  const L = nodeLength(region);
  const W = nodeWidth(region);
  const candidates: StudioRect[] = [{ x: 0, y: 0, w: footprintW, h: footprintH }];
  for (const p of existing) {
    candidates.push({ x: p.x + p.w + kerfXMm, y: p.y, w: footprintW, h: footprintH });
    candidates.push({ x: p.x, y: p.y + p.h + kerfYMm, w: footprintW, h: footprintH });
    candidates.push({ x: 0, y: p.y + p.h + kerfYMm, w: footprintW, h: footprintH });
    candidates.push({ x: p.x + p.w + kerfXMm, y: 0, w: footprintW, h: footprintH });
  }
  candidates.sort((a, b) => a.y - b.y || a.x - b.x);
  for (const c of candidates) {
    if (c.x < 0 || c.y < 0) continue;
    if (c.x + c.w > L + 0.01 || c.y + c.h > W + 0.01) continue;
    if (existing.some((e) => rectsConflictWithKerf(c, e, kerfXMm, kerfYMm))) continue;
    return { x: c.x, y: c.y };
  }
  return null;
}

export function nodeLength(node: SlittingRollNode): number {
  return node.lengthMm;
}

export function nodeWidth(node: SlittingRollNode): number {
  return node.widthMm;
}

/** 画板坐标：X=长度，Y=宽度 */
export function orderFootprint(
  widthMm: number,
  lengthMm: number,
  orientation: OrderOrientation,
): { alongX: number; alongY: number; rotated: boolean } {
  if (orientation === 'horizontal') {
    return { alongX: lengthMm, alongY: widthMm, rotated: false };
  }
  return { alongX: widthMm, alongY: lengthMm, rotated: true };
}

export function fitsInRegion(
  region: SlittingRollNode,
  widthMm: number,
  lengthMm: number,
  orientation: OrderOrientation,
): boolean {
  const { alongX, alongY } = orderFootprint(widthMm, lengthMm, orientation);
  return alongX <= nodeLength(region) + 0.01 && alongY <= nodeWidth(region) + 0.01;
}

export type SplitRegionSpec = {
  nodeId: string;
  parentNodeId: string;
  widthMm: number;
  lengthMm: number;
  posXMm: number;
  posYMm: number;
  label: string;
};

/** 与父节点同尺寸的单个区域（整卷） */
export function fullRegionSpec(parent: SlittingRollNode, idPrefix: string): SplitRegionSpec {
  return {
    nodeId: `${idPrefix}-FULL`,
    parentNodeId: parent.nodeId,
    widthMm: nodeWidth(parent),
    lengthMm: nodeLength(parent),
    posXMm: 0,
    posYMm: 0,
    label: `整卷区域 · ${nodeLength(parent)}×${nodeWidth(parent)}`,
  };
}

export function splitNodeIntoTwo(
  parent: SlittingRollNode,
  direction: SlitDirection,
  cutSizeMm: number,
  kerfMm = DEFAULT_KERF,
  idPrefix: string,
): SplitRegionSpec[] {
  const L = nodeLength(parent);
  const W = nodeWidth(parent);
  if (cutSizeMm <= 0) {
    throw new Error('分切尺寸须大于 0');
  }
  if (direction === 'horizontal') {
    if (cutSizeMm >= L - kerfMm) {
      throw new Error(`横向分切长度须小于 ${L - kerfMm} mm`);
    }
    const rem = L - cutSizeMm - kerfMm;
    return [
      {
        nodeId: `${idPrefix}-A`,
        parentNodeId: parent.nodeId,
        widthMm: W,
        lengthMm: cutSizeMm,
        posXMm: 0,
        posYMm: 0,
        label: `区域 A · ${cutSizeMm}×${W}`,
      },
      {
        nodeId: `${idPrefix}-B`,
        parentNodeId: parent.nodeId,
        widthMm: W,
        lengthMm: rem,
        posXMm: cutSizeMm + kerfMm,
        posYMm: 0,
        label: `区域 B · ${rem}×${W}`,
      },
    ];
  }
  if (cutSizeMm >= W - kerfMm) {
    throw new Error(`纵向分切宽度须小于 ${W - kerfMm} mm`);
  }
  const remW = W - cutSizeMm - kerfMm;
  return [
    {
      nodeId: `${idPrefix}-A`,
      parentNodeId: parent.nodeId,
      widthMm: cutSizeMm,
      lengthMm: L,
      posXMm: 0,
      posYMm: 0,
      label: `区域 A · ${L}×${cutSizeMm}`,
    },
    {
      nodeId: `${idPrefix}-B`,
      parentNodeId: parent.nodeId,
      widthMm: remW,
      lengthMm: L,
      posXMm: 0,
      posYMm: cutSizeMm + kerfMm,
      label: `区域 B · ${L}×${remW}`,
    },
  ];
}

/** 母卷在画板 X 轴（长度）居中时的起点 */
export function centeredOriginX(lengthMm: number, viewportLengthMm: number): number {
  return Math.max(0, (viewportLengthMm - lengthMm) / 2);
}

/** Studio 画板坐标 → API 持久化坐标（posX=宽，posY=长） */
export function studioToApiPosition(posXMm: number, posYMm: number): { posXMm: number; posYMm: number } {
  return { posXMm: posYMm, posYMm: posXMm };
}

export function apiToStudioPosition(posXMm: number, posYMm: number): { posXMm: number; posYMm: number } {
  return { posXMm: posYMm, posYMm: posXMm };
}
