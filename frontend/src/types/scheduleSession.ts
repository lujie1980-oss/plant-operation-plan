import type { DetailSchedulePlanningPreview } from './detailSchedulePlanningPreview';

export interface CreateScheduleSessionRequest {
  masterPlanVersionId: string;
  seedInitialQueues?: boolean;
  solve?: boolean;
  simulationProfileId?: string;
}

export interface ScheduleSession {
  sessionId: string;
  masterPlanVersionId: string;
  createdAt: string;
  expiresAt: string;
  preview: DetailSchedulePlanningPreview;
  simulationProfileId?: string | null;
}

export interface PlanningConflict {
  conflictId: string;
  stepId: string;
  planVersionId: string;
  reasonCode: string;
  message: string;
  detectedTs: string;
  resolved: boolean;
}

export interface SimulateScheduleSessionRequest {
  stepPatches?: SessionStepPatch[];
  affectedOperationIds?: string[];
  fullReschedule?: boolean;
  simulationProfileId?: string;
  ruleOverrides?: Record<string, { enabled?: boolean }>;
}

export interface SessionStepPatch {
  stepId: string;
  lineId?: string;
  sequenceOnLine?: number;
  pinned?: boolean;
}

export interface ScheduleSessionSimulateResult {
  session: ScheduleSession;
  simulationMode: string;
  simulationDurationMs: number;
  recalculatedOperationIds: string[];
  violations: import('./detailSchedulePlanningPreview').ScheduleConstraintViolation[];
  hardViolationCount: number;
  mediumViolationCount: number;
  appliedRules?: string[];
  simulationProfileId?: string | null;
}

export interface ConfirmScheduleSessionResult {
  planVersionId: string;
  releasedCount: number;
  conflicts: PlanningConflict[];
}

export interface ProductionTask {
  stepId: string;
  batchNo: string | null;
  workOrderNo: string;
  operationSeq: number;
  operationName: string;
  productCode: string;
  lineId: string | null;
  resourceId: string | null;
  quantity: number | null;
  plannedStartTs: string | null;
  plannedEndTs: string | null;
  planVersionId: string | null;
  executionState: string;
  releasedTs: string | null;
  actualStartTs: string | null;
  actualEndTs: string | null;
  updatedTs: string | null;
}
