import type { MasterPlanPlanningDiagnostics } from './planningDiagnostics';

export interface MasterPlanPlanningPreviewRequest {
  strategyId?: string | null;
  solve?: boolean;
  persist?: boolean;
  feedbackCutoff?: string | null;
}

export interface MasterPlanPlanningPreviewAllocation {
  allocationId: string;
  segmentIndex: number;
  workOrderNo: string;
  productCode: string;
  resourceId: string;
  operationSeq: number;
  operationName: string | null;
  dueDate: string | null;
  durationMinutes: number;
  scheduled: boolean;
  slotIndex: number | null;
  slotDate: string | null;
  shiftId: string | null;
  plannedStartTs: string | null;
  plannedEndTs: string | null;
}

export interface MasterPlanPlanningPreview {
  computedAt: string;
  planningStart: string;
  strategyId: string;
  strategyName: string;
  capacityStrategy: string;
  overlayActive: boolean;
  solved: boolean;
  persisted: boolean;
  planVersionId: string | null;
  score: string | null;
  solveDurationMs: number | null;
  diagnostics: MasterPlanPlanningDiagnostics;
  allocations: MasterPlanPlanningPreviewAllocation[];
  allocationCount: number;
  scheduledAllocationCount: number;
}
