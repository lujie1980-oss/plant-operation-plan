export type RollNodeType = 'MASTER' | 'INTERMEDIATE' | 'CHILD';

export interface MasterRoll {
  rollCode: string;
  widthMm: number;
  lengthMm: number;
  thicknessMm?: number;
  materialCode?: string;
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
  status: string;
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
