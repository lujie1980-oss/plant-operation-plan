import type { TabConfig } from '../components/MasterDataTabBody';

import type { MasterDataRecord } from '../types/masterData';

import { PRODUCTION_RULE_TABS } from './businessRulesTabs';

import { DEMAND_RULE_TABS, LABOR_RULE_TABS, MATERIAL_RULE_TABS } from './masterDataTabConfigs';



export type RuleCategoryId = 'production' | 'capacity' | 'material' | 'labor' | 'demand';



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

    description: '资源日历、负荷与产能约束相关规则（利用率由主数据日历与产能分析计算）。',

  },

  {

    id: 'material',

    label: '物料规则',

    description: 'BOM 关键件标记，影响齐套判定与物料可行性。',

  },

  {

    id: 'labor',

    label: '人力规则',

    description: '班次可用人员配置，影响排程人力可行性。',

  },

  {

    id: 'demand',

    label: '需求规则',

    description: '销售订单优先级、加急与排程锁定，影响主计划排序与冻结。',

  },

];



export const BUSINESS_RULE_TABS: TabConfig<MasterDataRecord>[] = [

  ...PRODUCTION_RULE_TABS,

  ...MATERIAL_RULE_TABS,

  ...LABOR_RULE_TABS,

  ...DEMAND_RULE_TABS,

];



const TAB_CATEGORY: Record<string, RuleCategoryId> = {

  changeover: 'production',

  'parallel-operations': 'production',

  'operation-transfer-time': 'production',

  'operation-post-processing': 'production',

  'continuous-production': 'production',

  'bom-rules': 'material',

  'material-lead-time': 'material',

  'shift-headcount-rules': 'labor',

  'demand-priority-rules': 'demand',

};



export function ruleCategoryId(tabId: string): RuleCategoryId {

  return TAB_CATEGORY[tabId] ?? 'production';

}



export function tabsForCategory(categoryId: string): TabConfig<MasterDataRecord>[] {

  return BUSINESS_RULE_TABS.filter((t) => ruleCategoryId(t.id) === categoryId);

}



export function isRuleCategoryId(id: string): id is RuleCategoryId {

  return BUSINESS_RULE_CATEGORIES.some((c) => c.id === id);

}



export function defaultCategoryId(): RuleCategoryId {

  return 'production';

}

