import type { Task } from 'gantt-task-react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import { isMaterialNodeType } from './fulfillmentMaterial';
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

  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const depths = new Map<string, number>();
  for (const so of nodes.filter((n) => n.nodeType === 'SALES_ORDER')) {
    depths.set(so.nodeId, 0);
    const queue = [{ id: so.nodeId, depth: 0 }];
    while (queue.length > 0) {
      const { id, depth } = queue.shift()!;
      for (const sid of suppliersByDemander.get(id) ?? []) {
        const supplier = nodeById.get(sid);
        if (supplier && isMaterialNodeType(supplier.nodeType)) {
          continue;
        }
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

function resolveWorkOrderRootId(nodes: FulfillmentChainNode[], workOrderNo: string): string {
  for (const id of [`supo-${workOrderNo}`, `wo-${workOrderNo}`]) {
    if (nodes.some((n) => n.nodeId === id)) {
      return id;
    }
  }
  const byAttr = nodes.find(
    (n) =>
      n.attributes?.workOrderNo === workOrderNo || n.attributes?.supplyOrderId === workOrderNo,
  );
  return byAttr?.nodeId ?? `supo-${workOrderNo}`;
}

/** 上游满足链：以工单为根，子级为供应方（子件工单 / 库存 / 缺料） */
export function buildUpstreamChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  rootWorkOrderNo: string,
): FulfillmentTreeNode[] {
  return buildWorkOrderDirectedTree(
    nodes,
    edges,
    resolveWorkOrderRootId(nodes, rootWorkOrderNo),
    'upstream',
  );
}

/** 下游满足链：以工单为根，子级为消费方（父工单 / 销售订单） */
export function buildDownstreamChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
  rootWorkOrderNo: string,
): FulfillmentTreeNode[] {
  return buildWorkOrderDirectedTree(
    nodes,
    edges,
    resolveWorkOrderRootId(nodes, rootWorkOrderNo),
    'downstream',
  );
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
  if (nodeType === 'SALES_ORDER') return '客户交付';
  if (nodeType === 'SUPPLY_ORDER') return '供应订单';
  if (nodeType === 'WORK_ORDER') return '工单';
  if (nodeType === 'INVENTORY') return '库存';
  if (nodeType === 'SHORTAGE') return '缺料';
  return nodeType;
}

/** 左侧供应订单树：仅销售订单 / 供应订单 / 工单（不含库存、缺料） */
export function isSupplyOrderTreeNodeType(nodeType: string): boolean {
  return nodeType === 'SALES_ORDER' || nodeType === 'SUPPLY_ORDER' || nodeType === 'WORK_ORDER';
}

/** 满足链供应订单树（过滤物料叶子，物料在右侧面板展示） */
export function buildSupplyOrderChainTree(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): FulfillmentTreeNode[] {
  const full = buildFulfillmentChainTree(nodes, edges);
  return filterTreeToSupplyOrders(full);
}

function filterTreeToSupplyOrders(tree: FulfillmentTreeNode[]): FulfillmentTreeNode[] {
  const out: FulfillmentTreeNode[] = [];
  for (const node of tree) {
    if (!isSupplyOrderTreeNodeType(node.nodeType)) {
      continue;
    }
    out.push({
      ...node,
      children: filterTreeToSupplyOrders(node.children),
    });
  }
  return out;
}

export interface SupplyOrderTreeRow {
  nodeId: string;
  nodeType: string;
  label: string;
  productCode: string;
  quantity: number;
  startTs: string;
  endTs: string;
  depth: number;
  hasChildren: boolean;
}

function formatDayTime(ts: string): string {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '—';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
}

/** 按展开状态扁平化供应订单树（行序与甘特对齐） */
export function flattenSupplyOrderTreeRows(
  tree: FulfillmentTreeNode[],
  nodes: FulfillmentChainNode[],
  collapsed: Set<string>,
): SupplyOrderTreeRow[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const rows: SupplyOrderTreeRow[] = [];

  function walk(items: FulfillmentTreeNode[], depth: number) {
    for (const item of items) {
      const meta = nodeById.get(item.nodeId);
      rows.push({
        nodeId: item.nodeId,
        nodeType: item.nodeType,
        label: item.label,
        productCode: meta?.productCode ?? '',
        quantity: item.quantity,
        startTs: meta?.startTs ?? '',
        endTs: meta?.endTs ?? '',
        depth,
        hasChildren: item.children.length > 0,
      });
      if (item.children.length > 0 && !collapsed.has(item.nodeId)) {
        walk(item.children, depth + 1);
      }
    }
  }

  walk(tree, 0);
  return rows;
}

export function formatSupplyOrderTreeDate(ts: string): string {
  return formatDayTime(ts);
}

export function defaultCollapsedSupplyOrderIds(tree: FulfillmentTreeNode[]): Set<string> {
  const collapsed = new Set<string>();
  function walk(nodes: FulfillmentTreeNode[], depth: number) {
    for (const n of nodes) {
      if (depth >= 1 && n.children.length > 0) {
        collapsed.add(n.nodeId);
      }
      walk(n.children, depth + 1);
    }
  }
  walk(tree, 0);
  return collapsed;
}
