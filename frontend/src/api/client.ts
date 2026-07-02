import type {
  CapacityAnalysis,
  CustomerOrderLineDeliveryListItem,
  DemandPoolSummary,
  OrderFulfillmentChain,
  DetailScheduleResult,
  MasterPlanRefreshResult,
  ScheduleFeedback,
  ScheduleFeedbackApplyResult,
  DispatchResult,
  KittingResult,
  MaterialDemandDetail,
  MaterialRequirementReport,
  KpiReport,
  MasterPlanCapacityStrategy,
  MasterPlanResult,
  PipelineResult,
  PlanVersionCompare,
  RescheduleResult,
  SrpCapacityGantt,
  WorkOrderCapacityGantt,
  WorkOrder,
  WorkOrderOrderLineTree,
  WorkOrderScheduleOperation,
  WorkOrderDispatchResult,
  WorkOrderGenerationBatchResult,
  WorkOrderKitting,
  WorkOrderRoutingDetail,
  BatchPlanWorkOrder,
  BatchSplitResult,
  ProductionBatch,
  InventoryAvailabilitySummary,
  InventoryWorkOrderAllocation,
  DemandTrackingEntry,
  DashboardSummary,
  PlanningPipelineRun,
  PlanningScenario,
  RuleSetVersion,
  CreatePlanningScenarioPayload,
  CreateRuleSetVersionPayload,
  ScenarioComparison,
} from '../types/api';
import type {
  FactoryCalendarDay,
  FactoryCalendarMonth,
  FactoryCalendarPolicy,
  FactoryDayOverrideRequest,
  FactoryCalendarSyncResult,
} from '../types/factoryCalendar';
import type {
  BomMd,
  BusinessRuleScopeMd,
  ChangeoverMd,
  OperationTransferTimeMd,
  OperationPostProcessingMd,
  MaterialLeadTimeMd,
  ContinuousProductionMd,
  DeliveryDateStrategyMd,
  SupplyQuantityRuleMd,
  ResourceEfficiencyMd,
  RoutingStepTimingMd,
  RoutingStepResourceMd,
  ParallelOperationMd,
  InventoryMd,
  MaterialMd,
  MasterDataValidationReportMd,
  MasterFieldDefinitionCreateMd,
  MasterFieldDefinitionMd,
  MasterFieldDefinitionUpdateMd,
  ProductResourceMd,
  ProductionLineMd,
  ResourceCalendarMd,
  ResourceMd,
  SalesOrderMd,
  ShiftHeadcountMd,
  SystemParameterMd,
} from '../types/masterData';
import type { MasterPlanObjective, MasterPlanObjectiveUpdate } from '../types/masterPlanObjectives';
import type {
  DetailSchedulePlanningPreview,
  DetailSchedulePlanningPreviewRequest,
} from '../types/detailSchedulePlanningPreview';
import type {
  MasterPlanPlanningPreview,
  MasterPlanPlanningPreviewRequest,
} from '../types/masterPlanPlanningPreview';
import type { PlanningScoreExplanation } from '../types/planningScoreExplanation';
import type { PromiseDatePreview } from '../types/demandActions';
import type {
  ConfirmScheduleSessionResult,
  CreateScheduleSessionRequest,
  ProductionTask,
  ScheduleSession,
} from '../types/scheduleSession';
import type {
  MasterPlanStrategyCreate,
  MasterPlanStrategyDetail,
  MasterPlanStrategySummary,
  MasterPlanStrategyUpdate,
} from '../types/masterPlanStrategies';
import type {
  MasterPlanSessionConfirmResultDto,
  MasterPlanSessionDto,
  MasterPlanSessionOptimizeResultDto,
  MasterPlanSessionSimulateResultDto,
  PispPeriodSnapshotDto,
  PispSummaryDto,
  SimulateMasterPlanSessionRequest,
  SrpSnapshotDto,
} from '../types/ontology';
import { getStoredWorkspaceId } from '../context/WorkspaceContext';
import { apiHeaders as sharedApiHeaders } from './http';
import type { Workspace, WorkspaceCreatePayload } from '../types/workspace';

export type MasterDataImportResult = {
  rowsImported: number;
  errors: string[];
};

function filenameFromDisposition(header: string | null, fallback: string): string {
  if (!header) return fallback;
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  return match ? decodeURIComponent(match[1].trim()) : fallback;
}

function workspaceHeaders(extra?: HeadersInit): HeadersInit {
  return sharedApiHeaders(extra);
}

