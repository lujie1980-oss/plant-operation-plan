export type PlanningDiagnosticSeverity = 'SKIP' | 'WARN' | 'INFO';

export interface PlanningDiagnosticIssue {
  severity: PlanningDiagnosticSeverity;
  reasonCode: string;
  workOrderNo: string | null;
  entityId: string | null;
  message: string;
}

export interface MasterPlanPlanningDiagnostics {
  computedAt: string;
  capacityStrategy: string | null;
  overlayActive: boolean;
  inventorySnapshotId: string | null;
  counters: Record<string, number>;
  issues: PlanningDiagnosticIssue[];
  issuesTruncated: boolean;
}

export interface DetailSchedulePlanningDiagnostics {
  computedAt: string;
  masterPlanVersionId: string | null;
  inventorySnapshotId: string | null;
  counters: Record<string, number>;
  issues: PlanningDiagnosticIssue[];
  issuesTruncated: boolean;
}

export type PlanningDiagnosticsLayer = 'master-plan' | 'detail-schedule';

export interface PlanningPipelineRunDiagnostics {
  masterPlan?: MasterPlanPlanningDiagnostics | null;
  detailSchedule?: DetailSchedulePlanningDiagnostics | null;
}

export type PlanningDiagnosticsSnapshot =
  | { layer: 'master-plan'; data: MasterPlanPlanningDiagnostics }
  | { layer: 'detail-schedule'; data: DetailSchedulePlanningDiagnostics };
