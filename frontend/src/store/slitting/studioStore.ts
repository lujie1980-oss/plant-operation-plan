import { create } from 'zustand';
import type { ChildSlittingOrder, MasterRoll, SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import type { CreateRegionInput, PlaceOrderInput } from '../../types/slittingStudio';
import type { RegionCreateMode } from '../../components/slitting/studio/CreateRegionDialog';
import { findMasterForNode } from '../../utils/slitting/studioCanvasLayout';
import { collectDescendantNodeIds } from '../../utils/slitting/studioLock';
import {
  childPlacementsInRegion,
  findPlacementInRegion,
  fitsInRegion,
  fullRegionSpec,
  kerfForRegion,
  maxRegionSizeInParent,
  orderFootprint,
  regionChildrenStillFit,
  splitNodeIntoTwo,
  type SplitRegionSpec,
} from '../../utils/slitting/studioGeometry';

function collectDescendants(nodes: SlittingRollNode[], rootId: string): Set<string> {
  const ids = new Set<string>([rootId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const n of nodes) {
      if (n.parentNodeId && ids.has(n.parentNodeId) && !ids.has(n.nodeId)) {
        ids.add(n.nodeId);
        changed = true;
      }
    }
  }
  return ids;
}

function recomputePools(
  nodes: SlittingRollNode[],
  _assignments: SlittingAssignment[],
  allMasters: MasterRoll[],
  allOrders: ChildSlittingOrder[],
) {
  const usedMasters = new Set<string>();
  for (const n of nodes) {
    if (n.nodeType === 'MASTER') {
      usedMasters.add(n.nodeId.replace(/^MASTER-/, ''));
    }
  }
  const placedOrders = new Set<string>();
  for (const n of nodes) {
    if (n.nodeType === 'CHILD') {
      const m = n.nodeId.match(/^CHILD-(.+)-(\d+)$/);
      if (m) placedOrders.add(m[1]);
    }
  }
  return {
    usedMasterCodes: usedMasters,
    placedOrderCodes: placedOrders,
    masters: allMasters.filter((m) => !usedMasters.has(m.rollCode)),
    orders: allOrders.filter((o) => !placedOrders.has(o.orderCode)),
  };
}

interface StudioStore {
  planVersionId: string | null;
  planName: string;
  allMasters: MasterRoll[];
  allOrders: ChildSlittingOrder[];
  masters: MasterRoll[];
  orders: ChildSlittingOrder[];
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  canvasMasterId: string | null;
  selectedNodeId: string | null;
  usedMasterCodes: Set<string>;
  placedOrderCodes: Set<string>;

  setCatalog: (masters: MasterRoll[], orders: ChildSlittingOrder[]) => void;
  setPlan: (planVersionId: string, planName: string, nodes: SlittingRollNode[], assignments: SlittingAssignment[]) => void;
  clearPlan: () => void;
  addMasterNode: (roll: MasterRoll) => string;
  createRegions: (input: CreateRegionInput & { mode: RegionCreateMode }) => string | null;
  ensureFullRegionOnMaster: (masterNodeId: string) => { ok: true; regionNodeId: string } | { ok: false; message: string };
  placeOrder: (input: PlaceOrderInput) => { ok: true } | { ok: false; message: string };
  resizeRegion: (
    regionNodeId: string,
    lengthMm: number,
    widthMm: number,
  ) => { ok: true } | { ok: false; message: string };
  deleteNode: (nodeId: string) => void;
  selectNode: (nodeId: string | null) => void;
  toggleNodeLock: (nodeId: string) => void;
  getSnapshot: () => { nodes: SlittingRollNode[]; assignments: SlittingAssignment[] };
}

export const useSlittingStudioStore = create<StudioStore>((set, get) => ({
  planVersionId: null,
  planName: '分切工作台',
  allMasters: [],
  allOrders: [],
  masters: [],
  orders: [],
  nodes: [],
  assignments: [],
  canvasMasterId: null,
  selectedNodeId: null,
  usedMasterCodes: new Set(),
  placedOrderCodes: new Set(),

  setCatalog: (masters, orders) => {
    const state = get();
    const pools = recomputePools(state.nodes, state.assignments, masters, orders);
    set({ allMasters: masters, allOrders: orders, ...pools });
  },

  setPlan: (planVersionId, planName, nodes, assignments) => {
    const state = get();
    const pools = recomputePools(nodes, assignments, state.allMasters, state.allOrders);
    const master = nodes.find((n) => n.nodeType === 'MASTER');
    set({
      planVersionId,
      planName,
      nodes,
      assignments,
      canvasMasterId: master?.nodeId ?? null,
      selectedNodeId: null,
      ...pools,
    });
  },

  clearPlan: () =>
    set({
      planVersionId: null,
      nodes: [],
      assignments: [],
      canvasMasterId: null,
      selectedNodeId: null,
      usedMasterCodes: new Set(),
      placedOrderCodes: new Set(),
      masters: get().allMasters,
      orders: get().allOrders,
    }),

  addMasterNode: (roll) => {
    const nodeId = `MASTER-${roll.rollCode}`;
    const node: SlittingRollNode = {
      nodeId,
      nodeType: 'MASTER',
      parentNodeId: null,
      widthMm: roll.widthMm,
      lengthMm: roll.lengthMm,
    };
    const nodes = [...get().nodes, node];
    const pools = recomputePools(nodes, get().assignments, get().allMasters, get().allOrders);
    set({ nodes, canvasMasterId: nodeId, selectedNodeId: nodeId, ...pools });
    return nodeId;
  },

  createRegions: ({ targetNodeId, direction, cutSizeMm, mode }) => {
    const { nodes, assignments } = get();
    const target = nodes.find((n) => n.nodeId === targetNodeId);
    if (!target) throw new Error('节点不存在');
    if (target.nodeType !== 'MASTER' && target.nodeType !== 'INTERMEDIATE') {
      throw new Error('仅母卷或区域可分切');
    }
    if (nodes.some((n) => n.parentNodeId === targetNodeId)) {
      throw new Error('该节点已有子区域，请先删除后再分切');
    }
    const prefix = `REG-${targetNodeId}-${Date.now()}`;
    const master = findMasterForNode(nodes, target.nodeId);
    const rollCode = master?.nodeType === 'MASTER' ? master.nodeId.replace(/^MASTER-/, '') : null;
    const roll = rollCode ? get().allMasters.find((m) => m.rollCode === rollCode) : undefined;
    const kerfMm = roll
      ? Math.max(roll.kerfLongitudinalMm, roll.kerfTransverseMm)
      : 2;
    const specList: SplitRegionSpec[] =
      mode === 'full'
        ? [fullRegionSpec(target, prefix)]
        : splitNodeIntoTwo(target, direction, cutSizeMm, kerfMm, prefix);
    const cutDir = mode === 'full' ? null : direction;
    const newNodes: SlittingRollNode[] = specList.map((s) => ({
      nodeId: s.nodeId,
      nodeType: 'INTERMEDIATE',
      parentNodeId: target.nodeId,
      widthMm: s.widthMm,
      lengthMm: s.lengthMm,
      cuttingMethod: cutDir === 'horizontal' ? 'TRANSVERSE' : cutDir === 'vertical' ? 'LONGITUDINAL' : undefined,
    }));
    const newAssignments: SlittingAssignment[] = specList.map((s, i) => ({
      assignmentId: `ASN-${s.nodeId}`,
      childNodeId: s.nodeId,
      parentNodeId: target.nodeId,
      posXMm: s.posXMm,
      posYMm: s.posYMm,
      rotated: false,
      sequence: i,
      pinned: false,
    }));
    set({
      nodes: [...nodes, ...newNodes],
      assignments: [...assignments, ...newAssignments],
      canvasMasterId: master?.nodeId ?? get().canvasMasterId,
    });
    return specList[0]?.nodeId ?? null;
  },

  ensureFullRegionOnMaster: (masterNodeId) => {
    const { nodes } = get();
    const master = nodes.find((n) => n.nodeId === masterNodeId && n.nodeType === 'MASTER');
    if (!master) return { ok: false, message: '母卷不存在' };
    const regions = nodes.filter((n) => n.parentNodeId === masterNodeId && n.nodeType === 'INTERMEDIATE');
    if (regions.length === 1) {
      return { ok: true, regionNodeId: regions[0].nodeId };
    }
    if (regions.length > 1) {
      return { ok: false, message: '母卷上已有多个区域，请将订单拖到具体区域节点' };
    }
    try {
      const regionId = get().createRegions({
        targetNodeId: masterNodeId,
        direction: 'horizontal',
        cutSizeMm: 0,
        mode: 'full',
      });
      if (!regionId) return { ok: false, message: '创建整卷区域失败' };
      return { ok: true, regionNodeId: regionId };
    } catch (e: unknown) {
      return { ok: false, message: e instanceof Error ? e.message : String(e) };
    }
  },

  placeOrder: ({ regionNodeId, orderCode, orientation }) => {
    const { nodes, assignments, allOrders, allMasters } = get();
    const region = nodes.find((n) => n.nodeId === regionNodeId);
    if (!region || region.nodeType !== 'INTERMEDIATE') {
      return { ok: false, message: '请选择有效区域节点' };
    }
    const order = allOrders.find((o) => o.orderCode === orderCode);
    if (!order) return { ok: false, message: '订单不存在' };
    if (!fitsInRegion(region, order.widthMm, order.lengthMm, orientation)) {
      return {
        ok: false,
        message: `尺寸不匹配：订单 ${order.widthMm}×${order.lengthMm} mm 无法以${orientation === 'horizontal' ? '横向' : '纵向'}放入区域 ${region.lengthMm}×${region.widthMm} mm`,
      };
    }
    const { alongX, alongY, rotated } = orderFootprint(order.widthMm, order.lengthMm, orientation);
    const { kerfXMm, kerfYMm } = kerfForRegion(nodes, regionNodeId, allMasters);
    const existing = childPlacementsInRegion(regionNodeId, nodes, assignments);
    const pos = findPlacementInRegion(region, existing, alongX, alongY, kerfXMm, kerfYMm);
    if (!pos) {
      return {
        ok: false,
        message: `区域内无可用位置：与已有子订单需保持切边余量（长向 ${kerfXMm} mm、宽向 ${kerfYMm} mm），且不可重叠`,
      };
    }
    const childId = `CHILD-${orderCode}-${Date.now()}`;
    const childNode: SlittingRollNode = {
      nodeId: childId,
      nodeType: 'CHILD',
      parentNodeId: region.nodeId,
      widthMm: order.widthMm,
      lengthMm: order.lengthMm,
    };
    const assignment: SlittingAssignment = {
      assignmentId: `ASN-${childId}`,
      childNodeId: childId,
      parentNodeId: region.nodeId,
      posXMm: pos.x,
      posYMm: pos.y,
      rotated,
      pinned: false,
    };
    const nextNodes = [...nodes, childNode];
    const nextAssignments = [...assignments, assignment];
    const pools = recomputePools(nextNodes, nextAssignments, get().allMasters, get().allOrders);
    const master = findMasterForNode(nextNodes, region.nodeId);
    set({
      nodes: nextNodes,
      assignments: nextAssignments,
      canvasMasterId: master?.nodeId ?? get().canvasMasterId,
      selectedNodeId: childId,
      ...pools,
    });
    return { ok: true };
  },

  resizeRegion: (regionNodeId, lengthMm, widthMm) => {
    const { nodes, assignments } = get();
    const region = nodes.find((n) => n.nodeId === regionNodeId && n.nodeType === 'INTERMEDIATE');
    if (!region) return { ok: false, message: '请选择有效区域' };
    const parentAssign = assignments.find((a) => a.childNodeId === regionNodeId);
    if (!parentAssign) return { ok: false, message: '未找到区域在父节点上的位置' };
    const parent = nodes.find((n) => n.nodeId === parentAssign.parentNodeId);
    if (!parent) return { ok: false, message: '父节点不存在' };
    const { maxLengthMm, maxWidthMm } = maxRegionSizeInParent(
      parent,
      parentAssign.posXMm,
      parentAssign.posYMm,
    );
    if (lengthMm <= 0 || widthMm <= 0) {
      return { ok: false, message: '尺寸须大于 0' };
    }
    if (lengthMm > maxLengthMm + 0.01 || widthMm > maxWidthMm + 0.01) {
      return {
        ok: false,
        message: `超出父节点可用空间（最大 ${maxLengthMm.toFixed(0)}×${maxWidthMm.toFixed(0)} mm）`,
      };
    }
    if (!regionChildrenStillFit(region, lengthMm, widthMm, nodes, assignments)) {
      return { ok: false, message: '缩小后无法容纳区域内已有子订单' };
    }
    const nextNodes = nodes.map((n) =>
      n.nodeId === regionNodeId ? { ...n, lengthMm, widthMm } : n,
    );
    set({ nodes: nextNodes });
    return { ok: true };
  },

  deleteNode: (nodeId) => {
    const { nodes, assignments } = get();
    const removeIds = collectDescendants(nodes, nodeId);
    const nextNodes = nodes.filter((n) => !removeIds.has(n.nodeId));
    const nextAssignments = assignments.filter(
      (a) => !removeIds.has(a.childNodeId) && !removeIds.has(a.parentNodeId),
    );
    const pools = recomputePools(nextNodes, nextAssignments, get().allMasters, get().allOrders);
    const master = nextNodes.find((n) => n.nodeType === 'MASTER');
    set({
      nodes: nextNodes,
      assignments: nextAssignments,
      canvasMasterId: master?.nodeId ?? null,
      selectedNodeId: null,
      ...pools,
    });
  },

  selectNode: (nodeId) => {
    if (!nodeId) {
      set({ selectedNodeId: null });
      return;
    }
    const master = findMasterForNode(get().nodes, nodeId);
    set({ selectedNodeId: nodeId, canvasMasterId: master?.nodeId ?? get().canvasMasterId });
  },

  toggleNodeLock: (nodeId) => {
    const { nodes, assignments } = get();
    const node = nodes.find((n) => n.nodeId === nodeId);
    if (!node) return;
    let targetIds: string[];
    if (node.nodeType === 'MASTER') {
      const subtree = collectDescendantNodeIds(nodes, nodeId);
      targetIds = assignments.filter((a) => subtree.has(a.childNodeId)).map((a) => a.assignmentId);
    } else {
      const assignment = assignments.find((a) => a.childNodeId === nodeId);
      if (!assignment) return;
      targetIds = [assignment.assignmentId];
    }
    if (targetIds.length === 0) return;
    const allPinned = targetIds.every((id) => assignments.find((a) => a.assignmentId === id)?.pinned);
    const nextPinned = !allPinned;
    set({
      assignments: assignments.map((a) =>
        targetIds.includes(a.assignmentId) ? { ...a, pinned: nextPinned } : a,
      ),
    });
  },

  getSnapshot: () => {
    const s = get();
    return { nodes: s.nodes, assignments: s.assignments };
  },
}));
