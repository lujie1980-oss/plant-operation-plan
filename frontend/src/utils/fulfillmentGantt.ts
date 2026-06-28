import type { Task } from 'gantt-task-react';
import type {
  FulfillmentChainNode,
  FulfillmentOperation,
  FulfillmentPegEdge,
} from '../types/api';
import type { SupplyOrderTreeRow } from './fulfillmentChainTree';
import { isGanttChainNodeType, isMaterialNodeType } from './fulfillmentMaterial';

const STATUS_COLORS: Record<string, string> = {
  OK: '#10b981',
  PLANNED: '#3b82f6',
  DEMAND: '#6366f1',
  PENDING: '#94a3b8',
  SHORTAGE: '#ef4444',
  ON_TRACK: '#0ea5e9',
  AT_RISK: '#f59e0b',
};

const SUPPLIER_TYPE_ORDER: Record<string, number> = {
  SUPPLY_ORDER: 0,
  WORK_ORDER: 0,
  INVENTORY: 1,
  SHORTAGE: 2,
};

function parseTs(ts: string): Date {
  return new Date(ts);
}

function formatPlanDay(ts: string): string {
  const d = parseTs(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
}

function ganttDisplayName(node: FulfillmentChainNode, level: number): string {
  const qty =
    node.quantity != null && node.quantity > 0
      ? ` ×${Number(node.quantity)}`
      : '';
  const range = `${formatPlanDay(node.startTs)}→${formatPlanDay(node.endTs)}`;
  const prefix = level > 0 ? `${'　'.repeat(level)}└ ` : '';
  return `${prefix}${node.label}${qty} [${range}]`;
}

function pegLabel(type: string): string {
  if (type === 'INVENTORY_PEG') return '库存满足';
  if (type === 'WORK_ORDER_PEG') return '供应订单满足';
  return '缺料';
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
  return parseTs(a.startTs).getTime() - parseTs(b.startTs).getTime();
}

/** 按满足边自上而下：需求方 → 其供应方（DFS） */
function buildSuppliersByDemander(
  edges: FulfillmentPegEdge[],
): Map<string, string[]> {
  const map = new Map<string, string[]>();
  for (const e of edges) {
    const list = map.get(e.toNodeId) ?? [];
    list.push(e.fromNodeId);
    map.set(e.toNodeId, list);
  }
  return map;
}

/** 供应订单树行 → 甘特任务（一行一工单，不含工序子行） */
export function supplyOrderRowsToGanttTasks(rows: SupplyOrderTreeRow[]): Task[] {
  if (rows.length === 0) {
    const now = new Date();
    return [
      {
        id: 'empty',
        name: '暂无供应订单',
        start: now,
        end: new Date(now.getTime() + 3_600_000),
        type: 'task',
        progress: 0,
      },
    ];
  }

  const STATUS_COLORS: Record<string, string> = {
    OK: '#10b981',
    PLANNED: '#3b82f6',
    DEMAND: '#6366f1',
    PENDING: '#94a3b8',
    SHORTAGE: '#ef4444',
    ON_TRACK: '#0ea5e9',
    AT_RISK: '#f59e0b',
  };

  return rows.map((row, index) => {
    const start = parseTs(row.startTs);
    const end = parseTs(row.endTs);
    const color =
      row.nodeType === 'SALES_ORDER'
        ? STATUS_COLORS.DEMAND
        : STATUS_COLORS.PLANNED;
    return {
      id: row.nodeId,
      name: row.label,
      start,
      end: end.getTime() > start.getTime() ? end : new Date(start.getTime() + 3_600_000),
      type: 'task',
      progress: 100,
      displayOrder: index + 1,
      styles: {
        backgroundColor: color,
        progressColor: color,
      },
    };
  });
}

/** 满足追溯链 → 甘特（左侧行序 = 上下游满足层级；箭头层单独绘制） */
export function fulfillmentChainToGanttTasks(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): Task[] {
  if (nodes.length === 0) {
    const now = new Date();
    return [
      {
        id: 'empty',
        name: '暂无满足链',
        start: now,
        end: new Date(now.getTime() + 3_600_000),
        type: 'task',
        progress: 0,
      },
    ];
  }

  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  const suppliersByDemander = buildSuppliersByDemander(edges);
  const tasks: Task[] = [];
  const visited = new Set<string>();
  // gantt-task-react 用 displayOrder || MAX 排序，0 会被当成未设置而沉底
  let displayOrder = 0;
  const nextOrder = () => ++displayOrder;

  function emit(nodeId: string, level: number) {
    if (visited.has(nodeId)) return;
    const node = nodeById.get(nodeId);
    if (!node) return;
    if (isMaterialNodeType(node.nodeType)) {
      return;
    }
    visited.add(nodeId);

    const color = STATUS_COLORS[node.status] ?? '#64748b';
    const ops = node.operations ?? [];

    if (node.nodeType === 'WORK_ORDER' && ops.length > 0) {
      tasks.push({
        id: node.nodeId,
        name: ganttDisplayName(node, level),
        start: parseTs(node.startTs),
        end: parseTs(node.endTs),
        type: 'project',
        progress: 100,
        hideChildren: true,
        displayOrder: nextOrder(),
        styles: {
          backgroundColor: color,
          progressColor: color,
        },
      });
      for (const op of ops) {
        tasks.push({
          ...operationToTask(op, node.nodeId),
          displayOrder: nextOrder(),
        });
      }
    } else {
      tasks.push({
        id: node.nodeId,
        name: ganttDisplayName(node, level),
        start: parseTs(node.startTs),
        end: parseTs(node.endTs),
        type: 'task',
        progress: node.status === 'SHORTAGE' ? 30 : 100,
        displayOrder: nextOrder(),
        styles: {
          backgroundColor: color,
          progressColor: color,
        },
      });
    }

    const supplierIds = [...(suppliersByDemander.get(nodeId) ?? [])]
      .filter((sid) => {
        const supplier = nodeById.get(sid);
        return supplier != null && !isMaterialNodeType(supplier.nodeType);
      })
      .sort((a, b) => compareSupplierIds(a, b, nodeById));
    for (const sid of supplierIds) {
      emit(sid, level + 1);
    }
  }

  for (const so of nodes.filter((n) => n.nodeType === 'SALES_ORDER')) {
    emit(so.nodeId, 0);
  }
  for (const n of nodes) {
    if (!visited.has(n.nodeId)) {
      emit(n.nodeId, 0);
    }
  }

  return tasks;
}

function operationToTask(op: FulfillmentOperation, projectId: string): Task {
  const color = STATUS_COLORS.PLANNED;
  return {
    id: `${projectId}-${op.operationId}`,
    name: `　└ ${op.operationName} · ${op.resourceId}`,
    start: parseTs(op.startTs),
    end: parseTs(op.endTs),
    type: 'task',
    progress: 100,
    project: projectId,
    styles: {
      backgroundColor: color,
      progressColor: color,
    },
  };
}

/** 甘特满足链箭头：仅工单→工单（物料节点已分离至右侧面板） */
export function ganttPegEdges(
  edges: FulfillmentPegEdge[],
  nodes: FulfillmentChainNode[] = [],
): FulfillmentPegEdge[] {
  const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
  return edges.filter((e) => {
    if (e.pegType !== 'WORK_ORDER_PEG') {
      return false;
    }
    const from = nodeById.get(e.fromNodeId);
    const to = nodeById.get(e.toNodeId);
    return (
      from != null
      && to != null
      && isGanttChainNodeType(from.nodeType)
      && isGanttChainNodeType(to.nodeType)
    );
  });
}

export function formatPegEdges(
  nodes: FulfillmentChainNode[],
  edges: FulfillmentPegEdge[],
): { text: string; pegType: string }[] {
  const byId = new Map(nodes.map((n) => [n.nodeId, n]));
  return edges.map((e) => {
    const from = byId.get(e.fromNodeId);
    const to = byId.get(e.toNodeId);
    const fromLabel = from?.label ?? e.fromNodeId;
    const toLabel = to?.label ?? e.toNodeId;
    const label = e.pegLabel ?? pegLabel(e.pegType);
    return {
      pegType: e.pegType,
      text: `${fromLabel} → ${toLabel}（${label}）`,
    };
  });
}

export const FULFILLMENT_STATUS_LABEL: Record<string, string> = {
  ON_TRACK: '可满足',
  PLANNED: '工单满足',
  PENDING: '待满足',
  AT_RISK: '有风险',
  OK: '正常',
  SHORTAGE: '缺料',
  DEMAND: '需求',
  KITTING_OK: '齐套',
};
