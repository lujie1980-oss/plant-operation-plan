import type { DetailScheduleOperation } from '../types/api';

/** 工艺序号：优先 API 字段，否则从 operationId 解析。 */
export function routingSeq(op: DetailScheduleOperation): number {
  if (op.operationSeq != null && op.operationSeq > 0) {
    return op.operationSeq;
  }
  return parseOperationRoutingSeq(op.operationId);
}

/** 从 operationId 解析工艺路线工序序号（与后端 DetailScheduleAssignmentBuilder 一致）。 */
export function parseOperationRoutingSeq(operationId: string | null | undefined): number {
  if (!operationId) return -1;
  const underscore = operationId.lastIndexOf('_');
  const dash = operationId.lastIndexOf('-', underscore > 0 ? underscore : operationId.length);
  if (dash < 0 || underscore <= dash) return -1;
  const seq = Number.parseInt(operationId.slice(dash + 1, underscore), 10);
  return Number.isFinite(seq) ? seq : -1;
}

function batchKey(batchNo: string | null | undefined): string {
  return batchNo?.trim() ?? '';
}

function sameBatch(a: DetailScheduleOperation, b: DetailScheduleOperation): boolean {
  return a.workOrderNo === b.workOrderNo && batchKey(a.batchNo) === batchKey(b.batchNo);
}

/** 同工单/批次内，按工艺序号递增的下游工序（不含选中工序）。 */
export function buildDownstreamChain(
  operations: DetailScheduleOperation[],
  selected: DetailScheduleOperation,
): DetailScheduleOperation[] {
  const selectedSeq = routingSeq(selected);
  if (selectedSeq < 0) return [];

  return operations
    .filter((op) => {
      if (!sameBatch(op, selected)) return false;
      const seq = routingSeq(op);
      return seq > selectedSeq;
    })
    .sort((a, b) => routingSeq(a) - routingSeq(b));
}

/** 同工单/批次内，按工艺序号递减的上游工序（不含选中工序）。 */
export function buildUpstreamChain(
  operations: DetailScheduleOperation[],
  selected: DetailScheduleOperation,
): DetailScheduleOperation[] {
  const selectedSeq = routingSeq(selected);
  if (selectedSeq < 0) return [];

  return operations
    .filter((op) => {
      if (!sameBatch(op, selected)) return false;
      const seq = routingSeq(op);
      return seq > 0 && seq < selectedSeq;
    })
    .sort((a, b) => routingSeq(a) - routingSeq(b));
}

/** 工艺顺序链：上游 → 选中 → 下游（仅含已排产条目）。 */
export function buildRoutingProcessChain(
  operations: DetailScheduleOperation[],
  selected: DetailScheduleOperation,
): DetailScheduleOperation[] {
  return [
    ...buildUpstreamChain(operations, selected),
    selected,
    ...buildDownstreamChain(operations, selected),
  ];
}

/** 链上相邻工序若开工时间倒挂，返回违反对（前序应早于后序）。 */
export function findRoutingTimeViolations(
  chain: DetailScheduleOperation[],
): { earlier: DetailScheduleOperation; later: DetailScheduleOperation }[] {
  const violations: { earlier: DetailScheduleOperation; later: DetailScheduleOperation }[] = [];
  for (let i = 0; i < chain.length - 1; i++) {
    const earlier = chain[i];
    const later = chain[i + 1];
    if (earlier.startMinute > later.startMinute) {
      violations.push({ earlier, later });
    }
  }
  return violations;
}
