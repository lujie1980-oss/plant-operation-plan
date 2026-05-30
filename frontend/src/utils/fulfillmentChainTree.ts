import type { Task } from 'gantt-task-react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import { ganttVisibleTasks } from './ganttLayout';

const SUPPLIER_TYPE_ORDER: Record<string, number> = {
  WORK_ORDER: 0,
  INVENTORY: 1,
  SHORTAGE: 2,
};

export interface FulfillmentTreeNode {
  nodeId: string;
  nodeType: string;
  pegType?: string;
  label: string;
  displayDate: string;
  quantity: number;
  children: FulfillmentTreeNode[];
}

/** 与甘特可见行一一对应的左侧行 */
export interface FulfillmentSyncRow {
  taskId: string;
  nodeType: string;
  label: string;
  displayDate: string;
  quantity: number;
  depth: number;
}

function formatDay(ts: string): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function computeDepthFromSo(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): Map<string, number> {
  const suppliersByDemander = new Map<string, string[]>();
  for (const e of edges) {
    const list = suppliersByDemander.get(e.toNodeId) ?? [];
    list.push(e.fromNodeId);
    suppliersByDemander.set(e.toNodeId, list);
  }

  const depths = new Map<string, number>();
  for (const so of nodes.filter((n) => n.nodeType === 'SALES_ORDER')) {
    depths.set(so.nodeId, 0);
    const queue = [{ id: so.nodeId, depth: 0 }];
    while (queue.length > 0) {
      const { id, depth } = queue.shift()!;
      for (const sid of suppliersByDemander.get(id) ?? []) {
        if (!depths.has(sid)) {
          depths.set(sid, depth + 1);
          queue.push({ id: sid, depth: depth + 1 });
        }
      }
    }
  }
  return depths;
}

/** 左侧行序与 ganttVisibleTasks 完全一致，保证行高对齐 */
export function buildFulfillmentSyncRows(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  tasks: Task[],
): FulfillmentSyncRow[] {
  const visible = ganttVisibleTasks(tasks);
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const depthMap = computeDepthFromSo(nodes, edges);

  return visible.map((task) => {
    const node = nodeById.get(task.id);
    const depth = depthMap.get(task.id) ?? 0;
    return {
      taskId: task.id,
      nodeType: node?.nodeType ?? 'task',
      label: node?.label ?? task.name,
      displayDate: node ? formatDay(node.endTs) : '',
      quantity: node?.quantity ?? 0,
      depth,
    };
  });
}

function compareSupplierIds(
  aId: string,
  bId: string,
  nodeById: Map<string, FulfillmentChainNode>,
): number {
  const a = nodeById.get(aId);
  const b = nodeById.get(bId);
  if (!a || !b) return 0;
  const ta = SUPPLIER_TYPE_ORDER[a.nodeType] ?? 9;
  const tb = SUPPLIER_TYPE_ORDER[b.nodeType] ?? 9;
  if (ta !== tb) return ta - tb;
  return a.label.localeCompare(b.label);
}

/** 满足链树：销售订单为根，供应方（工单/库存/缺料）为子级 */
export function buildFulfillmentChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): FulfillmentTreeNode[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const suppliersByDemander = new Map<string, string[]>();
  const pegTypeByEdge = new Map<string, string>();

  for (const e of edges) {
    const list = suppliersByDemander.get(e.toNodeId) ?? [];
    list.push(e.fromNodeId);
    suppliersByDemander.set(e.toNodeId, list);
    pegTypeByEdge.set(`${e.fromNodeId}->${e.toNodeId}`, e.pegType);
  }

  const visited = new Set<string>();

  function buildNode(nodeId: string): FulfillmentTreeNode | null {
    if (visited.has(nodeId)) return null;
    visited.add(nodeId);
    const node = nodeById.get(nodeId);
    if (!node) return null;

    const supplierIds = [...(suppliersByDemander.get(nodeId) ?? [])].sort((a, b) =>
      compareSupplierIds(a, b, nodeById),
    );

    const children: FulfillmentTreeNode[] = [];
    for (const sid of supplierIds) {
      const child = buildNode(sid);
      if (child) {
        child.pegType = pegTypeByEdge.get(`${sid}->${nodeId}`);
        children.push(child);
      }
    }

    return {
      nodeId: node.nodeId,
      nodeType: node.nodeType,
      label: node.label,
      displayDate: formatDay(node.endTs),
      quantity: node.quantity ?? 0,
      children,
    };
  }

  const roots: FulfillmentTreeNode[] = [];
  for (const n of nodes) {
    if (n.nodeType === 'SALES_ORDER') {
      const root = buildNode(n.nodeId);
      if (root) roots.push(root);
    }
  }

  for (const n of nodes) {
    if (!visited.has(n.nodeId)) {
      const orphan = buildNode(n.nodeId);
      if (orphan) roots.push(orphan);
    }
  }

  return roots;
}

/** 上游满足链：以工单为根，子级为供应方（子件工单 / 库存 / 缺料） */
export function buildUpstreamChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  rootWorkOrderNo: string,
): FulfillmentTreeNode[] {
  return buildWorkOrderDirectedTree(nodes, edges, `wo-${rootWorkOrderNo}`, 'upstream');
}

/** 下游满足链：以工单为根，子级为消费方（父工单 / 销售订单） */
export function buildDownstreamChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  rootWorkOrderNo: string,
): FulfillmentTreeNode[] {
  return buildWorkOrderDirectedTree(nodes, edges, `wo-${rootWorkOrderNo}`, 'downstream');
}

function buildWorkOrderDirectedTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  rootNodeId: string,
  direction: 'upstream' | 'downstream',
): FulfillmentTreeNode[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const childIdsByNode = new Map<string, string[]>();
  const pegTypeByEdge = new Map<string, string>();

  for (const e of edges) {
    const parentId = direction === 'upstream' ? e.toNodeId : e.fromNodeId;
    const childId = direction === 'upstream' ? e.fromNodeId : e.toNodeId;
    const list = childIdsByNode.get(parentId) ?? [];
    list.push(childId);
    childIdsByNode.set(parentId, list);
    pegTypeByEdge.set(`${childId}|${parentId}`, e.pegType);
  }

  const visited = new Set<string>();

  function buildNode(nodeId: string): FulfillmentTreeNode | null {
    if (visited.has(nodeId)) return null;
    visited.add(nodeId);
    const node = nodeById.get(nodeId);
    if (!node) return null;

    const childIds = [...(childIdsByNode.get(nodeId) ?? [])].sort((a, b) =>
      compareSupplierIds(a, b, nodeById),
    );
    const children: FulfillmentTreeNode[] = [];
    for (const cid of childIds) {
      const child = buildNode(cid);
      if (child) {
        child.pegType = pegTypeByEdge.get(`${cid}|${nodeId}`);
        children.push(child);
      }
    }

    return {
      nodeId: node.nodeId,
      nodeType: node.nodeType,
      label: node.label,
      displayDate: formatDay(node.endTs),
      quantity: node.quantity ?? 0,
      children,
    };
  }

  const root = buildNode(rootNodeId);
  return root ? [root] : [];
}

export function fulfillmentTreeNodeTypeLabel(nodeType: string): string {
  if (nodeType === 'SALES_ORDER') return '销售订单';
  if (nodeType === 'WORK_ORDER') return '工单';
  if (nodeType === 'INVENTORY') return '库存';
  if (nodeType === 'SHORTAGE') return '缺料';
  return nodeType;
}
