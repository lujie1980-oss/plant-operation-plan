import { useCallback, useMemo, useState } from 'react';
import { getTableRelations } from '../../utils/tableRelationRegistry';
import { applyColumnFilters } from './filterRows';
import { TableHead } from './TableHead';
import type { ColumnOption } from './useConfigurableColumns';
import { useUnifiedTableColumns } from './useUnifiedTableColumns';
import type { RowRelationLink, RowViolation, TableColumnDef, TableSortState } from './types';
import { UNIFIED_COL_ACTIONS, UNIFIED_COL_VIOLATIONS } from './types';
import { useTableLayout } from './useTableLayout';
import { buildRowTooltipLinesFromColumns } from './tableCellText';
import { TableRowHoverTip, type TableRowHoverTipState } from './TableRowHoverTip';
import './FilterableTable.css';

export type { TableColumnDef, TableHeadColumn, TableSortState, RowViolation, RowRelationLink } from './types';
export type { ColumnOption } from './useConfigurableColumns';

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
  /** 默认 true：表头可排序 */
  enableSort?: boolean;
  /** 默认 true：首列菜单 + 第二列预警 */
  unifiedChrome?: boolean;
  /** 固定行高 + 单元格单行截断（…）；默认 true */
  clipCellOverflow?: boolean;
  /** 悬停行时在光标旁展示多行 tooltip；未提供则按列头 + 单元格文本自动生成 */
  getRowTooltipLines?: (row: T) => string[];
  /** 行级约束违背 / 预警 */
  getRowViolations?: (row: T) => RowViolation[];
  /** 行级关联跳转；未提供时可用 entityType 查注册表 */
  getRowRelations?: (row: T) => RowRelationLink[];
  entityType?: string;
  columnOptions?: ColumnOption[];
  defaultVisibleKeys?: string[];
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
  enableSort = true,
  unifiedChrome = true,
  clipCellOverflow = true,
  getRowTooltipLines,
  getRowViolations,
  getRowRelations,
  entityType,
  columnOptions,
  defaultVisibleKeys,
}: FilterableTableProps<T>) {
  const [sort, setSort] = useState<TableSortState>(null);
  const [hoverTip, setHoverTip] = useState<TableRowHoverTipState>(null);

  const resolveRelations = useCallback(
    (row: T) => {
      const custom = getRowRelations?.(row) ?? [];
      const fromRegistry = entityType ? getTableRelations(entityType, row) : [];
      const seen = new Set<string>();
      return [...custom, ...fromRegistry].filter((r) => {
        if (seen.has(r.id)) return false;
        seen.add(r.id);
        return true;
      });
    },
    [getRowRelations, entityType],
  );

  const { displayColumns } = useUnifiedTableColumns({
    tableId,
    columns,
    unifiedChrome,
    columnOptions,
    defaultVisibleKeys,
    getRowViolations,
    getRowRelations: resolveRelations,
  });

  const resolveTooltipLines = useCallback(
    (row: T) => {
      if (getRowTooltipLines) return getRowTooltipLines(row);
      if (!clipCellOverflow) return [];
      return buildRowTooltipLinesFromColumns(row, displayColumns);
    },
    [getRowTooltipLines, clipCellOverflow, displayColumns],
  );

  const showRowTip = useCallback(
    (event: React.MouseEvent<HTMLTableRowElement>, row: T) => {
      if (!clipCellOverflow && !getRowTooltipLines) return;
      const lines = resolveTooltipLines(row);
      if (lines.length === 0) return;
      setHoverTip({ lines, x: event.clientX, y: event.clientY });
    },
    [clipCellOverflow, getRowTooltipLines, resolveTooltipLines],
  );

  const moveRowTip = useCallback((event: React.MouseEvent<HTMLTableRowElement>) => {
    setHoverTip((prev) => (prev ? { ...prev, x: event.clientX, y: event.clientY } : prev));
  }, []);

  const hideRowTip = useCallback(() => setHoverTip(null), []);

  const headColumns = useMemo(
    () =>
      displayColumns.map((col) => ({
        key: col.key,
        header: col.header,
        headerNode: col.headerNode,
        ariaLabel: col.ariaLabel,
        width: col.width,
        defaultWidth: col.defaultWidth,
        minWidth: col.minWidth,
        maxWidth: col.maxWidth,
        filterable: col.filterable,
        resizable: col.resizable !== false,
        align: col.align,
        className:
          col.key === UNIFIED_COL_ACTIONS || col.key === UNIFIED_COL_VIOLATIONS
            ? `ft-th-unified ${col.className ?? ''}`.trim()
            : col.className,
        sortable:
          enableSort &&
          col.sortable !== false &&
          col.key !== UNIFIED_COL_ACTIONS &&
          col.key !== UNIFIED_COL_VIOLATIONS,
      })),
    [displayColumns, enableSort],
  );

  const { filters, setFilter, getColumnWidth, onResizeStart } = useTableLayout(tableId, headColumns);

  const onSortToggle = useCallback((key: string) => {
    setSort((prev) => {
      if (prev?.key !== key) return { key, dir: 'asc' };
      if (prev.dir === 'asc') return { key, dir: 'desc' };
      return null;
    });
  }, []);

  const sortedRows = useMemo(() => {
    if (!sort) return rows;
    const col = displayColumns.find((c) => c.key === sort.key);
    if (!col) return rows;
    const getVal = (row: T) => {
      if (col.getSortValue) return col.getSortValue(row);
      if (col.getFilterText) return col.getFilterText(row);
      const rendered = col.render(row);
      if (typeof rendered === 'number') return rendered;
      return String(rendered ?? '');
    };
    const copy = [...rows];
    copy.sort((a, b) => {
      const va = getVal(a);
      const vb = getVal(b);
      if (typeof va === 'number' && typeof vb === 'number') {
        return sort.dir === 'asc' ? va - vb : vb - va;
      }
      const sa = String(va);
      const sb = String(vb);
      const cmp = sa.localeCompare(sb, 'zh-CN', { numeric: true });
      return sort.dir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [rows, sort, displayColumns]);

  const filteredRows = useMemo(
    () =>
      applyColumnFilters(sortedRows, filters, displayColumns, (row, key) => {
        const col = displayColumns.find((c) => c.key === key);
        return col ? defaultFilterText(row, col) : '';
      }),
    [sortedRows, filters, displayColumns],
  );

  const cellClass = !clipCellOverflow && cellWrap ? 'ft-cell-wrap' : 'ft-cell';
  const tableClipClass = clipCellOverflow ? 'ft-table-clip-rows' : '';

  const renderCellContent = (col: TableColumnDef<T>, row: T) => {
    const content = col.render(row);
    if (!clipCellOverflow) return content;
    return <span className="ft-cell-inner">{content}</span>;
  };

  return (
    <div className={`${wrapClassName} ${className}`.trim()}>
      <table
        className={`data-table ft-table ${tableClipClass} ${tableClassName}`.trim()}
        data-table-id={tableId}
        data-ft-managed-tip={clipCellOverflow || getRowTooltipLines ? 'true' : undefined}
      >
        <thead>
          <TableHead
            columns={headColumns}
            filters={filters}
            setFilter={setFilter}
            getColumnWidth={getColumnWidth}
            onResizeStart={onResizeStart}
            sort={enableSort ? sort : undefined}
            onSortToggle={enableSort ? onSortToggle : undefined}
          />
        </thead>
        <tbody>
          {filteredRows.map((row, index) => {
            const key = rowKey(row, index);
            const extra = getRowProps?.(row) ?? {};
            const {
              onClick: extraOnClick,
              onContextMenu: extraOnContextMenu,
              onMouseEnter: extraMouseEnter,
              onMouseMove: extraMouseMove,
              onMouseLeave: extraMouseLeave,
              ...restExtra
            } = extra;
            const alignClass = (col: TableColumnDef<T>) =>
              col.align === 'right' ? 'ft-align-right' : col.align === 'center' ? 'ft-align-center' : '';
            return (
              <tr
                key={key}
                data-row-key={key}
                data-ft-row-ctx={extraOnContextMenu ? 'true' : undefined}
                className={getRowClassName?.(row)}
                onClick={onRowClick ? () => onRowClick(row) : extraOnClick}
                onContextMenu={extraOnContextMenu}
                onMouseEnter={(event) => {
                  showRowTip(event, row);
                  extraMouseEnter?.(event);
                }}
                onMouseMove={(event) => {
                  moveRowTip(event);
                  extraMouseMove?.(event);
                }}
                onMouseLeave={(event) => {
                  hideRowTip();
                  extraMouseLeave?.(event);
                }}
                {...restExtra}
              >
                {displayColumns.map((col) => (
                  <td
                    key={col.key}
                    data-col-key={col.key}
                    className={`${cellClass} ${alignClass(col)} ${col.className ?? ''}`.trim()}
                    style={{
                      width: getColumnWidth(col),
                      minWidth: getColumnWidth(col),
                      maxWidth: getColumnWidth(col),
                    }}
                  >
                    {renderCellContent(col, row)}
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
      <TableRowHoverTip tip={hoverTip} />
    </div>
  );
}
