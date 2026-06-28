import type { FulfillmentChainNode, FulfillmentPegEdge, OrderFulfillmentChain } from '../types/api';

export interface PlanningSignal {
  severity: string;
  reasonCode: string;
  message: string;
  entityId: string | null;
}

export function chainTrialRevision(chain: OrderFulfillmentChain | null): number {
  if (!chain?.nodes?.length) return 0;
  for (const node of chain.nodes) {
    const rev = node.attributes?.trialRevision;
    if (typeof rev === 'number' && rev > 0) return rev;
  }
  return 0;
}

export function chainSolverEngine(chain: OrderFulfillmentChain | null): string | null {
  if (!chain?.nodes?.length) return null;
  for (const node of chain.nodes) {
    const engine = node.attributes?.solverEngine;
    if (typeof engine === 'string' && engine.length > 0) return engine;
  }
  return null;
}

export function nodePlanningSignals(node: FulfillmentChainNode): PlanningSignal[] {
  const raw = node.attributes?.planningSignals;
  if (!Array.isArray(raw)) return [];
  return raw.filter(
    (item): item is PlanningSignal =>
      item != null && typeof item === 'object' && 'severity' in item,
  );
}

export function suggestPromiseDate(chain: OrderFulfillmentChain | null): string | null {
  if (!chain?.nodes?.length) return null;
  const dates = chain.nodes
    .filter((n) =>
      n.nodeType === 'SALES_ORDER' ||
      n.nodeType === 'WORK_ORDER' ||
      n.nodeType === 'SUPPLY_ORDER',
    )
    .map((n) => (n.endTs ? n.endTs.slice(0, 10) : null))
    .filter((d): d is string => Boolean(d));
  if (dates.length === 0) return null;
  return dates.sort().at(-1) ?? null;
}

export function chainEdgesForGantt(edges: FulfillmentPegEdge[]): FulfillmentPegEdge[] {
  return edges;
}
