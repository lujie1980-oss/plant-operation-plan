/** Workspace module catalog — mirrors knowledge/standard/modules/workspace-modules.yaml */

export type WorkspaceModuleCategoryId = 'CAT-INTEGRATION' | 'CAT-PLANNING';

export type WorkspaceModuleCatalogEntry = {
  id: string;
  name: string;
  categoryId: WorkspaceModuleCategoryId;
  defaultEnabled: boolean;
};

export const WORKSPACE_MODULE_CATEGORIES: { id: WorkspaceModuleCategoryId; name: string }[] = [
  { id: 'CAT-INTEGRATION', name: '数据集成' },
  { id: 'CAT-PLANNING', name: '计划模块' },
];

export const WORKSPACE_MODULE_CATALOG: WorkspaceModuleCatalogEntry[] = [
  { id: 'MOD-DI', name: '数据集成', categoryId: 'CAT-INTEGRATION', defaultEnabled: true },
  { id: 'MOD-CAL', name: '工厂日历', categoryId: 'CAT-INTEGRATION', defaultEnabled: true },
  { id: 'MOD-OCP', name: '订单协同计划', categoryId: 'CAT-PLANNING', defaultEnabled: true },
  { id: 'MOD-SCH', name: '作业排程', categoryId: 'CAT-PLANNING', defaultEnabled: true },
  { id: 'MOD-SLT', name: '分切排样', categoryId: 'CAT-PLANNING', defaultEnabled: false },
];

export const ALL_MODULE_IDS = WORKSPACE_MODULE_CATALOG.map((m) => m.id);

export function buildEnabledModuleMap(enabledModuleIds: string[]): Record<string, boolean> {
  const set = new Set(enabledModuleIds);
  const map: Record<string, boolean> = {};
  for (const mod of WORKSPACE_MODULE_CATALOG) {
    map[mod.id] = set.has(mod.id);
  }
  return map;
}
