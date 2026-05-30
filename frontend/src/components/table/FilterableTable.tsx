import { useMemo } from 'react';
import { applyColumnFilters } from './filterRows';
import { TableHead } from './TableHead';
import type { TableColumnDef } from './types';
import { useTableLayout } from './useTableLayout';
import './FilterableTable.css';

export type { TableColumnDef, TableHeadColumn } from './types';

export interface FilterableTableProps<T> {
  tableId: string;
  columns: TableColumnDef<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string;
  emptyText?: string;
  className?: string;
  tableClassName?: string;
  loading?: boolean;
  onRowClick?: (row: T) => void;
  getRowClassName?: (row: T) => string;
  getRowProps?: (row: T) => React.HTMLAttributes<HTMLTableRowElement>;
  wrapClassName?: string;
  cellWrap?: boolean;
}

function defaultFilterText<T>(row: T, col: TableColumnDef<T>): string {
  if (col.getFilterText) {
    return col.getFilterText(row);
  }
  const rendered = col.render(row);
  if (rendered == null || rendered === false) {
    return '';
  }
  if (typeof rendered === 'string' || typeof rendered === 'number') {
    return String(rendered);
  }
  return '';
}

export function FilterableTable<T>({
  tableId,
  columns,
  rows,
  rowKey,
  emptyText = '暂无数据',
  className = '',
  tableClassName = '',
  loading = false,
  onRowClick,
  getRowClassName,
  getRowProps,
  wrapClassName = 'ft-table-wrap',
  cellWrap = false,
}: FilterableTableProps<T>) {
  const headColumns = useMemo(
    () =>
      columns.map((col) => ({
        key: col.key,
        header: col.header,
        width: col.width,
        defaultWidth: col.defaultWidth,
        minWidth: col.minWidth,
        maxWidth: col.maxWidth,
        filterable: col.filterable,
        resizable: col.resizable,
        align: col.align,
        className: col.className,
      })),
    [columns],
  );

  const { filters, setFilter, getColumnWidth, onResizeStart } = useTableLayout(tableId, headColumns);

  const filteredRows = useMemo(
    () =>
      applyColumnFilters(rows, filters, columns, (row, key) => {
        const col = columns.find((c) => c.key === key);
        return col ? defaultFilterText(row, col) : '';
      }),
    [rows, filters, columns],
  );

  const cellClass = cellWrap ? 'ft-cell-wrap' : 'ft-cell';

  return (
    <div className={`${wrapClassName} ${className}`.trim()}>
      <table className={`data-table ft-table ${tableClassName}`.trim()}>
        <thead>
          <TableHead
            columns={headColumns}
            filters={filters}
            setFilter={setFilter}
            getColumnWidth={getColumnWidth}
            onResizeStart={onResizeStart}
          />
        </thead>
        <tbody>
          {filteredRows.map((row, index) => {
            const key = rowKey(row, index);
            const extra = getRowProps?.(row) ?? {};
            const alignClass = (col: TableColumnDef<T>) =>
              col.align === 'right' ? 'ft-align-right' : col.align === 'center' ? 'ft-align-center' : '';
            return (
              <tr
                key={key}
                data-row-key={key}
                className={getRowClassName?.(row)}
                onClick={onRowClick ? () => onRowClick(row) : extra.onClick}
                {...extra}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={`${cellClass} ${alignClass(col)} ${col.className ?? ''}`.trim()}
                    style={{
                      width: getColumnWidth(col),
                      minWidth: getColumnWidth(col),
                      maxWidth: getColumnWidth(col),
                    }}
                  >
                    {col.render(row)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      {filteredRows.length === 0 && (
        <p className="empty md-empty">{loading ? '加载中…' : emptyText}</p>
      )}
    </div>
  );
}
