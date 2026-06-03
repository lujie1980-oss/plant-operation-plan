import type { DetailScheduleOperation } from '../types/api';
import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';

export function previewOperationsToGantt(
  operations: DetailSchedulePlanningPreviewOperation[] | undefined,
): DetailScheduleOperation[] {
  if (!operations) {
    return [];
  }
  return operations
    .filter(
      (op) =>
        op.scheduled && op.startMinute != null && op.endMinute != null && op.lineId,
    )
    .map((op) => ({
      operationId: op.operationId,
      workOrderNo: op.workOrderNo,
      lineId: op.lineId!,
      resourceId: op.resourceId,
      sequenceIndex: op.sequenceOnLine ?? 0,
      startMinute: op.startMinute!,
      endMinute: op.endMinute!,
      productCode: op.productCode,
      pinned: op.pinned,
      batchNo: op.batchNo,
      operationSeq: op.operationSeq,
      operationName: op.operationName,
      changeoverMinutesBefore: op.changeoverMinutesBefore ?? null,
    }));
}
