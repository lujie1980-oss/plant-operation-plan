import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { TableHeadColumn } from './types';
import { useRegisterTableFilters } from './useRegisterTableFilters';

const DEFAULT_WIDTH = 120;
const MIN_COL_WIDTH = 56;

function storageKey(tableId: string): string {
  return `tbl-layout:${tableId}`;
}

function loadWidths(tableId: string, columns: TableHeadColumn[]): Record<string, number> {
  const defaults: Record<string, number> = {};
  for (const col of columns) {
    defaults[col.key] = col.width ?? col.defaultWidth ?? DEFAULT_WIDTH;
  }
  try {
    const raw = localStorage.getItem(storageKey(tableId));
    if (!raw) return defaults;
    const parsed = JSON.parse(raw) as Record<string, number>;
    return { ...defaults, ...parsed };
  } catch {
    return defaults;
  }
}

function saveWidths(tableId: string, widths: Record<string, number>): void {
  try {
    localStorage.setItem(storageKey(tableId), JSON.stringify(widths));
  } catch {
    /* ignore */
  }
}

export function useTableLayout(tableId: string, columns: TableHeadColumn[]) {
  const columnKeys = columns.map((c) => c.key).join('|');
  const [widths, setWidths] = useState<Record<string, number>>(() => loadWidths(tableId, columns));
  const [filters, setFilters] = useState<Record<string, string>>({});
  const widthsRef = useRef(widths);

  useEffect(() => {
    widthsRef.current = widths;
  }, [widths]);

  useEffect(() => {
    setWidths(loadWidths(tableId, columns));
    setFilters({});
  }, [tableId, columnKeys]);

  const getColumnWidth = useCallback(
    (col: TableHeadColumn): number => {
      const min = col.minWidth ?? MIN_COL_WIDTH;
      const max = col.maxWidth ?? 640;
      const base = widths[col.key] ?? col.width ?? col.defaultWidth ?? DEFAULT_WIDTH;
      return Math.min(max, Math.max(min, base));
    },
    [widths],
  );

  const setFilter = useCallback((key: string, value: string) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }, []);

  const clearFilters = useCallback(() => {
    setFilters({});
  }, []);

  const onResizeStart = useCallback(
    (key: string, event: React.MouseEvent) => {
      event.preventDefault();
      event.stopPropagation();
      const col = columns.find((c) => c.key === key);
      if (!col || col.resizable === false) {
        return;
      }
      const startX = event.clientX;
      const startWidth = getColumnWidth(col);

      const onMove = (ev: MouseEvent) => {
        const delta = ev.clientX - startX;
        const min = col.minWidth ?? MIN_COL_WIDTH;
        const max = col.maxWidth ?? 640;
        const next = Math.min(max, Math.max(min, startWidth + delta));
        setWidths((prev) => {
          const updated = { ...prev, [key]: next };
          widthsRef.current = updated;
          return updated;
        });
      };

      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        saveWidths(tableId, widthsRef.current);
      };

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    [columns, getColumnWidth, tableId],
  );

  const hasActiveFilters = Object.values(filters).some((v) => v.trim().length > 0);

  const filterableKeys = useMemo(
    () =>
      columns
        .filter((col) => col.filterable !== false && col.header.trim().length > 0)
        .map((col) => col.key),
    [columns],
  );

  useRegisterTableFilters(tableId, filters, setFilter, filterableKeys);

  return {
    filters,
    setFilter,
    clearFilters,
    hasActiveFilters,
    getColumnWidth,
    onResizeStart,
  };
}
