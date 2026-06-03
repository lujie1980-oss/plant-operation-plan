import type { Task } from 'gantt-task-react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import type { OrderPlanningChainNode } from '../types/orderPlanningChain';

const STATUS_COLORS: Record<string, string> = {
  OK: '#10b981',
  WARN: '#f59e0b',
  AT_RISK: '#f59e0b',
  BLOCKED: '#ef4444',
  SKIP: '#ef4444',
  SKIPPED: '#ef4444',
  SHORTAGE: '#ef4444',
  PLANNED: '#3b82f6',
};

function dateAtHour(isoDate: string, hour: number): Date {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d, hour, 0, 0);
}

export function orderPlanningChainToDisplayNodes(
  nodes: OrderPlanningChainNode[],
): FulfillmentChainNode[] {
  return nodes.map((n) => ({
    nodeId: n.nodeId,
    nodeType: n.nodeType,
    laneId: n.laneId,
    label: n.label,
    status: n.status,
    depth: n.depth,
    productCode: n.productCode,
    quantity: n.quantity,
    startTs: n.windowStart ? `${n.windowStart}T08:00:00` : '',
    endTs: n.windowEnd ? `${n.windowEnd}T17:00:00` : '',
    attributes: {
      ...n.attributes,
      planningLayer: n.planningLayer,
      planningSignals: n.planningSignals,
    },
    operations: n.operations,
  }));
}

export function orderPlanningChainToGanttTasks(nodes: OrderPlanningChainNode[]): Task[] {
  const display = orderPlanningChainToDisplayNodes(nodes).filter(
    (n) => n.startTs && n.endTs,
  );
  if (display.length === 0) {
    const now = new Date();
    return [
      {
        id: 'empty',
        name: '暂无推演时间窗',
        start: now,
        end: new Date(now.getTime() + 3_600_000),
        type: 'task',
        progress: 0,
      },
    ];
  }
  return display.map((n) => {
    const src = nodes.find((x) => x.nodeId === n.nodeId);
    const status = src?.status ?? n.status;
    const startDate = src?.windowStart ?? (n.startTs ? n.startTs.slice(0, 10) : null);
    const endDate = src?.windowEnd ?? (n.endTs ? n.endTs.slice(0, 10) : null);
    if (!startDate || !endDate) {
      const now = new Date();
      return {
        id: n.nodeId,
        name: n.label,
        type: 'task' as const,
        start: now,
        end: new Date(now.getTime() + 3_600_000),
        progress: 0,
      };
    }
    return {
      id: n.nodeId,
      name: n.label,
      type: 'task' as const,
      start: dateAtHour(startDate, 8),
      end: dateAtHour(endDate, 17),
      progress: status === 'OK' ? 100 : status === 'WARN' || status === 'AT_RISK' ? 50 : 0,
      styles: {
        backgroundColor: STATUS_COLORS[status] ?? '#94a3b8',
        progressColor: STATUS_COLORS[status] ?? '#94a3b8',
      },
    };
  });
}

export function chainEdgesForGantt(
  edges: FulfillmentPegEdge[],
): FulfillmentPegEdge[] {
  return edges;
}
