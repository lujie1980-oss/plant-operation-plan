import type { MasterPlanCapacityStrategy } from './api';
import type { MasterPlanObjective, MasterPlanObjectiveUpdate } from './masterPlanObjectives';

export interface MasterPlanStrategySummary {
  id: string;
  name: string;
  capacityStrategy: MasterPlanCapacityStrategy;
  isDefault: boolean;
}

export interface MasterPlanStrategyDetail extends MasterPlanStrategySummary {
  objectives: MasterPlanObjective[];
}

export interface MasterPlanStrategyCreate {
  name: string;
  capacityStrategy: MasterPlanCapacityStrategy;
  objectives: MasterPlanObjectiveUpdate[];
}

export interface MasterPlanStrategyUpdate {
  name?: string;
  capacityStrategy?: MasterPlanCapacityStrategy;
  setAsDefault?: boolean;
  objectives?: MasterPlanObjectiveUpdate[];
}

export const CAPACITY_STRATEGY_LABELS: Record<MasterPlanCapacityStrategy, string> = {
  UNCONSTRAINED: '无产能约束（无限产能）',
  FINITE_CAPACITY: '有限产能（跨天拆段）',
};
