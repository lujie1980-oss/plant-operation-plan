export type RollNodeType = 'MASTER' | 'INTERMEDIATE' | 'CHILD';

export interface MasterRoll {
  rollCode: string;
  widthMm: number;
  lengthMm: number;
  thicknessMm?: number;
  materialCode?: string;
  productCode?: string;
  finishedProductCode?: string;
  kerfLongitudinalMm: number;
  kerfTransverseMm: number;
  status: string;
}

export interface ChildSlittingOrder {
  orderCode: string;
  widthMm: number;
  lengthMm: number;
  thicknessMm?: number;
  quantity: number;
  priority: number;
  salesOrderNo?: string;
  salesOrderLineNo?: number;
  workOrderNo?: string;
  productCode?: string;
  finishedProductCode?: string;
  status: string;
}

export interface SlittingBomScope {
  scopeId: string;
  scopeType: string;
  label: string;
  finishedProductCode: string;
  productCode?: string;
}

export interface SlittingMaterialDemand {
  demandType: string;
  demandId: string;
  label: string;
  productCode?: string;
  finishedProductCode?: string;
  quantity?: number;
  salesOrderNo?: string;
  salesOrderLineNo?: number;
  relation?: string;
}

export interface IntermediateRollCatalog {
  specCode: string;
  widthMm: number;
  lengthMm: number;
  cuttingMethod: string;
  kerfMm: number;
  active: boolean;
}

export interface SlittingPlanSummary {
  planVersionId: string;
  name: string;
  status: string;
  score?: string;
  utilizationPct?: number;
  solveDurationMs?: number;
  solverPhase?: string;
}

export interface SlittingRollNode {
  nodeId: string;
  nodeType: RollNodeType;
  parentNodeId: string | null;
  widthMm: number;
  lengthMm: number;
  thicknessMm?: number;
  cuttingMethod?: string;
  sourceSpecCode?: string;
}

export interface SlittingAssignment {
  assignmentId: string;
  childNodeId: string;
  parentNodeId: string;
  posXMm: number;
  posYMm: number;
  rotated: boolean;
  pinned?: boolean;
  sequence?: number;
}

export interface SlittingPlanTree {
  planVersionId: string;
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  utilizationPct?: number;
}

export interface CreateSlittingPlanRequest {
  name: string;
  masterRollCodes: string[];
  childOrderCodes: string[];
}

export interface SlittingAssignmentPatch {
  assignmentId: string;
  posXMm?: number;
  posYMm?: number;
  rotated?: boolean;
  pinned?: boolean;
}

export interface SlittingSession {
  sessionId: string;
  planVersionId: string;
  activeParentNodeId: string | null;
  score?: string;
  lastOptimizeMs?: number;
  utilizationPct?: number;
  assignments: SlittingAssignment[];
}

export interface ImportChildOrdersFromDemandRequest {
  salesOrderNos?: string[];
  defaultWidthMm?: number;
  defaultLengthMm?: number;
  skipExisting?: boolean;
}

export interface ImportChildOrdersFromDemandResult {
  created: number;
  skipped: number;
  orders: ChildSlittingOrder[];
}

export interface SlittingSolverRunLogLine {
  timestamp: string;
  level: string;
  message: string;
}

export interface SlittingSolverRun {
  runId: string;
  runType: string;
  planVersionId: string | null;
  masterNodeId: string | null;
  sessionId: string | null;
  status: string;
  startedTs: string | null;
  finishedTs: string | null;
  durationMs: number | null;
  score: string | null;
  summary: string | null;
  errorMessage: string | null;
  executionLog: SlittingSolverRunLogLine[];
}

export const SLITTING_RUN_TYPE_LABELS: Record<string, string> = {
  PLAN_SOLVE: '整方案求解',
  STUDIO_OPTIMIZE: '工作台母卷优化',
  SESSION_OPTIMIZE: '会话局部优化',
};
