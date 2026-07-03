import { useCallback, useMemo, useState } from 'react';

function storageKey(tableId: string): string {
  return `tbl-cols:${tableId}`;
}

function loadVisible(tableId: string, defaultKeys: string[]): string[] {
  try {
    const raw = localStorage.getItem(storageKey(tableId));
    if (!raw) return defaultKeys;
    const parsed = JSON.parse(raw) as string[];
    if (!Array.isArray(parsed) || parsed.length === 0) return defaultKeys;
    return parsed;
  } catch {
    return defaultKeys;
  }
}

function saveVisible(tableId: string, keys: string[]): void {
  try {
    localStorage.setItem(storageKey(tableId), JSON.stringify(keys));
  } catch {
    /* ignore */
  }
}

export type ColumnOption = {
  key: string;
  label: string;
  required?: boolean;
};

export function useConfigurableColumns(tableId: string, options: ColumnOption[], defaultVisible: string[]) {
  const allKeys = useMemo(() => options.map((o) => o.key), [options]);
  const [visibleKeys, setVisibleKeys] = useState<string[]>(() => loadVisible(tableId, defaultVisible));

  const visibleSet = useMemo(() => new Set(visibleKeys), [visibleKeys]);

  const toggleColumn = useCallback(
    (key: string) => {
      const opt = options.find((o) => o.key === key);
      if (opt?.required) return;
      setVisibleKeys((prev) => {
        const has = prev.includes(key);
        const next = has ? prev.filter((k) => k !== key) : [...prev, key];
        const ordered = allKeys.filter((k) => next.includes(k));
        if (ordered.length === 0) return prev;
        saveVisible(tableId, ordered);
        return ordered;
      });
    },
    [allKeys, options, tableId],
  );

  const resetColumns = useCallback(() => {
    const ordered = allKeys.filter((k) => defaultVisible.includes(k));
    setVisibleKeys(ordered);
    saveVisible(tableId, ordered);
  }, [allKeys, defaultVisible, tableId]);

  return { visibleKeys, visibleSet, toggleColumn, resetColumns, allKeys };
}
