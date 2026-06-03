export interface PlanningSignal {
  severity: 'INFO' | 'WARN' | 'SKIP' | string;
  reasonCode: string;
  message: string;
  entityId: string | null;
}

export interface OrderPlanningChainNode {
  nodeId: string;
  nodeType: string;
  laneId: string;
  label: string;
  status: string;
  depth: number;
  productCode: string;
  quantity: number;
  windowStart: string | null;
  windowEnd: string | null;
  planningLayer: string;
  planningSignals: PlanningSignal[];
  attributes: Record<string, unknown>;
  operations: import('./api').FulfillmentOperation[];
}

export interface OrderPlanningChain {
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  dueDate: string;
  promiseDate: string | null;
  overallStatus: string;
  kittingStatus: string;
  summary: {
    capacityStrategy: string | null;
    inventorySnapshotId: string | null;
    workOrderCount: number;
    operationCount: number;
    issueCountBySeverity: Record<string, number>;
    computedAt: string;
  };
  nodes: OrderPlanningChainNode[];
  edges: import('./api').FulfillmentPegEdge[];
  compare: {
    baselineVersionId: string;
    nodeDeltas: Array<{
      nodeId: string;
      baselineStart: string | null;
      baselineEnd: string | null;
      trialStart: string | null;
      trialEnd: string | null;
      statusChanged: boolean;
    }>;
  } | null;
}

export interface OrderPlanningChainPreviewRequest {
  salesOrderNo: string;
  salesOrderLineNo: number;
  masterPlanStrategyId?: string;
  useFeedbackOverlay?: boolean;
  feedbackCutoff?: string;
  detailScheduleMasterPlanVersionId?: string;
  baselineMasterPlanVersionId?: string;
}
