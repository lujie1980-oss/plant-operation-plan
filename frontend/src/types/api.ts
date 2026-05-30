import type { PlanningPipelineRunDiagnostics } from './planningDiagnostics';

export interface DemandPoolEntry {
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  orderQty: number;
  dueDate: string;
  promiseDate: string | null;
  priority: number;
  expediteLevel: number;
  status: string;
  scheduleLockFlag: boolean;
  kittingStatus: string;
  fulfillmentStatus: string;
}

export interface DemandPoolKpi {
  metricId: string;
  label: string;
  value: number;
  unit: string;
  severity: 'ok' | 'warn' | 'danger' | 'info' | string;
}

export interface DemandPoolSummary {
  kpis: DemandPoolKpi[];
}

export interface FulfillmentOperation {
  operationId: string;
  operationName: string;
  sequenceNo: number;
  resourceId: string;
  startTs: string;
  endTs: string;
  durationMinutes: number;
  utilizationPct: number;
}

export interface UtilizationBucket {
  resourceId: string;
  bucketStart: string;
  bucketEnd: string;
  demandMinutes: number;
  availableMinutes: number;
  utilizationPct: number;
}

export interface FulfillmentChainNode {
  nodeId: string;
  nodeType: string;
  laneId: string;
  label: string;
  status: string;
  depth: number;
  productCode: string;
  quantity: number;
  startTs: string;
  endTs: string;
  attributes: Record<string, unknown>;
  operations: FulfillmentOperation[];
}

export interface FulfillmentPegEdge {
  fromNodeId: string;
  toNodeId: string;
  pegType: string;
  pegLabel?: string;
}

export interface OrderFulfillmentChain {
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  dueDate: string;
  promiseDate: string | null;
  overallStatus: string;
  kittingStatus: string;
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  utilizationBuckets: UtilizationBucket[];
}

export interface KittingResult {
  salesOrderNo: string;
  salesOrderLineNo: number;
  kittingStatus: string;
  shortageReason: string | null;
}

export interface MaterialBalanceDay {
  date: string;
  openingQty: number;
  demandQty: number;
  supplyQty: number;
  closingQty: number;
  shortageQty: number;
}

export interface MaterialBalanceRow {
  productCode: string;
  critical: boolean;
  totalShortageQty: number;
  days: MaterialBalanceDay[];
}

export interface MaterialRequirementReport {
  kpis: DemandPoolKpi[];
  horizonStart: string;
  horizonEnd: string;
  dates: string[];
  materials: MaterialBalanceRow[];
  kittingResults: KittingResult[];
}

export interface MaterialDemandUsage {
  demandType: string;
  demanderLabel: string;
  salesOrderNo: string;
  salesOrderLineNo: number;
  parentProductCode: string;
  needDate: string;
  quantity: number;
  bomLevel: number;
}

export interface MaterialDemandTreeNode {
  nodeId: string;
  nodeType: string;
  label: string;
  productCode: string;
  needDate: string;
  quantity: number;
  children: MaterialDemandTreeNode[];
}

export interface MaterialDemandDetail {
  productCode: string;
  roots: MaterialDemandTreeNode[];
  totalQuantity: number;
  pathCount: number;
}

export interface CapacityBucketWorkOrder {
  workOrderNo: string;
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  quantity: number;
  loadMinutes: number;
  scheduleSource: string;
  feedbackLocked?: boolean;
}

export interface LoadBucket {
  bucketId: string;
  resourceId: string;
  resourceLabel: string;
  date: string;
  shiftId: string;
  demandMinutes: number;
  feedbackLockedMinutes: number;
  availableMinutes: number;
  utilizationPct: number;
  overloaded: boolean;
  workOrders: CapacityBucketWorkOrder[];
}

export interface WorkOrderScheduleOperation {
  operationId: string;
  operationSeq: number;
  operationName: string;
  resourceId: string;
  plannedStart: string;
  plannedEnd: string;
  durationMinutes: number;
  scope: string;
}