async function downloadBlob(path: string, fallbackName: string): Promise<void> {
  const res = await fetch(path, { headers: workspaceHeaders() });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const blob = await res.blob();
  const name = filenameFromDisposition(res.headers.get('Content-Disposition'), fallbackName);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

function versionQuery(masterPlanVersionId?: string): string {
  return masterPlanVersionId
    ? `?masterPlanVersionId=${encodeURIComponent(masterPlanVersionId)}`
    : '';
}

function detailScheduleVersionQuery(detailScheduleVersionId?: string): string {
  return detailScheduleVersionId
    ? `?detailScheduleVersionId=${encodeURIComponent(detailScheduleVersionId)}`
    : '';
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Workspace-Id', getStoredWorkspaceId());
  if (init?.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(path, { ...init, headers });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

type MasterPlanSessionDtoWire = Omit<MasterPlanSessionDto, 'basePlanVersionId'> & {
  basePlanVersionId?: string;
  planVersionId?: string;
};

function normalizeMasterPlanSessionDto(dto: MasterPlanSessionDtoWire): MasterPlanSessionDto {
  return {
    sessionId: dto.sessionId,
    basePlanVersionId: dto.basePlanVersionId ?? dto.planVersionId ?? '',
    pispCount: dto.pispCount,
    periodCount: dto.periodCount,
    expiresAt: dto.expiresAt,
  };
}

export const api = {
  workspaces: {
    list: () => request<Workspace[]>('/api/v1/workspaces'),
    create: (payload: WorkspaceCreatePayload) =>
      request<Workspace>('/api/v1/workspaces', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    delete: (id: string) =>
      request<void>(`/api/v1/workspaces/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    reloadSampleData: (dataset?: string) =>
      request<{ status: string; resource: string; message: string }>(
        `/api/v1/admin/reload-sample-data${dataset ? `?dataset=${encodeURIComponent(dataset)}` : ''}`,
        { method: 'POST' },
      ),
  },
  ontologyDeliveries: (masterPlanVersionId?: string) =>
    request<CustomerOrderLineDeliveryListItem[]>(
      `/api/v1/ontology/fulfillment/deliveries${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyDeliverySummary: (masterPlanVersionId?: string) =>
    request<DemandPoolSummary>(
      `/api/v1/ontology/fulfillment/deliveries/summary${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyFulfillmentChain: (deliveryId: string, masterPlanVersionId?: string) =>
    request<OrderFulfillmentChain>(
      `/api/v1/ontology/fulfillment/deliveries/${encodeURIComponent(deliveryId)}/fulfillment-chain${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyPromiseDatePreview: (deliveryId: string, masterPlanVersionId?: string) =>
    request<PromiseDatePreview>(
      `/api/v1/ontology/fulfillment/deliveries/${encodeURIComponent(deliveryId)}/promise-date-preview${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyDeliveryAction: (
    deliveryId: string,
    action: import('../types/demandActions').OrderDemandActionId,
    body?: import('../types/demandActions').OrderDemandActionRequest,
    masterPlanVersionId?: string,
  ) =>
    request<import('../types/demandActions').OrderDemandActionResult>(
      `/api/v1/ontology/fulfillment/deliveries/${encodeURIComponent(deliveryId)}/actions/${action}${versionQuery(masterPlanVersionId)}`,
      {
        method: 'POST',
        body: JSON.stringify(body ?? {}),
      },
    ),
  ontologySupplyOrderUpstreamChain: (workOrderNo: string, masterPlanVersionId?: string) =>
    request<OrderFulfillmentChain>(
      `/api/v1/ontology/fulfillment/supply-orders/${encodeURIComponent(workOrderNo)}/upstream-chain${versionQuery(masterPlanVersionId)}`,
    ),
  ontologySupplyOrderDownstreamChain: (workOrderNo: string, masterPlanVersionId?: string) =>
    request<OrderFulfillmentChain>(
      `/api/v1/ontology/fulfillment/supply-orders/${encodeURIComponent(workOrderNo)}/downstream-chain${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyAnalyzeCapacity: (masterPlanVersionId?: string) =>
    request<CapacityAnalysis>(
      `/api/v1/ontology/capacity/analyze${versionQuery(masterPlanVersionId)}`,
      { method: 'POST' },
    ),
  ontologySrpCapacityGantt: (masterPlanVersionId?: string) =>
    request<SrpCapacityGantt>(`/api/v1/ontology/capacity/srp-gantt${versionQuery(masterPlanVersionId)}`),
  ontologyMaterialPlanningBalance: (masterPlanVersionId?: string) =>
    request<MaterialRequirementReport>(
      `/api/v1/ontology/material-planning/balance${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyMaterialPlanningCompute: (masterPlanVersionId?: string) =>
    request<MaterialRequirementReport>(
      `/api/v1/ontology/material-planning/compute${versionQuery(masterPlanVersionId)}`,
      { method: 'POST' },
    ),
  ontologyMaterialPlanningDemandDetail: (productCode: string, masterPlanVersionId?: string) =>
    request<MaterialDemandDetail>(
      `/api/v1/ontology/material-planning/materials/${encodeURIComponent(productCode)}/demand-detail${versionQuery(masterPlanVersionId)}`,
    ),
  ontologyMaterialPlanningRoutingCandidates: (
    pispId: string,
    periodFrom: string,
    periodTo: string,
    opts?: { quantity?: number; masterPlanVersionId?: string },
  ) => {
    const params = new URLSearchParams();
    params.set('periodFrom', periodFrom);
    params.set('periodTo', periodTo);
    if (opts?.quantity != null) params.set('quantity', String(opts.quantity));
    if (opts?.masterPlanVersionId) params.set('masterPlanVersionId', opts.masterPlanVersionId);
    return request<import('../types/api').SupplyRoutingCandidate[]>(
      `/api/v1/ontology/material-planning/pisps/${encodeURIComponent(pispId)}/routing-candidates?${params}`,
    );
  },
  ontologyMaterialPlanningCreateSupplyPlan: (
    pispId: string,
    body: import('../types/api').CreateSupplyPlanRequest,
    masterPlanVersionId?: string,
  ) =>
    request<import('../types/api').CreateSupplyPlanResult>(
      `/api/v1/ontology/material-planning/pisps/${encodeURIComponent(pispId)}/supply-plans${versionQuery(masterPlanVersionId)}`,
      { method: 'POST', body: JSON.stringify(body) },
    ),
  masterPlanDataModelTree: () =>
    request<import('../types/masterPlanDataModel').MasterPlanDataModelTree>(
      '/api/v1/ontology/master-model/tree',
    ),
  masterPlanPispRouting: (pispId: string) =>
    request<import('../types/masterPlanDataModel').MasterPlanPispRoutingDetail>(
      `/api/v1/ontology/master-model/pisps/${encodeURIComponent(pispId)}/routing`,
    ),
  demandTracking: () => request<DemandTrackingEntry[]>('/api/v1/demand/tracking'),
  dashboardSummary: () => request<DashboardSummary>('/api/v1/dashboard/summary'),
  workOrders: {
    list: (masterPlanVersionId?: string) =>
      request<WorkOrder[]>(`/api/v1/work-orders${versionQuery(masterPlanVersionId)}`),
    listByOrderLine: (
      salesOrderNo: string,
      salesOrderLineNo: number,
      masterPlanVersionId?: string,
    ) =>
      request<WorkOrderOrderLineTree>(
        `/api/v1/work-orders/by-order-line/${encodeURIComponent(salesOrderNo)}/${salesOrderLineNo}${versionQuery(masterPlanVersionId)}`,
      ),
    generateAll: (replaceExisting = true) =>
      request<WorkOrderGenerationBatchResult>(
        `/api/v1/work-orders/generate-all?replaceExisting=${replaceExisting}`,
        { method: 'POST' },
      ),
    dispatch: (workOrderNos: string[]) =>
      request<WorkOrderDispatchResult>('/api/v1/work-orders/dispatch', {
        method: 'POST',
        body: JSON.stringify({ workOrderNos }),
      }),
    listDispatched: (detailScheduleVersionId?: string) =>
      request<WorkOrder[]>(
        `/api/v1/work-orders/dispatched${detailScheduleVersionQuery(detailScheduleVersionId)}`,
      ),
    routingDetail: (workOrderNo: string, masterPlanVersionId?: string) =>
      request<WorkOrderRoutingDetail>(
        `/api/v1/work-orders/${encodeURIComponent(workOrderNo)}/routing-detail${versionQuery(masterPlanVersionId)}`,
      ),
    updatePendingScheduleEligible: (workOrderNo: string, pendingScheduleEligible: boolean) =>
      request<WorkOrder>(
        `/api/v1/work-orders/${encodeURIComponent(workOrderNo)}/pending-schedule-eligible`,
        {
          method: 'PUT',
          body: JSON.stringify({ pendingScheduleEligible }),
        },
      ),
    inventoryAvailability: () =>
      request<InventoryAvailabilitySummary[]>('/api/v1/work-orders/dispatched/inventory/availability'),
    inventoryWorkOrders: (productCode: string) =>
      request<InventoryWorkOrderAllocation[]>(
        `/api/v1/work-orders/dispatched/inventory/${encodeURIComponent(productCode)}/work-orders`,
      ),
    dispatchedKitting: () => request<WorkOrderKitting[]>('/api/v1/work-orders/dispatched/kitting'),
    computeDispatchedKitting: () =>
      request<WorkOrderKitting[]>('/api/v1/work-orders/dispatched/kitting/compute', { method: 'POST' }),
    scheduleOperations: (workOrderNo: string, masterPlanVersionId?: string) =>
      request<WorkOrderScheduleOperation[]>(
        `/api/v1/work-orders/${encodeURIComponent(workOrderNo)}/schedule-operations${versionQuery(masterPlanVersionId)}`,
      ),
  },
  schedulingBatches: {
    listWorkOrders: () => request<BatchPlanWorkOrder[]>('/api/v1/scheduling/batches/work-orders'),
    listByWorkOrder: (workOrderNo: string) =>
      request<ProductionBatch[]>(
        `/api/v1/scheduling/batches/by-work-order/${encodeURIComponent(workOrderNo)}`,
      ),
    autoSplit: (workOrderNo: string) =>
      request<BatchSplitResult>('/api/v1/scheduling/batches/split/auto', {
        method: 'POST',
        body: JSON.stringify({ workOrderNo, quantity: null }),
      }),
    autoSplitAll: () =>
      request<import('../types/api').BulkBatchSplitResult>('/api/v1/scheduling/batches/split/auto-all', {
        method: 'POST',
        body: JSON.stringify({}),
      }),
    manualSplit: (workOrderNo: string, quantity: number) =>
      request<BatchSplitResult>('/api/v1/scheduling/batches/split/manual', {
        method: 'POST',
        body: JSON.stringify({ workOrderNo, quantity }),
      }),
    cancel: (payload: { batchNo?: string; workOrderNo?: string; cancelAll: boolean }) =>
      request<BatchSplitResult>('/api/v1/scheduling/batches/cancel', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    refreshKitting: (workOrderNo: string) =>
      request<BatchSplitResult>('/api/v1/scheduling/batches/refresh-kitting', {
        method: 'POST',
        body: JSON.stringify({ workOrderNo, quantity: null }),
      }),
    listKitting: () =>
      request<import('../types/api').ProductionBatchKitting[]>('/api/v1/scheduling/batches/kitting'),
    computeKitting: () =>
      request<import('../types/api').ProductionBatchKitting[]>('/api/v1/scheduling/batches/kitting/compute', {
        method: 'POST',
        body: JSON.stringify({}),
      }),
    batchAllocations: (productCode: string) =>
      request<import('../types/api').InventoryBatchAllocation[]>(
        `/api/v1/scheduling/batches/kitting/component/${encodeURIComponent(productCode)}/allocations`,
      ),
    updatePendingScheduleEligible: (batchNo: string, pendingScheduleEligible: boolean) =>
      request<import('../types/api').ProductionBatchKitting>(
        `/api/v1/scheduling/batches/${encodeURIComponent(batchNo)}/pending-schedule-eligible`,
        {
          method: 'PUT',
          body: JSON.stringify({ pendingScheduleEligible }),
        },
      ),
    routing: (batchNo: string, masterPlanVersionId?: string) =>
      request<WorkOrderRoutingDetail>(
        `/api/v1/scheduling/batches/${encodeURIComponent(batchNo)}/routing${versionQuery(masterPlanVersionId)}`,
      ),
  },
  computeKitting: () => request<KittingResult[]>('/api/v1/kitting/compute', { method: 'POST' }),
  solveMasterPlan: (strategyId?: string, capacityStrategy?: MasterPlanCapacityStrategy) =>
    request<MasterPlanResult>('/api/v1/planning/master-plan/solve', {
      method: 'POST',
      body: JSON.stringify({ strategyId, capacityStrategy }),
    }),
  getMasterPlan: (versionId: string) => request<MasterPlanResult>(`/api/v1/planning/master-plan/result/${versionId}`),
  previewMasterPlanPlanning: (body: MasterPlanPlanningPreviewRequest) =>
    request<MasterPlanPlanningPreview>('/api/v1/planning/master-plan/preview', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  previewDetailSchedulePlanning: (body: DetailSchedulePlanningPreviewRequest) =>
    request<DetailSchedulePlanningPreview>('/api/v1/planning/detail-schedule/preview', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  createScheduleSession: (body: CreateScheduleSessionRequest) =>
    request<ScheduleSession>('/api/v1/planning/schedule-sessions', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  confirmScheduleSession: (sessionId: string) =>
    request<ConfirmScheduleSessionResult>(
      `/api/v1/planning/schedule-sessions/${encodeURIComponent(sessionId)}/confirm`,
      { method: 'POST' },
    ),
  optimizeScheduleSession: (sessionId: string) =>
    request<ScheduleSession>(
      `/api/v1/planning/schedule-sessions/${encodeURIComponent(sessionId)}/optimize`,
      { method: 'POST' },
    ),
  simulateScheduleSession: (sessionId: string, body?: import('../types/scheduleSession').SimulateScheduleSessionRequest) =>
    request<import('../types/scheduleSession').ScheduleSessionSimulateResult>(
      `/api/v1/planning/schedule-sessions/${encodeURIComponent(sessionId)}/simulate`,
      { method: 'POST', body: JSON.stringify(body ?? {}) },
    ),
  masterPlanSessions: {
    create: (planVersionId: string) =>
      request<MasterPlanSessionDtoWire>('/api/v1/master-plan/sessions', {
        method: 'POST',
        body: JSON.stringify({ planVersionId }),
      }).then(normalizeMasterPlanSessionDto),
    get: (id: string) =>
      request<MasterPlanSessionDtoWire>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}`,
      ).then(normalizeMasterPlanSessionDto),
    listPisps: (id: string) =>
      request<PispSummaryDto[]>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/pisps`,
      ),
    listPeriods: (id: string, pispId: string) =>
      request<PispPeriodSnapshotDto[]>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/pisps/${encodeURIComponent(pispId)}/periods`,
      ),
    listResources: (id: string) =>
      request<SrpSnapshotDto[]>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/resources`,
      ),
    simulate: (id: string, body: SimulateMasterPlanSessionRequest) =>
      request<MasterPlanSessionSimulateResultDto>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/simulate`,
        { method: 'POST', body: JSON.stringify(body) },
      ),
    optimize: (id: string) =>
      request<MasterPlanSessionOptimizeResultDto>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/optimize`,
        { method: 'POST' },
      ),
    confirm: (id: string) =>
      request<MasterPlanSessionConfirmResultDto>(
        `/api/v1/master-plan/sessions/${encodeURIComponent(id)}/confirm`,
        { method: 'POST' },
      ),
  },
  scheduleSessionCandidateLines: (sessionId: string, operationId: string) =>
    request<string[]>(
      `/api/v1/planning/schedule-sessions/${encodeURIComponent(sessionId)}/operations/${encodeURIComponent(operationId)}/candidate-lines`,
    ),
  patchScheduleSessionSteps: (sessionId: string, patches: import('../types/scheduleSession').SessionStepPatch[]) =>
    request<import('../types/scheduleSession').ScheduleSessionSimulateResult>(
      `/api/v1/planning/schedule-sessions/${encodeURIComponent(sessionId)}/steps`,
      { method: 'PATCH', body: JSON.stringify(patches) },
    ),
  listProductionTasks: (executionState?: string) => {
    const q = executionState ? `?executionState=${encodeURIComponent(executionState)}` : '';
    return request<ProductionTask[]>(`/api/v1/production-tasks${q}`);
  },
  startProductionTask: (stepId: string) =>
    request<ProductionTask>(`/api/v1/production-tasks/${encodeURIComponent(stepId)}/start`, {
      method: 'POST',
    }),
  completeProductionTask: (stepId: string) =>
    request<ProductionTask>(`/api/v1/production-tasks/${encodeURIComponent(stepId)}/complete`, {
      method: 'POST',
    }),
  explainMasterPlanScore: (versionId: string) =>
    request<PlanningScoreExplanation>(
      `/api/v1/planning/master-plan/${encodeURIComponent(versionId)}/score-explanation`,
    ),
  explainDetailScheduleScore: (versionId: string, masterPlanVersionId: string) =>
    request<PlanningScoreExplanation>(
      `/api/v1/planning/detail-schedule/${encodeURIComponent(versionId)}/score-explanation?masterPlanVersionId=${encodeURIComponent(masterPlanVersionId)}`,
    ),
  listMasterPlanObjectives: () =>
    request<MasterPlanObjective[]>('/api/v1/planning/master-plan/objectives'),
  saveMasterPlanObjectives: (objectives: MasterPlanObjectiveUpdate[]) =>
    request<MasterPlanObjective[]>('/api/v1/planning/master-plan/objectives', {
      method: 'PUT',
      body: JSON.stringify({ objectives }),
    }),
  resetMasterPlanObjectives: () =>
    request<MasterPlanObjective[]>('/api/v1/planning/master-plan/objectives/reset-defaults', {
      method: 'POST',
    }),
  listMasterPlanStrategies: () =>
    request<MasterPlanStrategySummary[]>('/api/v1/planning/master-plan/strategies'),
  getMasterPlanStrategy: (strategyId: string) =>
    request<MasterPlanStrategyDetail>(
      `/api/v1/planning/master-plan/strategies/${encodeURIComponent(strategyId)}`,
    ),
  createMasterPlanStrategy: (payload: MasterPlanStrategyCreate) =>
    request<MasterPlanStrategyDetail>('/api/v1/planning/master-plan/strategies', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  updateMasterPlanStrategy: (strategyId: string, payload: MasterPlanStrategyUpdate) =>
    request<MasterPlanStrategyDetail>(
      `/api/v1/planning/master-plan/strategies/${encodeURIComponent(strategyId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
    ),
  duplicateMasterPlanStrategy: (strategyId: string, name?: string) =>
    request<MasterPlanStrategyDetail>(
      `/api/v1/planning/master-plan/strategies/${encodeURIComponent(strategyId)}/duplicate`,
      {
        method: 'POST',
        body: JSON.stringify({ name }),
      },
    ),
  deleteMasterPlanStrategy: (strategyId: string) =>
    request<void>(`/api/v1/planning/master-plan/strategies/${encodeURIComponent(strategyId)}`, {
      method: 'DELETE',
    }),
  workOrderCapacityGantt: (versionId: string, workOrderNo: string) =>
    request<WorkOrderCapacityGantt>(
      `/api/v1/planning/master-plan/${encodeURIComponent(versionId)}/work-orders/${encodeURIComponent(workOrderNo)}/capacity-gantt`,
    ),
  solveDetailSchedule: (
    masterPlanVersionId?: string,
    options?: { refreshMasterPlan?: boolean; feedbackCutoff?: string },
  ) => {
    const params = new URLSearchParams();
    if (masterPlanVersionId) {
      params.set('masterPlanVersionId', masterPlanVersionId);
    }
    if (options?.refreshMasterPlan) {
      params.set('refreshMasterPlan', 'true');
    }
    if (options?.feedbackCutoff) {
      params.set('feedbackCutoff', options.feedbackCutoff);
    }
    const q = params.toString() ? `?${params}` : '';
    return request<DetailScheduleResult>(`/api/v1/planning/detail-schedule/solve${q}`, { method: 'POST' });
  },
  applyScheduleFeedback: (
    detailScheduleVersionId: string,
    masterPlanVersionId?: string,
    feedbackCutoff?: string,
  ) => {
    const params = new URLSearchParams();
    if (masterPlanVersionId) {
      params.set('masterPlanVersionId', masterPlanVersionId);
    }
    if (feedbackCutoff) {
      params.set('feedbackCutoff', feedbackCutoff);
    }
    const q = params.toString() ? `?${params}` : '';
    return request<ScheduleFeedbackApplyResult>(
      `/api/v1/planning/schedule-feedback/apply-from-detail-schedule/${encodeURIComponent(detailScheduleVersionId)}${q}`,
      { method: 'POST' },
    );
  },
  listScheduleFeedback: (opts: { detailScheduleVersionId?: string; frozenThrough?: string }) => {
    const params = new URLSearchParams();
    if (opts.detailScheduleVersionId) {
      params.set('detailScheduleVersionId', opts.detailScheduleVersionId);
    }
    if (opts.frozenThrough) {
      params.set('frozenThrough', opts.frozenThrough);
    }
    const q = params.toString() ? `?${params}` : '';
    return request<ScheduleFeedback[]>(`/api/v1/planning/schedule-feedback${q}`);
  },
  refreshSubsequentMasterPlan: (opts: {
    parentMasterPlanVersionId: string;
    detailScheduleVersionId: string;
    feedbackCutoff?: string;
    strategyId?: string;
  }) => {
    const params = new URLSearchParams();
    params.set('parentMasterPlanVersionId', opts.parentMasterPlanVersionId);
    params.set('detailScheduleVersionId', opts.detailScheduleVersionId);
    if (opts.feedbackCutoff) {
      params.set('feedbackCutoff', opts.feedbackCutoff);
    }
    if (opts.strategyId) {
      params.set('strategyId', opts.strategyId);
    }
    return request<MasterPlanRefreshResult>(
      `/api/v1/planning/master-plan/refresh-subsequent?${params}`,
      { method: 'POST' },
    );
  },
  dispatch: (planVersionId: string) =>
    request<DispatchResult>('/api/v1/planning/dispatch', {
      method: 'POST',
      body: JSON.stringify({ planVersionId }),
    }),
  handleEvent: (eventType: string, payload: Record<string, unknown>) =>
    request<RescheduleResult>('/api/v1/events', {
      method: 'POST',
      body: JSON.stringify({ eventId: null, eventType, eventTs: null, payload }),
    }),
  kpiReport: () => request<KpiReport>('/api/v1/kpi/report'),
  listPipelineRuns: (limit = 30) =>
    request<PlanningPipelineRun[]>(`/api/v1/planning/pipeline-runs?limit=${limit}`),
  getPipelineRun: (runId: string) =>
    request<PlanningPipelineRun>(`/api/v1/planning/pipeline-runs/${encodeURIComponent(runId)}`),
  startPipelineRun: (
    strategyId: string,
    options?: { scenarioId?: string; ruleSetVersionId?: string },
  ) =>
    request<PlanningPipelineRun>('/api/v1/planning/pipeline-runs', {
      method: 'POST',
      body: JSON.stringify({
        strategyId,
        scenarioId: options?.scenarioId,
        ruleSetVersionId: options?.ruleSetVersionId,
      }),
    }),
  executePipelineRun: (
    runId: string,
    options?: { includeDetailSchedule?: boolean; refreshMasterPlanAfterSchedule?: boolean },
  ) => {
    const params = new URLSearchParams();
    if (options?.includeDetailSchedule) {
      params.set('includeDetailSchedule', 'true');
    }
    if (options?.refreshMasterPlanAfterSchedule) {
      params.set('refreshMasterPlanAfterSchedule', 'true');
    }
    const q = params.toString() ? `?${params}` : '';
    return request<PipelineResult>(
      `/api/v1/planning/pipeline-runs/${encodeURIComponent(runId)}/execute${q}`,
      {
        method: 'POST',
        body: JSON.stringify({
          includeDetailSchedule: options?.includeDetailSchedule ?? false,
          refreshMasterPlanAfterSchedule: options?.refreshMasterPlanAfterSchedule ?? false,
        }),
      },
    );
  },
  runFullPipeline: (
    strategyId: string,
    options?: {
      scenarioId?: string;
      ruleSetVersionId?: string;
      includeDetailSchedule?: boolean;
      refreshMasterPlanAfterSchedule?: boolean;
    },
  ) =>
    request<PipelineResult>('/api/v1/planning/run-full-pipeline', {
      method: 'POST',
      body: JSON.stringify({
        strategyId,
        scenarioId: options?.scenarioId,
        ruleSetVersionId: options?.ruleSetVersionId,
        includeDetailSchedule: options?.includeDetailSchedule ?? false,
        refreshMasterPlanAfterSchedule: options?.refreshMasterPlanAfterSchedule ?? false,
      }),
    }),
  listScenarios: (limit = 50) =>
    request<PlanningScenario[]>(`/api/v1/planning/scenarios?limit=${limit}`),
  listScenarioCatalog: () =>
    request<PlanningScenario[]>('/api/v1/planning/scenario-catalog'),
  createScenario: (payload: CreatePlanningScenarioPayload) =>
    request<PlanningScenario>('/api/v1/planning/scenario-catalog', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listRuleSetVersions: () => request<RuleSetVersion[]>('/api/v1/planning/rule-set-versions'),
  createRuleSetVersion: (payload: CreateRuleSetVersionPayload) =>
    request<RuleSetVersion>('/api/v1/planning/rule-set-versions', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  syncRuleSetFromWorkspace: (ruleSetVersionId: string) =>
    request<RuleSetVersion>(
      `/api/v1/planning/rule-set-versions/${encodeURIComponent(ruleSetVersionId)}/sync-from-workspace`,
      { method: 'POST' },
    ),
  compareScenarios: (planVersionIds: string[]) =>
    request<ScenarioComparison>('/api/v1/planning/scenarios/compare', {
      method: 'POST',
      body: JSON.stringify({ planVersionIds }),
    }),
  listDetailScheduleVersions: (limit = 50) =>
    request<import('../types/api').DetailScheduleVersionSummary[]>(
      `/api/v1/planning/detail-schedule/versions?limit=${limit}`,
    ),
  getDetailSchedule: (versionId: string) =>
    request<DetailScheduleResult>(
      `/api/v1/planning/detail-schedule/${encodeURIComponent(versionId)}`,
    ),
  detailSchedulePageKpis: (
    detailScheduleVersionIdOrBody?: string | null | {
      detailScheduleVersionId?: string | null;
      operations?: import('../types/api').DetailScheduleOperation[];
    },
  ) => {
    if (
      detailScheduleVersionIdOrBody != null &&
      typeof detailScheduleVersionIdOrBody === 'object'
    ) {
      return request<import('../types/api').DemandPoolKpi[]>(
        '/api/v1/planning/detail-schedule/page-kpis',
        {
          method: 'POST',
          body: JSON.stringify({
            detailScheduleVersionId: detailScheduleVersionIdOrBody.detailScheduleVersionId ?? null,
            operations: detailScheduleVersionIdOrBody.operations ?? [],
          }),
        },
      );
    }
    const q = detailScheduleVersionIdOrBody
      ? `?detailScheduleVersionId=${encodeURIComponent(detailScheduleVersionIdOrBody)}`
      : '';
    return request<import('../types/api').DemandPoolKpi[]>(
      `/api/v1/planning/detail-schedule/page-kpis${q}`,
    );
  },
  compareDetailScheduleVersions: (planVersionIds: string[]) =>
    request<ScenarioComparison>('/api/v1/planning/detail-schedule/versions/compare', {
      method: 'POST',
      body: JSON.stringify({ planVersionIds }),
    }),
  comparePlans: (from: string, to: string) =>
    request<PlanVersionCompare>(`/api/v1/planning/compare?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  erpOrders: () => request<unknown>('/api/v1/integration/erp/orders'),
  mesStatus: () => request<Record<string, unknown>>('/api/v1/integration/mes/status'),
  masterData: {
    salesOrders: {
      list: () => request<SalesOrderMd[]>('/api/v1/master-data/sales-orders'),
      save: (dto: SalesOrderMd) =>
        request<SalesOrderMd>('/api/v1/master-data/sales-orders', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/sales-orders/${id}`, { method: 'DELETE' }),
    },
    boms: {
      list: () => request<BomMd[]>('/api/v1/master-data/boms'),
      save: (dto: BomMd) =>
        request<BomMd>('/api/v1/master-data/boms', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/boms/${id}`, { method: 'DELETE' }),
    },
    materials: {
      list: () => request<MaterialMd[]>('/api/v1/master-data/materials'),
      save: (dto: MaterialMd) =>
        request<MaterialMd>('/api/v1/master-data/materials', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/materials/${id}`, { method: 'DELETE' }),
    },
    inventory: {
      list: () => request<InventoryMd[]>('/api/v1/master-data/inventory'),
      save: (dto: InventoryMd) =>
        request<InventoryMd>('/api/v1/master-data/inventory', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/inventory/${id}`, { method: 'DELETE' }),
    },
    resources: {
      list: () => request<ResourceMd[]>('/api/v1/master-data/resources'),
      save: (dto: ResourceMd) =>
        request<ResourceMd>('/api/v1/master-data/resources', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/resources/${id}`, { method: 'DELETE' }),
    },
    productResources: {
      list: () => request<ProductResourceMd[]>('/api/v1/master-data/product-resources'),
      save: (dto: ProductResourceMd) =>
        request<ProductResourceMd>('/api/v1/master-data/product-resources', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/product-resources/${id}`, { method: 'DELETE' }),
    },
    lines: {
      list: () => request<ProductionLineMd[]>('/api/v1/master-data/lines'),
      save: (dto: ProductionLineMd) =>
        request<ProductionLineMd>('/api/v1/master-data/lines', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/lines/${id}`, { method: 'DELETE' }),
    },
    calendar: {
      list: () => request<ResourceCalendarMd[]>('/api/v1/master-data/calendar'),
      save: (dto: ResourceCalendarMd) =>
        request<ResourceCalendarMd>('/api/v1/master-data/calendar', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/calendar/${id}`, { method: 'DELETE' }),
    },
    shiftHeadcount: {
      list: () => request<ShiftHeadcountMd[]>('/api/v1/master-data/shift-headcount'),
      save: (dto: ShiftHeadcountMd) =>
        request<ShiftHeadcountMd>('/api/v1/master-data/shift-headcount', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/shift-headcount/${id}`, { method: 'DELETE' }),
    },
    changeover: {
      list: () => request<ChangeoverMd[]>('/api/v1/master-data/changeover'),
      save: (dto: ChangeoverMd) =>
        request<ChangeoverMd>('/api/v1/master-data/changeover', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/changeover/${id}`, { method: 'DELETE' }),
    },
    parallelOperations: {
      list: () => request<ParallelOperationMd[]>('/api/v1/master-data/parallel-operations'),
      save: (dto: ParallelOperationMd) =>
        request<ParallelOperationMd>('/api/v1/master-data/parallel-operations', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/parallel-operations/${id}`, { method: 'DELETE' }),
    },
    operationTransferTime: {
      list: () => request<OperationTransferTimeMd[]>('/api/v1/master-data/operation-transfer-time'),
      save: (dto: OperationTransferTimeMd) =>
        request<OperationTransferTimeMd>('/api/v1/master-data/operation-transfer-time', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/operation-transfer-time/${id}`, { method: 'DELETE' }),
    },
    operationPostProcessing: {
      list: () => request<OperationPostProcessingMd[]>('/api/v1/master-data/operation-post-processing'),
      save: (dto: OperationPostProcessingMd) =>
        request<OperationPostProcessingMd>('/api/v1/master-data/operation-post-processing', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/operation-post-processing/${id}`, { method: 'DELETE' }),
    },
    materialLeadTime: {
      list: () => request<MaterialLeadTimeMd[]>('/api/v1/master-data/material-lead-time'),
      save: (dto: MaterialLeadTimeMd) =>
        request<MaterialLeadTimeMd>('/api/v1/master-data/material-lead-time', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/material-lead-time/${id}`, { method: 'DELETE' }),
    },
    continuousProduction: {
      list: () => request<ContinuousProductionMd[]>('/api/v1/master-data/continuous-production'),
      save: (dto: ContinuousProductionMd) =>
        request<ContinuousProductionMd>('/api/v1/master-data/continuous-production', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/continuous-production/${id}`, { method: 'DELETE' }),
    },
    parameters: {
      list: () => request<SystemParameterMd[]>('/api/v1/master-data/parameters'),
      save: (dto: SystemParameterMd) =>
        request<SystemParameterMd>('/api/v1/master-data/parameters', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/parameters/${id}`, { method: 'DELETE' }),
    },
    deliveryDateStrategy: {
      list: () => request<DeliveryDateStrategyMd[]>('/api/v1/master-data/delivery-date-strategy'),
      save: (dto: DeliveryDateStrategyMd) =>
        request<DeliveryDateStrategyMd>('/api/v1/master-data/delivery-date-strategy', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/delivery-date-strategy/${id}`, { method: 'DELETE' }),
    },
    supplyQuantityRules: {
      list: () => request<SupplyQuantityRuleMd[]>('/api/v1/master-data/supply-quantity-rules'),
      save: (dto: SupplyQuantityRuleMd) =>
        request<SupplyQuantityRuleMd>('/api/v1/master-data/supply-quantity-rules', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/supply-quantity-rules/${id}`, { method: 'DELETE' }),
    },
    resourceEfficiency: {
      list: () => request<ResourceEfficiencyMd[]>('/api/v1/master-data/resource-efficiency'),
      save: (dto: ResourceEfficiencyMd) =>
        request<ResourceEfficiencyMd>('/api/v1/master-data/resource-efficiency', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/resource-efficiency/${id}`, { method: 'DELETE' }),
    },
    routingStepTiming: {
      list: () => request<RoutingStepTimingMd[]>('/api/v1/master-data/routing-step-timing'),
      save: (dto: RoutingStepTimingMd) =>
        request<RoutingStepTimingMd>('/api/v1/master-data/routing-step-timing', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/routing-step-timing/${id}`, { method: 'DELETE' }),
    },
    routingStepResource: {
      list: () => request<RoutingStepResourceMd[]>('/api/v1/master-data/routing-step-resource'),
      save: (dto: RoutingStepResourceMd) =>
        request<RoutingStepResourceMd>('/api/v1/master-data/routing-step-resource', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/routing-step-resource/${id}`, { method: 'DELETE' }),
    },
    businessRuleScopes: {
      list: () => request<BusinessRuleScopeMd[]>('/api/v1/master-data/business-rule-scopes'),
      save: (ruleTypeId: string, dto: BusinessRuleScopeMd) =>
        request<BusinessRuleScopeMd>(`/api/v1/master-data/business-rule-scopes/${encodeURIComponent(ruleTypeId)}`, {
          method: 'PUT',
          body: JSON.stringify(dto),
        }),
    },
    validation: () => request<MasterDataValidationReportMd>('/api/v1/master-data/validation'),
    fieldSchema: (entityType: string) =>
      request<MasterFieldDefinitionMd[]>(
        `/api/v1/master-data/field-schema/${encodeURIComponent(entityType)}`,
      ),
    fieldDefinitions: {
      create: (dto: MasterFieldDefinitionCreateMd) =>
        request<MasterFieldDefinitionMd>('/api/v1/master-data/field-definitions', {
          method: 'POST',
          body: JSON.stringify(dto),
        }),
      update: (id: number, dto: MasterFieldDefinitionUpdateMd) =>
        request<MasterFieldDefinitionMd>(`/api/v1/master-data/field-definitions/${id}`, {
          method: 'PUT',
          body: JSON.stringify(dto),
        }),
      delete: (id: number) =>
        request<void>(`/api/v1/master-data/field-definitions/${id}`, { method: 'DELETE' }),
    },
    downloadTemplate: () =>
      downloadBlob('/api/v1/master-data/excel/template', 'master-data-template.xlsx'),
    exportAll: () => downloadBlob('/api/v1/master-data/excel/export', 'master-data-export.xlsx'),
    importExcel: async (file: File): Promise<MasterDataImportResult> =>
      importMasterDataExcel('/api/v1/master-data/excel/import', file),
    importChangeoverExcel: async (file: File, replace = true): Promise<MasterDataImportResult> =>
      importMasterDataExcel('/api/v1/master-data/excel/changeover-import', file, replace),
    importParallelOperationExcel: async (file: File, replace = true): Promise<MasterDataImportResult> =>
      importMasterDataExcel('/api/v1/master-data/excel/parallel-operation-import', file, replace),
  },
  businessRuleExcel: {
    downloadTemplate: (kind: string) =>
      downloadBlob(`/api/v1/business-rules/excel/${kind}/template`, `${kind}-template.xlsx`),
    exportRules: (kind: string) =>
      downloadBlob(`/api/v1/business-rules/excel/${kind}/export`, `${kind}-export.xlsx`),
    importRules: async (kind: string, file: File, replace = true): Promise<MasterDataImportResult> =>
      importMasterDataExcel(`/api/v1/business-rules/excel/${kind}/import`, file, replace),
  },
  factoryCalendar: {
    getPolicy: () => request<FactoryCalendarPolicy>('/api/v1/factory-calendar/policy'),
    savePolicy: (dto: FactoryCalendarPolicy) =>
      request<FactoryCalendarPolicy>('/api/v1/factory-calendar/policy', {
        method: 'PUT',
        body: JSON.stringify(dto),
      }),
    getMonth: (year: number, month: number) =>
      request<FactoryCalendarMonth>(`/api/v1/factory-calendar/month?year=${year}&month=${month}`),
    saveDay: (dto: FactoryDayOverrideRequest) =>
      request<FactoryCalendarDay>('/api/v1/factory-calendar/day', {
        method: 'PUT',
        body: JSON.stringify(dto),
      }),
    sync: () =>
      request<FactoryCalendarSyncResult>('/api/v1/factory-calendar/sync', { method: 'POST' }),
  },
};

async function importMasterDataExcel(
  path: string,
  file: File,
  replace = true,
): Promise<MasterDataImportResult> {
  const res = await fetch(`${path}?replace=${replace}`, {
    method: 'POST',
    headers: workspaceHeaders({
      'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
    body: file,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json() as Promise<MasterDataImportResult>;
}
