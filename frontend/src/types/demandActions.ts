import type { OrderFulfillmentChain } from './api';

export type OrderDemandActionId =
  | 'INFINITE_PLAN_JIT'
  | 'FINITE_PLAN'
  | 'CONFIRM_PROMISE_DATE'
  | 'CANCEL_PLAN'
  /** @deprecated */
  | 'BUILD_UPSTREAM_CHAIN'
  /** @deprecated */
  | 'PLAN_UNCONSTRAINED'
  /** @deprecated */
  | 'PLAN_FINITE';

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
  confirmedPromiseDate: string | null;
  workOrderGeneration: WorkOrderGenerationResult | null;
}

export interface PromiseDatePreview {
  fulfillmentChain: OrderFulfillmentChain;
  suggestedPromiseDate: string | null;
  overallStatus: string;
}