export interface LineOpeningSuggestion {
  areaId: string;
  lineId: string;
  shiftId: string;
  date: string;
  open: boolean;
  suggestedHeadcount: number;
  reason: string;
}

export interface CapacityAnalysis {
  kpis: DemandPoolKpi[];
  loadBuckets: LoadBucket[];
  lineOpeningSuggestions: LineOpeningSuggestion[];
}

/** EXTERNAL=成品工单（订单层根工单）；REPLENISH=组件工单（BOM 子件） */
export type WorkOrderSource = 'EXTERNAL' | 'REPLENISH';

/** 主计划产能策略：无约束允许超负荷；有限产能则工单按日历拆段跨天 */
export type MasterPlanCapacityStrategy = 'UNCONSTRAINED' | 'FINITE_CAPACITY';

export interface MasterPlanAllocation {
  allocationId: string;
  segmentIndex: number;
  workOrderNo: string;
  parentWorkOrderNo: string | null;
  workOrderSource: WorkOrderSource;
  productCode: string;
  quantity: number;
  salesOrderNo: string;
  salesOrderLineNo: number;
  resourceId: string;
  slotIndex: number;
  slotDate: string;
  shiftId: string;
  plannedStartTs: string;
  plannedEndTs: string;
  durationMinutes: number;
}

export interface LineOpeningDecision {
  areaId: string;
  lineId: string;
  shiftId: string;
  date: string;
  opened: boolean;
  suggestedHeadcount: number;
}

export interface MasterPlanResult {
  planVersionId: string;
  score: string;
  solveDurationMs: number;
  capacityStrategy: MasterPlanCapacityStrategy;
  strategyId?: string | null;
  strategyName?: string | null;
  kpis: DemandPoolKpi[];
  allocations: MasterPlanAllocation[];
  lineOpenings: LineOpeningDecision[];
}

export interface WorkOrderCapacityOperation {
  operationId: string;
  operationName: string;
  sequenceNo: number;
  resourceId: string;
  plannedStartTs: string;
  plannedEndTs: string;
  durationMinutes: number;
}

export interface WorkOrderCapacityBucket {
  resourceId: string;
  date: string;
  shiftId: string;
  demandMinutes: number;
  availableMinutes: number;
  utilizationPct: number;
  overloaded: boolean;
}

export interface WorkOrderTimingWindow {
  latestDesiredStart: string;
  latestDesiredEnd: string;
  latestDesiredDelivery: string;
  earliestPossibleStart: string;
  earliestPossibleEnd: string;
  earliestPossibleDelivery: string;
  earliestPossibleStartOwn: string;
  earliestPossibleEndOwn: string;
  earliestPossibleDeliveryOwn: string;
  productionDurationMinutes: number;
  postProcessingMinutes: number;
}

export interface WorkOrderCapacityGantt {
  workOrderNo: string;
  parentWorkOrderNo: string | null;
  workOrderSource: WorkOrderSource;
  productCode: string;
  quantity: number;
  salesOrderNo: string;
  salesOrderLineNo: number;
  plannedStartTs: string;
  plannedEndTs: string;
  totalDurationMinutes: number;
  horizonStartTs: string;
  horizonEndTs: string;
  timingWindow: WorkOrderTimingWindow | null;
  operations: WorkOrderCapacityOperation[];
  resourceBuckets: WorkOrderCapacityBucket[];
}

export interface DetailScheduleOperation {
  operationId: string;
  workOrderNo: string;
  lineId: string;
  resourceId: string;
  sequenceIndex: number;
  startMinute: number;
  endMinute: number;
  productCode: string;
  pinned: boolean;
}

export interface ShortageRecommendation {
  shortageId: string;
  shortageType: string;
  severity: string;
  areaId: string;
  shiftId: string;
  lineId: string;
  recommendedAction: string;
  impactOrders: string[];
}

