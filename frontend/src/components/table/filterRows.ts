import type { TableColumnDef } from './types';

export function applyColumnFilters<T>(
  rows: T[],
  filters: Record<string, string>,
  columns: Array<Pick<TableColumnDef<T>, 'key' | 'getFilterText'>>,
  getDefaultFilterText: (row: T, key: string) => string,
): T[] {
  const active = Object.entries(filters).filter(([, value]) => value.trim().length > 0);
  if (active.length === 0) {
    return rows;
  }

  return rows.filter((row) =>
    active.every(([key, query]) => {
      const col = columns.find((c) => c.key === key);
      const text = col?.getFilterText?.(row) ?? getDefaultFilterText(row, key);
      return text.toLowerCase().includes(query.trim().toLowerCase());
    }),
  );
}
