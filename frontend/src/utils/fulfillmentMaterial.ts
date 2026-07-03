import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';

export const FULFILLMENT_GANTT_NODE_TYPES = new Set(['SALES_ORDER', 'WORK_ORDER', 'SUPPLY_ORDER']);

export function isMaterialNodeType(nodeType: string): boolean {
  return nodeType === 'INVENTORY' || nodeType === 'SHORTAGE';
}

export function isGanttChainNodeType(nodeType: string): boolean {
  return FULFILLMENT_GANTT_NODE_TYPES.has(nodeType);
}

/** 甘特 / 满足链左侧：仅销售订单与工单（不含库存、缺料节点） */
export function filterGanttChainNodes(nodes: FulfillmentChainNode[]): FulfillmentChainNode[] {
  return nodes.filter((n) => isGanttChainNodeType(n.nodeType));
}

/** 将甘特 taskId 解析为需求方节点（销售订单或工单） */
export function resolveDemanderNodeId(
  taskId: string,
  nodes: FulfillmentChainNode[],
): string | null {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const direct = nodeById.get(taskId);
  if (direct && isGanttChainNodeType(direct.nodeType)) {
    return direct.nodeId;
  }
  for (const n of nodes) {
    if (
      (n.nodeType === 'WORK_ORDER' || n.nodeType === 'SUPPLY_ORDER') &&
      taskId.startsWith(`${n.nodeId}-`)
    ) {
      return n.nodeId;
    }
  }
  return null;
}

/** 选中节点直接上游物料（库存 + 缺料），不展开子工单 */
export function collectMaterialNodesForDemander(
  demanderId: string | null | undefined,
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): FulfillmentChainNode[] {
  if (!demanderId) {
    return [];
  }
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const result: FulfillmentChainNode[] = [];

  for (const e of edges) {
    if (e.toNodeId !== demanderId) {
      continue;
    }
    const supplier = nodeById.get(e.fromNodeId);
    if (supplier && isMaterialNodeType(supplier.nodeType)) {
      result.push(supplier);
    }
  }

  return result.sort((a, b) => {
    const typeOrder = (t: string) => (t === 'INVENTORY' ? 0 : t === 'SHORTAGE' ? 1 : 2);
    const ta = typeOrder(a.nodeType);
    const tb = typeOrder(b.nodeType);
    if (ta !== tb) {
      return ta - tb;
    }
    return a.productCode.localeCompare(b.productCode, 'zh-CN');
  });
}

export function materialPegLabel(node: FulfillmentChainNode, edges: FulfillmentPegEdge[]): string {
  const edge = edges.find((e) => e.fromNodeId === node.nodeId);
  if (edge?.pegLabel) {
    return edge.pegLabel;
  }
  if (node.nodeType === 'INVENTORY') {
    return '库存满足';
  }
  if (node.nodeType === 'SHORTAGE') {
    return '缺料';
  }
  return '—';
}
