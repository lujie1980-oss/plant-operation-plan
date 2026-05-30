export interface MasterDataRecord {
  id: number | null;
}

/** 规则项目级启用范围 */
export interface BusinessRuleScopeMd {
  ruleTypeId: string;
  label: string;
  enableMasterPlan: boolean;
  enableDetailSchedule: boolean;
}

export interface SalesOrderMd extends MasterDataRecord {
  salesOrderNo: string;
  salesOrderLineNo: number;
  customerCode: string | null;
  productCode: string;
  orderQty: number;
  uom: string | null;
  promiseDate: string | null;
  dueDate: string;
  priority: number;
  expediteLevel: number;
  status: string;
  scheduleLockFlag: boolean;
}

export interface BomMd extends MasterDataRecord {
  finishedProductCode: string | null;
  bomId: string;
  bomVersion: string;
  parentProductCode: string;
  componentProductCode: string;
  componentQty: number;
  isCriticalComponent: boolean;
  bomEffectiveFrom: string | null;
  bomEffectiveTo: string | null;
  componentEffectiveFrom: string | null;
  componentEffectiveTo: string | null;
  scrapRate: number | null;
  lotSize?: number | null;
  lotSizeMultiple?: number | null;
}

export interface MaterialMd extends MasterDataRecord {
  siteCode: string | null;
  materialCode: string;
  materialName: string | null;
  uomCode: string | null;
  materialType: string | null;
}

export interface InventoryMd extends MasterDataRecord {
  stockingPointCode: string;
  productCode: string;
  onhandQty: number;
  reservedQty: number;
  qualityHoldQty: number;
  inTransitQty: number;
}

export interface ResourceMd extends MasterDataRecord {
  resourceId: string;
  resourceGroup: string | null;
  areaId: string;
  bottleneck: boolean;
  runRatePerHour: number;
}

export interface ProductResourceMd extends MasterDataRecord {
  productCode: string;
  resourceId: string;
  setupTimeMinutes: number;
  sequenceNo: number | null;
  resourcePriority: number;
  operationName: string | null;
  processTimeSeconds: number | null;
  bomLevel: string | null;
  wireMaterial: string | null;
  keyMaterial: string | null;
  maleFemaleEnd: string | null;
  totalBranch: string | null;
  standardLabor: number | null;
}

export interface ProductionLineMd extends MasterDataRecord {
  lineId: string;
  areaId: string;
  resourceId: string;
  lineMinHeadcount: number;
  lineCapacityPerShift: number;
}

export interface ResourceCalendarMd extends MasterDataRecord {
  resourceId: string;
  shiftId: string;
  calendarDate: string;
  availableCapacityMinutes: number;
  unavailableCapacityMinutes: number;
}

export interface ShiftHeadcountMd extends MasterDataRecord {
  areaId: string;
  shiftId: string;
  calendarDate: string;
  availableHeadcount: number;
}

export interface ChangeoverMd extends MasterDataRecord {
  operationName: string;
  attributeKey: string;
  fromAttributeValue: string;
  toAttributeValue: string;
  setupMinutes: number;
}

export interface ParallelOperationMd extends MasterDataRecord {
  lineId: string;
  firstProductCode: string;
  secondProductCode: string;
}

export interface OperationTransferTimeMd extends MasterDataRecord {
  productCode: string;
  fromOperationName: string;
  toOperationName: string;
  transferMinutes: number;
  minTransferMinutes: number;
}

export interface OperationPostProcessingMd extends MasterDataRecord {
  productCode: string;
  operationName: string;
  postProcessingMinutes: number;
}

export interface MaterialLeadTimeMd extends MasterDataRecord {
  productCode: string;
  leadTimeDays: number;
}

export interface ContinuousProductionMd extends MasterDataRecord {
  lineId: string;
  firstProductCode: string;
  secondProductCode: string;
  finishedProductCode: string;
}

export interface SystemParameterMd extends MasterDataRecord {
  paramId: string;
  paramValue: string;
  description: string | null;
}

export type ValidationSeverity = 'ERROR' | 'WARNING';

export interface ValidationIssueMd {
  ruleId: string;
  severity: ValidationSeverity;
  entityType: string;
  entityKey: string;
  reason: string;
  fields: Record<string, unknown> | null;
}

export interface BlockedSalesOrderLineMd {
  salesOrderNo: string;
  salesOrderLineNo: number;
  ruleId: string;
  reason: string;
}

export interface MasterDataValidationReportMd {
  errors: ValidationIssueMd[];
  warnings: ValidationIssueMd[];
  blockedSalesOrderLines: BlockedSalesOrderLineMd[];
}
