/** Preset adapters — mirrors knowledge/standard/modules/integration-adapters.yaml */
export type AdapterCatalogEntry = {
  id: string;
  name: string;
  type: 'ERP' | 'MES' | 'FILE';
  phase: number;
  routeSlug: string;
  description: string;
};

export const ADAPTER_CATALOG: AdapterCatalogEntry[] = [
  {
    id: 'ADP-ERP-SAP',
    name: 'ERP 适配器（SAP）',
    type: 'ERP',
    phase: 1,
    routeSlug: 'erp-sap',
    description: '对接 SAP ERP；主数据与交易写入 external_* staging',
  },
  {
    id: 'ADP-MES',
    name: 'MES 适配器',
    type: 'MES',
    phase: 1,
    routeSlug: 'mes',
    description: '工单反馈、SchedulerFeedback 等写入 external_*',
  },
  {
    id: 'ADP-EXCEL',
    name: 'Excel 数据适配器',
    type: 'FILE',
    phase: 1,
    routeSlug: 'excel',
    description: '按 External 列模板从 Excel 导入',
  },
];

export function adapterBySlug(slug: string): AdapterCatalogEntry | undefined {
  return ADAPTER_CATALOG.find((a) => a.routeSlug === slug);
}
