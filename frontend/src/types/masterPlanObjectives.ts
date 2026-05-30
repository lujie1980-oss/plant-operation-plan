export interface MasterPlanObjective {
  id: string;
  name: string;
  description: string;
  penaltyUnit: string;
  enabled: boolean;
  weight: number;
  defaultWeight: number;
}

export interface MasterPlanObjectiveUpdate {
  id: string;
  enabled: boolean;
  weight: number;
}
