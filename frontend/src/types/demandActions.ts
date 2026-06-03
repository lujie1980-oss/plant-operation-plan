import type { OrderFulfillmentChain } from './api';
import type { OrderPlanningChain } from './orderPlanningChain';

export type OrderDemandActionId =
  | 'BUILD_UPSTREAM_CHAIN'
  | 'PLAN_UNCONSTRAINED'
  | 'PLAN_FINITE'
  | 'CONFIRM_PROMISE_DATE'
  | 'CANCEL_PLAN';

export interface OrderDemandActionRequest {
  masterPlanVersionId?: string;
  promiseDateOverride?: string | null;
  useFeedbackOverlay?: boolean;
  feedbackCutoff?: string;
}

export interface WorkOrderGenerationResult {
  salesOrderNo: string;
  salesOrderLineNo: number;
  workOrdersCreated: number;
  workOrderNos: string[];
}

export interface OrderDemandActionResult {
  action: OrderDemandActionId;
  message: string;
  fulfillmentChain: OrderFulfillmentChain | null;
  planningChain: OrderPlanningChain | null;
  confirmedPromiseDate: string | null;
  workOrderGeneration: WorkOrderGenerationResult | null;
}

export type DemandChainViewMode = 'fulfillment' | 'planning';
