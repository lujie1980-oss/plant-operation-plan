import type { ReactNode } from 'react';
import { FilterableTable, type TableColumnDef } from './table/FilterableTable';

export interface Column<T> {
  key: string;
  header: string;
  width?: number;
  defaultWidth?: number;
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  getFilterText?: (row: T) => string;
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
}: DataTableProps<T>) {
  const tableColumns: TableColumnDef<T>[] = columns.map((c) => ({
    key: c.key,
    header: c.header,
    width: c.width,
    defaultWidth: c.defaultWidth,
    filterable: c.filterable,
    resizable: c.resizable,
    align: c.align,
    getFilterText: c.getFilterText,
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
    />
  );
}
