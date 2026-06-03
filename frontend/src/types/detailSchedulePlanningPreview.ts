import type { DetailSchedulePlanningDiagnostics } from './planningDiagnostics';
import type { ShortageRecommendation } from './api';

export interface DetailSchedulePlanningPreviewRequest {
  masterPlanVersionId: string;
  solve?: boolean;
  persist?: boolean;
  refreshMasterPlanAfter?: boolean;
  feedbackCutoff?: string;
  seedInitialQueues?: boolean;
}

export interface DetailSchedulePlanningPreviewLine {
  lineId: string;
  resourceId: string;
  areaId: string;
  opened: boolean;
  capacityMinutes: number;
  queuedOperationCount: number;
}

export interface DetailSchedulePlanningPreviewOperation {
  operationId: string;
  workOrderNo: string;
  batchNo: string | null;
  productCode: string;
  operationName: string;
  operationSeq: number;
  resourceId: string;
  lineId: string | null;
  sequenceOnLine: number | null;
  startMinute: number | null;
  endMinute: number | null;
  scheduled: boolean;
  kittingEligible: boolean;
  earliestStartMinute: number;
  pinned: boolean;
  mpContractStartDate: string | null;
  mpContractEndDate: string | null;
  mpTargetEndDate: string | null;
  changeoverMinutesBefore?: number | null;
}

export interface DetailSchedulePlanningPreview {
  computedAt: string;
  planningAnchor: string;
  masterPlanVersionId: string;
  solved: boolean;
  persisted: boolean;
  initialQueuesSeeded: boolean;
  planVersionId: string | null;
  score: string | null;
  solveDurationMs: number | null;
  diagnostics: DetailSchedulePlanningDiagnostics;
  lines: DetailSchedulePlanningPreviewLine[];
  operations: DetailSchedulePlanningPreviewOperation[];
  operationCount: number;
  scheduledOperationCount: number;
  shortageRecommendations: ShortageRecommendation[];
  violations?: ScheduleConstraintViolation[];
  simulationMode?: string | null;
  simulationDurationMs?: number | null;
  recalculatedOperationIds?: string[];
}

export interface ScheduleConstraintViolation {
  level: string;
  ruleCode: string;
  operationId: string | null;
  lineId: string | null;
  message: string;
}