export interface MasterPlanRefreshResult {
  newMasterPlanVersionId: string;
  parentMasterPlanVersionId: string;
  detailScheduleVersionId: string;
  feedbackCutoff: string;
  frozenAllocationRows: number;
  replannedAllocationRows: number;
}

export interface ScheduleFeedbackApplyResult {
  feedbackBatchId: string;
  detailScheduleVersionId: string;
  masterPlanVersionId: string | null;
  cutoffDate: string;
  operationCount: number;
  frozenCount: number;
  suggestionCount: number;
}

export interface ScheduleFeedback {
  feedbackId: string;
  masterPlanVersionId: string | null;
  detailScheduleVersionId: string;
  workOrderNo: string;
  operationSeq: number;
  operationId: string;
  resourceId: string;
  plannedStart: string;
  plannedEnd: string;
  slotDate: string;
  durationMinutes: number;
  scope: 'FROZEN' | 'SUGGESTION' | string;
  planningAnchorDate: string;
  feedbackTs: string;
}

export interface DetailScheduleResult {
  planVersionId: string;
  score: string;
  solveDurationMs: number;
  operations: DetailScheduleOperation[];
  shortageRecommendations: ShortageRecommendation[];
  masterPlanRefresh?: MasterPlanRefreshResult | null;
}

export interface DispatchResult {
  planVersionId: string;
  dispatchedTs: string;
  status: string;
}

export interface KpiMetric {
  metricId: string;
  value: number;
  unit: string;
}

export interface KpiReport {
  metrics: KpiMetric[];
}

export interface PipelineRunLogLine {
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR' | string;
  message: string;
}

export interface PlanningPipelineRun {
  runId: string;
  capacityStrategy: string;
  strategyId?: string | null;
  strategyName?: string | null;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  masterPlanVersionId: string | null;
  detailPlanVersionId: string | null;
  masterPlanScore: string | null;
  errorMessage: string | null;
  executionLog?: PipelineRunLogLine[];
  diagnostics?: PlanningPipelineRunDiagnostics | null;
}

export interface PipelineResult {
  pipelineRunId: string;
  executionLog?: PipelineRunLogLine[];
  demandPool: DemandPoolEntry[];
  kittingResults: KittingResult[];
  capacityAnalysis: CapacityAnalysis;
  masterPlan: MasterPlanResult;
  detailSchedule?: DetailScheduleResult | null;
  masterPlanRefresh?: MasterPlanRefreshResult | null;
  dispatch?: DispatchResult | null;
  kpiReport?: KpiReport | null;
}

export interface PlanningScenario {
  scenarioId: string;
  name: string;
  isDefault: boolean;
  strategyId?: string | null;
  strategyName?: string | null;
  ruleSetVersionId: string;
  ruleSetVersionName?: string | null;
  currentPlanVersionId: string | null;
  previousPlanVersionId: string | null;
  currentGeneratedAt?: string | null;
  currentScore?: string | null;
  currentSolveDurationMs?: number | null;
  /** 生效主计划版本（分析页 API 使用） */
  planVersionId: string | null;
  runId: string | null;
  label: string;
  capacityStrategy: string;
  generatedAt: string | null;
  score: string | null;
  solveDurationMs: number | null;
}

