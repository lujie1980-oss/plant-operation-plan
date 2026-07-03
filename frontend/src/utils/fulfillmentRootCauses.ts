import type { OrderFulfillmentChain } from '../types/api';
import { isMaterialNodeType } from './fulfillmentMaterial';
import { nodePlanningSignals } from './fulfillmentChainMeta';
import { pispIdFromProductCode } from './masterPlanDeepLink';

export interface FulfillmentRootCause {
  id: string;
  kind: 'capacity' | 'material';
  label: string;
  resourceId?: string;
  productCode?: string;
  pispId?: string;
  utilizationPct?: number;
}

function addCapacityCause(
  map: Map<string, FulfillmentRootCause>,
  resourceId: string,
  utilizationPct?: number,
) {
  if (!resourceId || resourceId === 'UNASSIGNED') return;
  const existing = map.get(`cap:${resourceId}`);
  const pct = utilizationPct ?? existing?.utilizationPct ?? 0;
  if (!existing || pct > (existing.utilizationPct ?? 0)) {
    map.set(`cap:${resourceId}`, {
      id: `cap:${resourceId}`,
      kind: 'capacity',
      label: resourceId,
      resourceId,
      utilizationPct: pct,
    });
  }
}

/** SCN-02b/02c: infer navigable capacity / material root causes from a fulfillment chain. */
export function extractFulfillmentRootCauses(chain: OrderFulfillmentChain | null): FulfillmentRootCause[] {
  if (!chain) return [];

  const map = new Map<string, FulfillmentRootCause>();

  for (const bucket of chain.utilizationBuckets ?? []) {
    if (bucket.utilizationPct >= 70) {
      addCapacityCause(map, bucket.resourceId, bucket.utilizationPct);
    }
  }

  for (const node of chain.nodes) {
    for (const signal of nodePlanningSignals(node)) {
      if (
        signal.reasonCode === 'ALLOC_NO_RESOURCE_SLOTS' ||
        signal.reasonCode === 'ALLOC_TIMING_FALLBACK'
      ) {
        const resourceId =
          signal.entityId ??
          node.operations.find((op) => op.resourceId && op.resourceId !== 'UNASSIGNED')?.resourceId;
        if (resourceId) {
          addCapacityCause(map, resourceId);
        }
      }
    }

    for (const op of node.operations) {
      if (op.resourceId && op.resourceId !== 'UNASSIGNED') {
        if (node.status === 'AT_RISK' || node.status === 'SHORTAGE' || node.status === 'BLOCKED') {
          addCapacityCause(map, op.resourceId);
        }
      }
    }

    if (isMaterialNodeType(node.nodeType) && (node.status === 'SHORTAGE' || node.status === 'AT_RISK')) {
      const code = node.productCode?.trim();
      if (!code) continue;
      const pispId = pispIdFromProductCode(code);
      map.set(`mat:${code}`, {
        id: `mat:${code}`,
        kind: 'material',
        label: code,
        productCode: code,
        pispId,
      });
    }
  }

  return [...map.values()].sort((a, b) => {
    if (a.kind !== b.kind) return a.kind === 'capacity' ? -1 : 1;
    if (a.kind === 'capacity') {
      return (b.utilizationPct ?? 0) - (a.utilizationPct ?? 0);
    }
    return a.label.localeCompare(b.label, 'zh-CN');
  });
}
