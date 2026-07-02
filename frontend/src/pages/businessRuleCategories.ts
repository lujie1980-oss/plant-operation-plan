import type { TabConfig } from '../components/MasterDataTabBody';
import type { MasterDataRecord } from '../types/masterData';
import type { MasterDataFocusPage } from '../utils/masterDataFocus';
import { PRODUCTION_RULE_TABS } from './businessRulesTabs';
import { CAPACITY_RULE_TABS, DEMAND_RULE_TABS, LABOR_RULE_TABS, MATERIAL_RULE_TABS } from './masterDataTabConfigs';

export type RuleCategoryId = 'production' | 'capacity' | 'material' | 'labor' | 'demand';

export type PlanningModuleId = 'MOD-OCP' | 'MOD-SCH' | 'MOD-SLT';

export type RuleCategoryDef = {
  id: RuleCategoryId;
  label: string;
  description: string;
};

export const BUSINESS_RULE_CATEGORIES: RuleCategoryDef[] = [
  {
    id: 'production',
    label: '生产规则',
    description: '换型、工序流转/后处理时间、并行与连续生产等工序 lead time 相关规则。',
  },
  {
    id: 'capacity',
    label: '产能规则',
    description: '资源效率系数与细排反馈占用（RULE-SUP-05）；资源日历、负荷与利用率见主数据与计划分析。',
  },
  {
    id: 'material',
    label: '物料规则',
    description: 'BOM 关键件与最长采购周期（含默认 * 行，RULE-MRP-04）。',
  },
  {
    id: 'labor',
    label: '人力规则',
    description: '班次可用人员配置，影响排程人力可行性。',
  },
  {
    id: 'demand',
    label: '需求规则',
    description: '销售订单优先级、加急与排程锁定，影响订单协同计划排序与冻结。',
  },
];

/** 规则分类归属计划模块（非全局 MOD）· §19.4.5 */
export const CATEGORY_PLANNING_MODULE: Record<RuleCategoryId, PlanningModuleId> = {
  demand: 'MOD-OCP',
  capacity: 'MOD-OCP',
  material: 'MOD-OCP',
  production: 'MOD-SCH',
  labor: 'MOD-SCH',
};

export const BUSINESS_RULE_TABS: TabConfig<MasterDataRecord>[] = [
  ...PRODUCTION_RULE_TABS,
  ...MATERIAL_RULE_TABS,
  ...LABOR_RULE_TABS,
  ...DEMAND_RULE_TABS,
  ...CAPACITY_RULE_TABS,
];

const TAB_CATEGORY: Record<string, RuleCategoryId> = {
  changeover: 'production',
  'parallel-operations': 'production',
  'operation-transfer-time': 'production',
  'operation-post-processing': 'production',
  'continuous-production': 'production',
  'routing-step-timing': 'production',
  'routing-step-resource': 'production',
  'bom-rules': 'material',
  'material-lead-time': 'material',
  'supply-quantity-rules': 'material',
  'shift-headcount-rules': 'labor',
  'demand-priority-rules': 'demand',
  'delivery-date-strategy': 'demand',
  'resource-efficiency': 'capacity',
  'scheduler-feedback': 'capacity',
};

export function ruleCategoryId(tabId: string): RuleCategoryId {
  return TAB_CATEGORY[tabId] ?? 'production';
}

export function planningModuleRoutePrefix(moduleId: PlanningModuleId): string {
  if (moduleId === 'MOD-OCP') return '/master-plan/rules';
  if (moduleId === 'MOD-SCH') return '/scheduling/rules';
  return '/slitting/rules';
}

export function categoriesForModule(moduleId: PlanningModuleId): RuleCategoryDef[] {
  return BUSINESS_RULE_CATEGORIES.filter((c) => CATEGORY_PLANNING_MODULE[c.id] === moduleId);
}

export function defaultCategoryIdForModule(moduleId: PlanningModuleId): RuleCategoryId {
  return categoriesForModule(moduleId)[0]?.id ?? 'production';
}

export function rulesRouteForCategory(categoryId: RuleCategoryId): string {
  return `${planningModuleRoutePrefix(CATEGORY_PLANNING_MODULE[categoryId])}/${categoryId}`;
}

export function rulesRouteForTab(tabId: string): string {
  return rulesRouteForCategory(ruleCategoryId(tabId));
}

export function rulesFocusPageForModule(moduleId: PlanningModuleId): MasterDataFocusPage {
  return moduleId === 'MOD-SCH' ? 'scheduling-rules' : 'master-plan-rules';
}

export function rulesFocusPageForTab(tabId: string): MasterDataFocusPage {
  return rulesFocusPageForModule(CATEGORY_PLANNING_MODULE[ruleCategoryId(tabId)]);
}

export function tabsForCategory(categoryId: string): TabConfig<MasterDataRecord>[] {
  return BUSINESS_RULE_TABS.filter((t) => ruleCategoryId(t.id) === categoryId);
}

export function isRuleCategoryId(id: string): id is RuleCategoryId {
  return BUSINESS_RULE_CATEGORIES.some((c) => c.id === id);
}

/** @deprecated use defaultCategoryIdForModule('MOD-OCP') */
export function defaultCategoryId(): RuleCategoryId {
  return 'demand';
}

export function categoryBelongsToModule(categoryId: RuleCategoryId, moduleId: PlanningModuleId): boolean {
  return CATEGORY_PLANNING_MODULE[categoryId] === moduleId;
}
