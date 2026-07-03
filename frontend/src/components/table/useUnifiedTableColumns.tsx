import { useMemo } from 'react';
import { ConstraintViolationCell } from './ConstraintViolationCell';
import { TableRowMenu } from './TableRowMenu';
import type { ColumnOption } from './useConfigurableColumns';
import { useConfigurableColumns } from './useConfigurableColumns';
import type { RowRelationLink, RowViolation, TableColumnDef } from './types';
import { UNIFIED_COL_ACTIONS, UNIFIED_COL_VIOLATIONS, VIOLATION_HEADER_ARIA } from './types';
import { ViolationColumnHeader } from './ViolationColumnHeader';

const CHROME_KEYS = new Set([UNIFIED_COL_ACTIONS, UNIFIED_COL_VIOLATIONS]);

function isDataColumn<T>(col: TableColumnDef<T>) {
  return !CHROME_KEYS.has(col.key);
}

function withDefaults<T>(col: TableColumnDef<T>): TableColumnDef<T> {
  if (!isDataColumn(col)) return col;
  return {
    filterable: col.filterable !== false,
    sortable: col.sortable !== false,
    resizable: col.resizable !== false,
    ...col,
  };
}

type Options<T> = {
  tableId: string;
  columns: TableColumnDef<T>[];
  unifiedChrome: boolean;
  columnOptions?: ColumnOption[];
  defaultVisibleKeys?: string[];
  getRowViolations?: (row: T) => RowViolation[];
  getRowRelations?: (row: T) => RowRelationLink[];
};

export function useUnifiedTableColumns<T>({
  tableId,
  columns,
  unifiedChrome,
  columnOptions,
  defaultVisibleKeys,
  getRowViolations,
  getRowRelations,
}: Options<T>) {
  const dataInput = useMemo(() => columns.filter(isDataColumn).map(withDefaults), [columns]);

  const useColumnPicker = Boolean(columnOptions?.length && defaultVisibleKeys?.length);
  const configurable = useConfigurableColumns(
    tableId,
    columnOptions ?? [],
    defaultVisibleKeys ?? columnOptions?.map((o) => o.key) ?? [],
  );

  const visibleDataColumns = useMemo(() => {
    if (!useColumnPicker) return dataInput;
    const keys = new Set(configurable.visibleKeys);
    return dataInput.filter((c) => keys.has(c.key));
  }, [dataInput, useColumnPicker, configurable.visibleKeys]);

  const displayColumns = useMemo((): TableColumnDef<T>[] => {
    if (!unifiedChrome) return visibleDataColumns;

    const chrome: TableColumnDef<T>[] = [
      {
        key: UNIFIED_COL_ACTIONS,
        header: '…',
        width: 36,
        defaultWidth: 36,
        minWidth: 32,
        maxWidth: 40,
        filterable: false,
        sortable: false,
        resizable: true,
        className: 'ft-td-unified',
        render: (row) => (
          <TableRowMenu
            columnOptions={useColumnPicker ? columnOptions : undefined}
            visibleSet={useColumnPicker ? configurable.visibleSet : undefined}
            onToggleColumn={useColumnPicker ? configurable.toggleColumn : undefined}
            onResetColumns={useColumnPicker ? configurable.resetColumns : undefined}
            relations={getRowRelations?.(row) ?? []}
          />
        ),
      },
      {
        key: UNIFIED_COL_VIOLATIONS,
        header: '',
        headerNode: <ViolationColumnHeader />,
        ariaLabel: VIOLATION_HEADER_ARIA,
        defaultWidth: 52,
        minWidth: 40,
        maxWidth: 120,
        filterable: false,
        sortable: false,
        resizable: true,
        className: 'ft-td-unified',
        render: (row) => (
          <ConstraintViolationCell violations={getRowViolations?.(row) ?? []} />
        ),
        getFilterText: (row) => {
          const v = getRowViolations?.(row) ?? [];
          return v.map((x) => x.message).join(' ');
        },
      },
    ];

    return [...chrome, ...visibleDataColumns];
  }, [
    unifiedChrome,
    visibleDataColumns,
    useColumnPicker,
    columnOptions,
    configurable.visibleSet,
    configurable.toggleColumn,
    configurable.resetColumns,
    getRowRelations,
    getRowViolations,
  ]);

  return {
    displayColumns,
    columnOptions: useColumnPicker ? columnOptions : undefined,
    visibleSet: useColumnPicker ? configurable.visibleSet : undefined,
    toggleColumn: useColumnPicker ? configurable.toggleColumn : undefined,
    resetColumns: useColumnPicker ? configurable.resetColumns : undefined,
  };
}
