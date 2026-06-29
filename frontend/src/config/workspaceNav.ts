import {
  ALL_MODULE_IDS,
  WORKSPACE_MODULE_CATALOG,
} from './workspaceModules';

export type NavLinkItem = { to: string; label: string; end?: boolean; moduleId?: string };

export type NavSubGroup = {
  id: string;
  label: string;
  items: NavLinkItem[];
};

export type NavGroup = {
  id: string;
  label: string;
  categoryId: 'CAT-INTEGRATION' | 'CAT-PLANNING';
  moduleIds: string[];
  items: NavLinkItem[];
  subGroups?: NavSubGroup[];
};

export const TOP_NAV: NavLinkItem[] = [{ to: '/', label: '首页', end: true }];

/** MOD-DI — §19.2.2 */
export const INTEGRATION_GROUP: NavGroup = {
  id: 'integration',
  label: '数据集成',
  categoryId: 'CAT-INTEGRATION',
  moduleIds: ['MOD-DI', 'MOD-CAL'],
  items: [
    { to: '/integration', label: '集成概览', end: true, moduleId: 'MOD-DI' },
    { to: '/integration/external/master', label: 'External 主数据', moduleId: 'MOD-DI' },
    { to: '/integration/external/transactional', label: 'External 交易', moduleId: 'MOD-DI' },
    { to: '/integration/adapters', label: '适配器', moduleId: 'MOD-DI' },
    { to: '/integration/quality', label: '质检报告', moduleId: 'MOD-DI' },
    { to: '/master-data', label: '主数据（internal）', end: true, moduleId: 'MOD-DI' },
    { to: '/business-data', label: '业务数据（internal）', end: true, moduleId: 'MOD-DI' },
  ],
  subGroups: [
    {
      id: 'factory-calendar',
      label: '工厂日历',
      items: [
        { to: '/factory-calendar', label: '日历维护', end: true, moduleId: 'MOD-CAL' },
      ],
    },
  ],
};

export const MASTER_PLAN_GROUP: NavGroup = {
  id: 'master-plan',
  label: '订单协同计划',
  categoryId: 'CAT-PLANNING',
  moduleIds: ['MOD-OCP'],
  items: [
    { to: '/master-plan/parameters', label: '计划参数', moduleId: 'MOD-OCP' },
    { to: '/master-plan/objectives', label: '优化目标', moduleId: 'MOD-OCP' },
    { to: '/master-plan/plan-run', label: '计划运行', moduleId: 'MOD-OCP' },
    { to: '/master-plan/ontology', label: '本体推演', moduleId: 'MOD-OCP' },
    { to: '/master-plan/data-model', label: '数据模型', moduleId: 'MOD-OCP' },
    { to: '/master-plan/scenario-comparison', label: '场景对比', moduleId: 'MOD-OCP' },
  ],
  subGroups: [
    {
      id: 'mp-rules',
      label: '业务规则',
      items: [
        { to: '/master-plan/rules/demand', label: '需求规则', moduleId: 'MOD-OCP' },
        { to: '/master-plan/rules/capacity', label: '产能规则', moduleId: 'MOD-OCP' },
        { to: '/master-plan/rules/material', label: '物料规则', moduleId: 'MOD-OCP' },
      ],
    },
    {
      id: 'plan-analysis',
      label: '计划分析',
      items: [
        { to: '/master-plan/analysis/demand', label: '需求满足', moduleId: 'MOD-OCP' },
        { to: '/master-plan/analysis/capacity', label: '产能平衡', moduleId: 'MOD-OCP' },
        { to: '/master-plan/analysis/material-planning', label: '物料计划', moduleId: 'MOD-OCP' },
        { to: '/master-plan/analysis/work-orders', label: '生产工单', moduleId: 'MOD-OCP' },
      ],
    },
  ],
};

