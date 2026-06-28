import type { ReactNode } from 'react';
import { FilterableTable, type TableColumnDef, type RowRelationLink, type RowViolation } from './table/FilterableTable';
import type { ColumnOption } from './table/useConfigurableColumns';

export interface Column<T> {
  key: string;
  header: string;
  width?: number;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  getFilterText?: (row: T) => string;
  getSortValue?: (row: T) => string | number;
  render: (row: T) => ReactNode;
}

interface DataTableProps<T> {
  tableId?: string;
  columns: Column<T>[];
  rows: T[];
  rowKey?: (row: T, index: number) => string;
  emptyText?: string;
  loading?: boolean;
  onRowClick?: (row: T) => void;
  getRowClassName?: (row: T) => string;
  cellWrap?: boolean;
  enableSort?: boolean;
  unifiedChrome?: boolean;
  getRowViolations?: (row: T) => RowViolation[];
  getRowRelations?: (row: T) => RowRelationLink[];
  entityType?: string;
  columnOptions?: ColumnOption[];
  defaultVisibleKeys?: string[];
}

export function DataTable<T>({
  tableId = 'data-table',
  columns,
  rows,
  rowKey,
  emptyText = '暂无数据',
  loading = false,
  onRowClick,
  getRowClassName,
  cellWrap,
  enableSort,
  unifiedChrome,
  getRowViolations,
  getRowRelations,
  entityType,
  columnOptions,
  defaultVisibleKeys,
}: DataTableProps<T>) {
  const tableColumns: TableColumnDef<T>[] = columns.map((c) => ({
    key: c.key,
    header: c.header,
    width: c.width,
    defaultWidth: c.defaultWidth,
    minWidth: c.minWidth,
    maxWidth: c.maxWidth,
    filterable: c.filterable,
    resizable: c.resizable,
    align: c.align,
    sortable: c.sortable,
    getFilterText: c.getFilterText,
    getSortValue: c.getSortValue,
    render: c.render,
  }));

  return (
    <FilterableTable
      tableId={tableId}
      columns={tableColumns}
      rows={rows}
      rowKey={(row, index) => (rowKey ? rowKey(row, index) : String(index))}
      emptyText={emptyText}
      loading={loading}
      onRowClick={onRowClick}
      getRowClassName={getRowClassName}
      wrapClassName="table-wrap ft-table-wrap"
      cellWrap={cellWrap}
      enableSort={enableSort}
      unifiedChrome={unifiedChrome}
      getRowViolations={getRowViolations}
      getRowRelations={getRowRelations}
      entityType={entityType}
      columnOptions={columnOptions}
      defaultVisibleKeys={defaultVisibleKeys}
    />
  );
}
