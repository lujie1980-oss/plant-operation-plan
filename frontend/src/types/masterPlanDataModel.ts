export interface ProductInStockingPointNode {
  pispId: string;
  productCode: string;
  productName: string;
  stockingPointId: string;
  bomTierFromTop: number;
  bomTierLabel: string;
  hasRouting: boolean;
}

export interface StockingPointNode {
  id: string;
  stockingPointCode: string;
  displayName: string;
  pisps: ProductInStockingPointNode[];
}

export interface MasterPlanDataModelTree {
  stockingPoints: StockingPointNode[];
}

export interface RoutingSummary {
  id: string;
  pispId: string;
  productCode: string;
  routingName: string;
  stepCount: number;
}

export interface RoutingStepOnStandardResource {
  id: string;
  routingStepId: string;
  standardResourceId: string;
  resourcePriority: number | null;
  setupTimeMinutes: number;
  processTimeSeconds: number | null;
}

export interface RoutingStepInputMaterial {
  id: string;
  routingStepId: string;
  componentProductCode: string;
  componentQtyPer: number;
  critical: boolean;
}

export interface RoutingStepOutputMaterial {
  id: string;
  routingStepId: string;
  outputProductCode: string;
  outputQtyPer: number;
}

export interface RoutingStepDetail {
  id: string;
  routingId: string;
  sequenceNo: number;
  operationName: string;
  standardResources: RoutingStepOnStandardResource[];
  inputMaterials: RoutingStepInputMaterial[];
  outputMaterials: RoutingStepOutputMaterial[];
}

export interface MasterPlanPispRoutingDetail {
  pisp: ProductInStockingPointNode;
  routing: RoutingSummary | null;
  steps: RoutingStepDetail[];
}
