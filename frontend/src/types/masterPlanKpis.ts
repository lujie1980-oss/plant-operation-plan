/** §15 KPI-MP-TOT / KPI-MP-B01~B10 — mirrors MasterPlanKpiDtos.java */

export interface KpiDomainScore {
  domain: string;
  hard: number;
  soft: number;
}

export interface KpiBreakdownItem {
  kpiId: string;
  name: string;
  constraintId: string;
  hard: number;
  soft: number;
}

export interface KpiBreakdown {
  delivery: KpiDomainScore;
  material: KpiDomainScore;
  capacity: KpiDomainScore;
  supply: KpiDomainScore;
  preference: KpiDomainScore;
  scoring: KpiBreakdownItem[];
  constraint: KpiBreakdownItem[];
}

export interface MasterPlanBusinessKpi {
  kpiId: string;
  name: string;
  value: number;
  unit: string;
  severity: 'ok' | 'warn' | 'danger' | 'info' | string;
}

export interface MasterPlanKpisResponse {
  planVersionId: string;
  totalKpi: number | null;
  scoreSummary: string | null;
  kpiBreakdown: KpiBreakdown | null;
  businessKpis: MasterPlanBusinessKpi[];
}
