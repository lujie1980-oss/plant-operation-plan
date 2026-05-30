export interface PlanningConstraintMatch {
  identification: string;
  hardScore: number;
  softScore: number;
  indictedIds: string[];
}

export interface PlanningConstraintMatchTotal {
  constraintId: string;
  constraintPackage: string;
  constraintName: string;
  hardScore: number;
  softScore: number;
  matchCount: number;
  sampleMatches: PlanningConstraintMatch[];
  sampleTruncated: boolean;
}

export interface PlanningScoreExplanation {
  computedAt: string;
  planVersionId: string;
  planType: 'MASTER_PLAN' | 'DETAIL_SCHEDULE';
  masterPlanVersionId: string | null;
  score: string;
  hardScore: number;
  softScore: number;
  summary: string;
  constraintTotals: PlanningConstraintMatchTotal[];
  matchesTruncated: boolean;
}

export type PlanningScoreLayer = 'master-plan' | 'detail-schedule';
