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
  plannedSupplyTotalMrp: number;
  plannedSupplyTotalOptimized: number;
  plannedDemandQuantityTotal: number;
  plannedInventoryLevel: number;
  stockShortageQuantity: number;
}

export interface SrpSnapshotDto {
  id: string;
  resourceId: string;
  periodId: string;
  totalCapacity: number;
  calendarDowntime: number;
  reservedCapacity: number;
  availableCapacity: number;
  freeCapacity: number;
  overloadCapacity: number;
}

export interface OperationSnapshotDto {
  id: string;
  supplyOrderId: string;
  sequenceNr: number;
  routingSequenceNo: number;
  operationName: string | null;
  productionDuration: number;
  preprocessingTime: number;
  postprocessingTime: number;
  segmentIndex: number;
  lastSegment: boolean;
  parallelGroupId: string | null;
  locked: boolean;
  earliestPossibleStartOwn: string | null;
  earliestPossibleEndOwn: string | null;
  earliestPossibleStartTotal: string | null;
  earliestPossibleEndTotal: string | null;
  latestDesiredStart: string | null;
  latestDesiredEnd: string | null;
  plannedStartTotal: string | null;
  plannedEndTotal: string | null;
  infeasible: boolean;
}

export interface MasterPlanSessionSimulateResultDto {
  recalculatedPeriodIds: string[];
  snapshots: PispPeriodSnapshotDto[];
  srpSnapshots?: SrpSnapshotDto[];
  operationSnapshots?: OperationSnapshotDto[];
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

export type OntologySimulateTargetType = 'PISPP' | 'SRP' | 'SUPPLY_ORDER';

export interface SimulateMasterPlanSessionRequest {
  /** M4 F.4：PISPP | SRP | SUPPLY_ORDER；缺省为 PISPP（兼容 pispPeriodId） */
  targetType?: OntologySimulateTargetType;
  targetId?: string;
  /** @deprecated 使用 targetId；PISPP 时二者等价 */
  pispPeriodId?: string;
  property: string;
  value?: number;
  /** SUPPLY_ORDER + needDate 时使用，ISO 日期 yyyy-MM-dd */
  dateValue?: string;
}

