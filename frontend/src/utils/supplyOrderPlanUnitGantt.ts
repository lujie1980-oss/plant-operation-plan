import type { FulfillmentChainNode, FulfillmentOperation } from '../types/api';

export interface PlanUnitGanttRow {
  rowId: string;
  rowType: 'plan_unit' | 'operation';
  label: string;
  planUnitId: string;
  planUnitSequenceNr: number;
  operation?: FulfillmentOperation;
  startTs: string;
  endTs: string;
}

export interface PlanUnitGroup {
  planUnitId: string;
  sequenceNr: number;
  quantity: number;
  startTs: string;
  endTs: string;
  operations: FulfillmentOperation[];
}

function parseTs(ts: string): number {
  return new Date(ts).getTime();
}

function minTs(a: string, b: string): string {
  return parseTs(a) <= parseTs(b) ? a : b;
}

function maxTs(a: string, b: string): string {
  return parseTs(a) >= parseTs(b) ? a : b;
}

/** 按 PlanUnit 分组工序，并计算 PlanUnit 计划起止 */
export function groupOperationsByPlanUnit(
  node: FulfillmentChainNode,
): PlanUnitGroup[] {
  const ops = [...(node.operations ?? [])].sort((a, b) => a.sequenceNo - b.sequenceNo);
  if (ops.length === 0) return [];

  const byPu = new Map<string, FulfillmentOperation[]>();
  for (const op of ops) {
    const puId = op.planUnitId ?? `${node.nodeId}-pu-0`;
    const list = byPu.get(puId) ?? [];
    list.push(op);
    byPu.set(puId, list);
  }

  const groups: PlanUnitGroup[] = [];
  for (const [planUnitId, puOps] of byPu) {
    const sorted = [...puOps].sort((a, b) => a.sequenceNo - b.sequenceNo);
    let startTs = sorted[0].startTs;
    let endTs = sorted[0].endTs;
    for (const op of sorted) {
      if (op.startTs) startTs = minTs(startTs, op.startTs);
      if (op.endTs) endTs = maxTs(endTs, op.endTs);
    }
    groups.push({
      planUnitId,
      sequenceNr: sorted[0].planUnitSequenceNr ?? 0,
      quantity: node.quantity,
      startTs,
      endTs,
      operations: sorted,
    });
  }

  return groups.sort((a, b) => a.sequenceNr - b.sequenceNr);
}

/** PlanUnit 行 + 工序行（末道工序在最上） */
export function buildPlanUnitGanttRows(node: FulfillmentChainNode | null): PlanUnitGanttRow[] {
  if (!node || (node.operations ?? []).length === 0) return [];

  const rows: PlanUnitGanttRow[] = [];
  for (const pu of groupOperationsByPlanUnit(node)) {
    rows.push({
      rowId: pu.planUnitId,
      rowType: 'plan_unit',
      label: `PlanUnit #${pu.sequenceNr + 1}`,
      planUnitId: pu.planUnitId,
      planUnitSequenceNr: pu.sequenceNr,
      startTs: pu.startTs,
      endTs: pu.endTs,
    });

    const opsDesc = [...pu.operations].sort((a, b) => b.sequenceNo - a.sequenceNo);
    for (const op of opsDesc) {
      rows.push({
        rowId: `${pu.planUnitId}:${op.operationId}`,
        rowType: 'operation',
        label: `${op.sequenceNo} · ${op.operationName}`,
        planUnitId: pu.planUnitId,
        planUnitSequenceNr: pu.sequenceNr,
        operation: op,
        startTs: op.startTs,
        endTs: op.endTs,
      });
    }
  }
  return rows;
}

export function resolveSupplyOrderNodeForGantt(
  nodeId: string | null,
  nodes: FulfillmentChainNode[],
): FulfillmentChainNode | null {
  if (!nodeId) return null;
  const node = nodes.find((n) => n.nodeId === nodeId);
  if (!node) return null;
  if (node.nodeType === 'SUPPLY_ORDER' && (node.operations?.length ?? 0) > 0) {
    return node;
  }
  return null;
}