export const SCHEDULING_GROUP: NavGroup = {
  id: 'scheduling',
  label: '作业排程',
  categoryId: 'CAT-PLANNING',
  moduleIds: ['MOD-SCH'],
  items: [
    { to: '/scheduling/parameters', label: '计划参数', moduleId: 'MOD-SCH' },
    { to: '/scheduling/pending-work-orders', label: '待排工单', moduleId: 'MOD-SCH' },
    { to: '/scheduling/batch-plan', label: '批次计划', moduleId: 'MOD-SCH' },
    { to: '/scheduling/kitting', label: '物料齐套', moduleId: 'MOD-SCH' },
    { to: '/scheduling/detail-schedule', label: '生产排程', moduleId: 'MOD-SCH' },
    { to: '/scheduling/version-comparison', label: '版本对比', moduleId: 'MOD-SCH' },
  ],
  subGroups: [
    {
      id: 'sch-rules',
      label: '业务规则',
      items: [
        { to: '/scheduling/rules/production', label: '生产规则', moduleId: 'MOD-SCH' },
        { to: '/scheduling/rules/labor', label: '人力规则', moduleId: 'MOD-SCH' },
      ],
    },
  ],
};

export const SLITTING_GROUP: NavGroup = {
  id: 'slitting',
  label: '分切排样',
  categoryId: 'CAT-PLANNING',
  moduleIds: ['MOD-SLT'],
  items: [
    { to: '/slitting/master-data', label: '基础数据', moduleId: 'MOD-SLT' },
    { to: '/slitting/parameters', label: '优化参数', moduleId: 'MOD-SLT' },
    { to: '/slitting/runs', label: '优化运行', moduleId: 'MOD-SLT' },
    { to: '/slitting/studio', label: '母卷分切', moduleId: 'MOD-SLT' },
  ],
};

/** Ordered sidebar groups — matches §17.2.1 */
export const ALL_NAV_GROUPS: NavGroup[] = [
  INTEGRATION_GROUP,
  MASTER_PLAN_GROUP,
  SCHEDULING_GROUP,
  SLITTING_GROUP,
];

/** Default module enablement when IAM data is unavailable. */
export const DEFAULT_ENABLED_MODULES: Record<string, boolean> = Object.fromEntries(
  WORKSPACE_MODULE_CATALOG.map((m) => [m.id, m.defaultEnabled]),
);

export { ALL_MODULE_IDS };

export function isModuleEnabled(moduleId: string, enabledModules?: Record<string, boolean>): boolean {
  const map = enabledModules ?? DEFAULT_ENABLED_MODULES;
  return map[moduleId] === true;
}

export function filterNavGroups(enabledModules?: Record<string, boolean>): NavGroup[] {
  return ALL_NAV_GROUPS.filter((group) =>
    group.moduleIds.some((id) => isModuleEnabled(id, enabledModules)),
  ).map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.moduleId || isModuleEnabled(item.moduleId, enabledModules)),
    subGroups: group.subGroups
      ?.map((sub) => ({
        ...sub,
        items: sub.items.filter((item) => !item.moduleId || isModuleEnabled(item.moduleId, enabledModules)),
      }))
      .filter((sub) => sub.items.length > 0),
  }));
}

export function pathMatchesItem(pathname: string, item: NavLinkItem) {
  return pathname === item.to || pathname.startsWith(`${item.to}/`);
}

export function subGroupActive(sub: NavSubGroup, pathname: string) {
  return sub.items.some((item) => pathMatchesItem(pathname, item));
}

export function groupActive(group: NavGroup, pathname: string) {
  if (group.subGroups?.some((sub) => subGroupActive(sub, pathname))) {
    return true;
  }
  return group.items.some((item) => pathMatchesItem(pathname, item));
}

export function expandForPath(pathname: string, groups: NavGroup[], prev: Record<string, boolean>) {
  const next = { ...prev };
  for (const g of groups) {
    if (groupActive(g, pathname)) {
      next[g.id] = true;
    }
    for (const sub of g.subGroups ?? []) {
      if (subGroupActive(sub, pathname)) {
        next[g.id] = true;
        next[sub.id] = true;
      }
    }
  }
  return next;
}
