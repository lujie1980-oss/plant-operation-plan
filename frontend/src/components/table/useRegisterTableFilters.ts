import { useEffect, useRef } from 'react';
import { registerTableFilters, unregisterTableFilters } from './tableFilterRegistry';

export function useRegisterTableFilters(
  tableId: string,
  filters: Record<string, string>,
  setFilter: (key: string, value: string) => void,
  filterableKeys: readonly string[],
): void {
  const filtersRef = useRef(filters);
  filtersRef.current = filters;
  const setFilterRef = useRef(setFilter);
  setFilterRef.current = setFilter;
  const filterableRef = useRef(filterableKeys);
  filterableRef.current = filterableKeys;

  useEffect(() => {
    registerTableFilters(tableId, {
      getFilter: (key) => filtersRef.current[key] ?? '',
      setFilter: (key, value) => setFilterRef.current(key, value),
      clearFilter: (key) => setFilterRef.current(key, ''),
      isFilterable: (key) => filterableRef.current.includes(key),
    });
    return () => unregisterTableFilters(tableId);
  }, [tableId]);
}
