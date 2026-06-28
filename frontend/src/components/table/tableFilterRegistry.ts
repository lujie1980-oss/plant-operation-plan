export type TableFilterRegistryApi = {
  getFilter: (columnKey: string) => string;
  setFilter: (columnKey: string, value: string) => void;
  clearFilter: (columnKey: string) => void;
  isFilterable: (columnKey: string) => boolean;
};

const registry = new Map<string, TableFilterRegistryApi>();

export function registerTableFilters(tableId: string, api: TableFilterRegistryApi): void {
  registry.set(tableId, api);
}

export function unregisterTableFilters(tableId: string): void {
  registry.delete(tableId);
}

export function getTableFilters(tableId: string): TableFilterRegistryApi | undefined {
  return registry.get(tableId);
}
