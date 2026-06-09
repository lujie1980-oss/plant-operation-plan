export interface MasterPlanSessionDto {
  sessionId: string;
  basePlanVersionId: string;
  pispCount: number;
  periodCount: number;
  expiresAt: string;
}

export interface PispSummaryDto {
  pispId: string;
  productCode: string | null;
}

export interface PispPeriodSnapshotDto {
  id: string;
  pispId: string;
  periodId: string;
  onHand: number;
  plannedSupplyTotal: number;
  plannedDemandQuantityTotal: number;
  plannedInventoryLevel: number;
  stockShortageQuantity: number;
}

export interface MasterPlanSessionSimulateResultDto {
  recalculatedPeriodIds: string[];
  snapshots: PispPeriodSnapshotDto[];
}

export interface MasterPlanSessionOptimizeResultDto {
  sessionId: string;
  score: string | null;
  allocationCount: number;
  solveDurationMs: number;
  affectedSnapshots: PispPeriodSnapshotDto[];
}

export interface MasterPlanSessionConfirmResultDto {
  sessionId: string;
  planVersionId: string;
  allocationCount: number;
}

export interface CreateMasterPlanSessionRequest {
  planVersionId: string;
}

export interface SimulateMasterPlanSessionRequest {
  pispPeriodId: string;
  property: 'plannedSupplyTotal' | 'plannedDemandQuantityTotal';
  value: number;
}
