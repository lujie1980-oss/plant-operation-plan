import type { ChildSlittingOrder, MasterRoll, SlittingAssignment, SlittingRollNode } from './slitting';

/** 横向 = 沿长度(X)分切；纵向 = 沿宽度(Y)分切 */
export type SlitDirection = 'horizontal' | 'vertical';

/** 订单放入区域时的纹路方向 */
export type OrderOrientation = 'horizontal' | 'vertical';

export interface StudioMasterRef extends MasterRoll {}

export interface StudioOrderRef extends ChildSlittingOrder {
  placedQty?: number;
}

export interface StudioState {
  planVersionId: string | null;
  planName: string;
  masters: StudioMasterRef[];
  orders: StudioOrderRef[];
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  focusNodeId: string | null;
  selectedNodeId: string | null;
  usedMasterCodes: Set<string>;
  placedOrderCodes: Set<string>;
}

export type CreateRegionInput = {
  targetNodeId: string;
  direction: SlitDirection;
  cutSizeMm: number;
  mode?: 'split' | 'full';
};

export type PlaceOrderInput = {
  regionNodeId: string;
  orderCode: string;
  orientation: OrderOrientation;
};