export interface RuleSetVersion {
  ruleSetVersionId: string;
  name: string;
  isDefault: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CreatePlanningScenarioPayload {
  name: string;
  strategyId?: string;
  ruleSetVersionId?: string;
}

export interface CreateRuleSetVersionPayload {
  name: string;
  copyFromRuleSetVersionId?: string;
}

export interface ScenarioMetric {
  metricId: string;
  label: string;
  unit: string;
  chartType: string;
}

export interface ScenarioComparisonSeries {
  planVersionId: string;
  scenarioLabel: string;
  metricId: string;
  value: number;
}

export interface ScenarioComparison {
  metrics: ScenarioMetric[];
  series: ScenarioComparisonSeries[];
}

export interface PlanVersionCompare {
  fromVersionId: string;
  toVersionId: string;
  fromScore: string;
  toScore: string;
  impactSummary: string[];
}

export interface RescheduleResult {
  level: string;
  masterPlanVersionId: string | null;
  detailScheduleVersionId: string | null;
  impactedOrders: string[];
}

export interface WorkOrder {
  id: number | null;
  workOrderNo: string;
  parentWorkOrderNo: string | null;
  workOrderSource: WorkOrderSource;
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  quantity: number;
  resourceId: string;
  sequenceNo: number;
  dispatchStatus: 'PENDING' | 'DISPATCHED' | string;
  dispatchedTs: string | null;
  plannedSlotDate?: string | null;
  plannedShiftId?: string | null;
  inScenarioPlan?: boolean;
  hasScheduleFeedback?: boolean;
  hasFrozenScheduleFeedback?: boolean;
  scheduleFeedbackOperationCount?: number;
  linkedDetailScheduleVersionId?: string | null;
  needDate?: string | null;
  bomLevel?: number;
  orderLineTreeParentWorkOrderNo?: string | null;
  orderLinePeggedQty?: number | null;
  peggingCount?: number;
  timingWindow?: WorkOrderTimingWindow | null;
}

export interface OrderLineWorkOrderNode {
  workOrder: WorkOrder;
  orderLineTreeParentWorkOrderNo: string | null;
  peggedQtyForLine: number;
}

export interface WorkOrderOrderLineTree {
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  dueDate: string;
  workOrders: OrderLineWorkOrderNode[];
}

export interface WorkOrderDispatchResult {
  dispatchedCount: number;
  dispatchedTs: string;
  workOrderNos: string[];
}

export interface WorkOrderGenerationBatchResult {
  orderLinesProcessed: number;
  workOrdersCreated: number;
  details: { salesOrderNo: string; salesOrderLineNo: number; workOrdersCreated: number; workOrderNos: string[] }[];
}

export interface WorkOrderKittingLine {
  componentProductCode: string;
  requiredQty: number;
  availableQty: number;
  shortage: boolean;
}

export interface WorkOrderKitting {
  workOrderNo: string;
  productCode: string;
  quantity: number;
  dispatchStatus: string;
  kittingStatus: string;
  shortageReason: string | null;
  lines: WorkOrderKittingLine[];
}

export interface DashboardSummary {
  demandFulfillmentRatePct: number;
  capacityUtilizationPct: number;
  materialShortageRatePct: number;
  totalDemandLines: number;
  fulfilledCount: number;
  unfulfilledCount: number;
  shortageCount: number;
  overloadedBucketCount: number;
  latestMasterPlanVersionId: string | null;
  latestDetailPlanVersionId: string | null;
  unfulfilledDemands: DemandPoolEntry[];
  shortageAffectedOrders: DemandPoolEntry[];
  highUtilizationBuckets: LoadBucket[];
}

export interface DemandTrackingFlowStep {
  stepId: string;
  label: string;
  status: string;
  detail: string;
}

export interface DemandTrackingProcessNode {
  nodeId: string;
  nodeType: string;
  label: string;
  planStatus: string;
  plannedStart: string | null;
  plannedEnd: string | null;
  productionStart: string | null;
  productionEnd: string | null;
  sequenceNo: number;
}

export interface DemandTrackingProcessEdge {
  fromNodeId: string;
  toNodeId: string;
}

export interface DemandTrackingEntry {
  salesOrderNo: string;
  salesOrderLineNo: number;
  customerCode: string | null;
  productCode: string;
  orderQty: number;
  dueDate: string;
  promiseDate: string | null;
  priority: number;
  orderStatus: string;
  fulfillmentStatus: string;
  kittingStatus: string;
  workOrderCount: number;
  dispatchedWorkOrderCount: number;
  scheduledOperationCount: number;
  executionStatus: string;
  progressPct: number;
  flowSteps: DemandTrackingFlowStep[];
  processNodes: DemandTrackingProcessNode[];
  processEdges: DemandTrackingProcessEdge[];
}
