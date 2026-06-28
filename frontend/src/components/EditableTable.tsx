import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { MasterDataRecord } from '../types/masterData';
import { mergeViolations, warningToViolations } from '../utils/tableViolations';
import { applyColumnFilters } from './table/filterRows';
import { ConstraintViolationCell } from './table/ConstraintViolationCell';
import { TableHead } from './table/TableHead';
import { TableRowMenu } from './table/TableRowMenu';
import type { RowRelationLink, RowViolation, TableSortState } from './table/types';
import {
  TABLE_COL_ROW_ACTIONS,
  UNIFIED_COL_ACTIONS,
  UNIFIED_COL_VIOLATIONS,
  VIOLATION_HEADER_ARIA,
} from './table/types';
import { ViolationColumnHeader } from './table/ViolationColumnHeader';
import { useConfigurableColumns } from './table/useConfigurableColumns';
import { useTableLayout } from './table/useTableLayout';
import './EditableTable.css';
import './table/FilterableTable.css';

export type EditableFieldType = 'text' | 'number' | 'integer' | 'date' | 'boolean' | 'select';

export interface EditableColumn<T> {
  key: keyof T & string;
  label: string;
  type: EditableFieldType;
  required?: boolean;
  options?: { value: string; label: string }[];
  width?: number;
  step?: number;
  extensionKey?: string;
  format?: (value: T[keyof T & string], row: T) => ReactNode;
  editable?: boolean;
  filterable?: boolean;
  sortable?: boolean;
  resizable?: boolean;
  getFilterText?: (value: T[keyof T & string], row: T) => string;
}

interface EditableTableProps<T extends MasterDataRecord> {
  tableId?: string;
  rows: T[];
  columns: EditableColumn<T>[];
  rowKey: (row: T) => string;
  emptyRow: () => T;
  onSave: (row: T) => Promise<void> | void;
  onDelete: (row: T) => Promise<void> | void;
  loading?: boolean;
  saving?: boolean;
  search?: (row: T) => string;
  emptyText?: string;
  rowWarning?: (row: T) => string | null;
  validationEntityKey?: (row: T) => string;
  validationIndex?: Map<string, RowViolation[]>;
  getRowRelations?: (row: T) => RowRelationLink[];
  externalSearchQuery?: string;
  highlightRowKey?: string | null;
  getRowClassName?: (row: T) => string | undefined;
}

function inputValue(value: unknown, type: EditableFieldType): string {
  if (value === null || value === undefined) return '';
  if (type === 'boolean') return value ? 'true' : 'false';
  return String(value);
}

function parseInput(raw: string, type: EditableFieldType): unknown {
  if (type === 'boolean') return raw === 'true';
  if (type === 'integer') return raw === '' ? 0 : Number.parseInt(raw, 10);
  if (type === 'number') return raw === '' ? 0 : Number.parseFloat(raw);
  if (type === 'date') return raw === '' ? null : raw;
  return raw;
}

type RowWithExtensions = { extensions?: Record<string, unknown> | null };

function readCellValue<T>(row: T, col: EditableColumn<T>): unknown {
  if (col.extensionKey) {
    const ext = (row as T & RowWithExtensions).extensions ?? {};
    return ext[col.extensionKey];
  }
  return row[col.key];
}

function writeCellValue<T>(row: T, col: EditableColumn<T>, value: unknown): T {
  if (col.extensionKey) {
    const current = (row as T & RowWithExtensions).extensions ?? {};
    return {
      ...row,
      extensions: { ...current, [col.extensionKey]: value },
    } as T;
  }
  return { ...row, [col.key]: value } as T;
}

function filterTextForColumn<T>(row: T, col: EditableColumn<T>): string {
  const value = readCellValue(row, col);
  if (col.getFilterText) {
    return col.getFilterText(value as T[keyof T & string], row);
  }
  if (col.format) {
    const rendered = col.format(value as T[keyof T & string], row);
    if (typeof rendered === 'string' || typeof rendered === 'number') {
      return String(rendered);
    }
  }
  if (col.type === 'boolean') {
    return value ? '是 true' : '否 false';
  }
  if (value == null) return '';
  return String(value);
}

export function EditableTable<T extends MasterDataRecord>({
  tableId = 'editable-table',
  rows,
  columns,
  rowKey,
  emptyRow,
  onSave,
  onDelete,
  loading = false,
  saving = false,
  search,
  emptyText = '暂无数据',
  rowWarning,
  validationEntityKey,
  validationIndex,
  getRowRelations,
  externalSearchQuery,
  highlightRowKey,
  getRowClassName,
}: EditableTableProps<T>) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [draft, setDraft] = useState<T | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [query, setQuery] = useState('');
  const [pendingError, setPendingError] = useState<string | null>(null);
  const [sort, setSort] = useState<TableSortState>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const columnOptions = useMemo(
    () => columns.map((c) => ({ key: c.key, label: c.label })),
    [columns],
  );
  const defaultVisibleKeys = useMemo(() => columns.map((c) => c.key), [columns]);
  const { visibleSet, toggleColumn, resetColumns } = useConfigurableColumns(
    `${tableId}-cols`,
    columnOptions,
    defaultVisibleKeys,
  );

  const visibleColumns = useMemo(
    () => columns.filter((c) => visibleSet.has(c.key)),
    [columns, visibleSet],
  );

  const resolveViolations = useCallback(
    (row: T): RowViolation[] => {
      const key = validationEntityKey?.(row) ?? rowKey(row);
      const fromApi = validationIndex?.get(key) ?? [];
      return mergeViolations(fromApi, warningToViolations(rowWarning?.(row) ?? null));
    },
    [validationEntityKey, validationIndex, rowKey, rowWarning],
  );

  const headColumns = useMemo(
    () => [
      {
        key: UNIFIED_COL_ACTIONS,
        header: '…',
        width: 36,
        defaultWidth: 36,
        filterable: false,
        sortable: false,
        resizable: true,
        className: 'ft-th-unified',
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
        className: 'ft-th-unified',
      },
      ...visibleColumns.map((col) => ({
        key: col.key,
        header: col.label,
        width: col.width,
        defaultWidth: col.width ?? 120,
        filterable: col.filterable !== false,
        sortable: col.sortable !== false,
        resizable: col.resizable !== false,
        required: col.required,
      })),
      {
        key: TABLE_COL_ROW_ACTIONS,
        header: '操作',
        defaultWidth: 132,
        minWidth: 88,
        filterable: false,
        sortable: false,
        resizable: true,
        className: 'md-actions-col ft-th',
      },
    ],
    [visibleColumns],
  );

  const { filters, setFilter, getColumnWidth, onResizeStart, hasActiveFilters, clearFilters } =
    useTableLayout(tableId, headColumns);

  const widthFor = (key: string) => {
    const headCol = headColumns.find((c) => c.key === key);
    return headCol ? getColumnWidth(headCol) : undefined;
  };

  const onSortToggle = useCallback((key: string) => {
    setSort((prev) => {
      if (prev?.key !== key) return { key, dir: 'asc' };
      if (prev.dir === 'asc') return { key, dir: 'desc' };
      return null;
    });
  }, []);

  useEffect(() => {
    if (externalSearchQuery != null) {
      setQuery(externalSearchQuery);
    }
  }, [externalSearchQuery]);

  useEffect(() => {
    if (!highlightRowKey || !scrollRef.current) return;
    const el = scrollRef.current.querySelector(`tr[data-row-key="${CSS.escape(highlightRowKey)}"]`);
    if (el instanceof HTMLElement) {
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }, [highlightRowKey, rows, query]);

  const filtered = useMemo(() => {
    let result = rows;
    if (search && query.trim()) {
      const q = query.toLowerCase();
      result = result.filter((row) => search(row).toLowerCase().includes(q));
    }
    result = applyColumnFilters<T>(
      result,
      filters,
      visibleColumns.map((c) => ({ key: c.key })),
      (row, key) => {
        const col = visibleColumns.find((c) => c.key === key);
        return col ? filterTextForColumn(row, col) : '';
      },
    );
    if (sort) {
      const col = visibleColumns.find((c) => c.key === sort.key);
      if (col) {
        const copy = [...result];
        copy.sort((a, b) => {
          const sa = filterTextForColumn(a, col);
          const sb = filterTextForColumn(b, col);
          const na = Number(sa);
          const nb = Number(sb);
          if (!Number.isNaN(na) && !Number.isNaN(nb) && sa !== '' && sb !== '') {
            return sort.dir === 'asc' ? na - nb : nb - na;
          }
          const cmp = sa.localeCompare(sb, 'zh-CN', { numeric: true });
          return sort.dir === 'asc' ? cmp : -cmp;
        });
        result = copy;
      }
    }
    return result;
  }, [rows, query, search, filters, visibleColumns, sort]);

  const startEdit = (row: T) => {
    setEditingKey(rowKey(row));
    setDraft({ ...row });
    setIsCreating(false);
    setPendingError(null);
  };

  const startCreate = () => {
    setEditingKey('__new__');
    setDraft(emptyRow());
    setIsCreating(true);
    setPendingError(null);
  };

  const cancelEdit = () => {
    setEditingKey(null);
    setDraft(null);
    setIsCreating(false);
    setPendingError(null);
  };

  const updateField = (col: EditableColumn<T>, type: EditableFieldType, raw: string) => {
    setDraft((prev) => (prev ? writeCellValue(prev, col, parseInput(raw, type)) : prev));
  };

  const validate = (row: T): string | null => {
    for (const col of columns) {
      if (col.required) {
        const v = readCellValue(row, col);
        if (v === null || v === undefined || v === '') {
          return `${col.label} 不能为空`;
        }
      }
    }
    return null;
  };

  const doSave = async () => {
    if (!draft) return;
    const err = validate(draft);
    if (err) {
      setPendingError(err);
      return;
    }
    try {
      await onSave(draft);
      cancelEdit();
    } catch (e) {
      setPendingError(e instanceof Error ? e.message : '保存失败');
    }
  };

  const doDelete = async (row: T) => {
    if (row.id === null) return;
    if (!window.confirm(`确认删除该记录？此操作不可撤销。`)) return;
    try {
      await onDelete(row);
    } catch (e) {
      setPendingError(e instanceof Error ? e.message : '删除失败');
    }
  };

  const renderCellEdit = (col: EditableColumn<T>, row: T) => {
    const raw = inputValue(readCellValue(row, col), col.type);
    if (col.editable === false) {
      return <span className="md-readonly">{String(raw)}</span>;
    }
    if (col.type === 'boolean') {
      return (
        <input
          type="checkbox"
          checked={raw === 'true'}
          onChange={(e) => updateField(col, col.type, e.target.checked ? 'true' : 'false')}
        />
      );
    }
    if (col.type === 'select' && col.options) {
      return (
        <select
          className="md-input"
          value={raw}
          onChange={(e) => updateField(col, col.type, e.target.value)}
        >
          <option value="">--</option>
          {col.options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      );
    }
    const inputType =
      col.type === 'date' ? 'date' : col.type === 'number' || col.type === 'integer' ? 'number' : 'text';
    const step = col.step ?? (col.type === 'integer' ? 1 : col.type === 'number' ? 0.0001 : undefined);
    return (
      <input
        className="md-input"
        type={inputType}
        value={raw}
        step={step}
        onChange={(e) => updateField(col, col.type, e.target.value)}
      />
    );
  };

  const renderCellRead = (col: EditableColumn<T>, row: T): ReactNode => {
    const value = readCellValue(row, col);
    if (col.format) {
      return col.format(value as T[keyof T & string], row);
    }
    if (col.type === 'boolean') {
      return value ? '✓' : '—';
    }
    if (value === null || value === undefined || value === '') {
      return <span className="md-muted">—</span>;
    }
    if (col.type === 'number') {
      const num = Number(value);
      if (!Number.isNaN(num)) {
        return num.toLocaleString(undefined, { maximumFractionDigits: 4 });
      }
    }
    return String(value);
  };

  const renderChromeCells = (row: T, editing: boolean) => (
    <>
      <td className="ft-td-unified" style={{ width: widthFor(UNIFIED_COL_ACTIONS) }}>
        {!editing && (
          <TableRowMenu
            columnOptions={columnOptions}
            visibleSet={visibleSet}
            onToggleColumn={toggleColumn}
            onResetColumns={resetColumns}
            relations={getRowRelations?.(row) ?? []}
          />
        )}
      </td>
      <td className="ft-td-unified" style={{ width: widthFor(UNIFIED_COL_VIOLATIONS) }}>
        <ConstraintViolationCell violations={resolveViolations(row)} />
      </td>
    </>
  );

  return (
    <div className="editable-table">
      <div className="editable-table-toolbar">
        {search && (
          <input
            className="input"
            type="search"
            placeholder="全局搜索"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        )}
        {hasActiveFilters && (
          <button type="button" className="btn btn-secondary md-btn" onClick={clearFilters}>
            清除列过滤
          </button>
        )}
        <span className="editable-table-count">共 {rows.length} 条</span>
        <div className="editable-table-toolbar-spacer" />
        <button
          type="button"
          className="btn primary"
          onClick={startCreate}
          disabled={editingKey !== null || loading}
        >
          新增
        </button>
      </div>
      {pendingError && <div className="editable-table-error">{pendingError}</div>}
      <div className="editable-table-scroll" ref={scrollRef}>
        <table className="data-table md-table ft-table" data-table-id={tableId}>
          <thead>
            <TableHead
              columns={headColumns}
              filters={filters}
              setFilter={setFilter}
              getColumnWidth={getColumnWidth}
              onResizeStart={onResizeStart}
              sort={sort}
              onSortToggle={onSortToggle}
            />
          </thead>
          <tbody>
            {isCreating && draft && (
              <tr className="md-row-editing md-row-new">
                {renderChromeCells(draft, true)}
                {visibleColumns.map((col) => (
                  <td key={col.key} data-col-key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                    {renderCellEdit(col, draft)}
                  </td>
                ))}
                <td
                  className="md-actions-col"
                  style={{
                    width: widthFor(TABLE_COL_ROW_ACTIONS),
                    minWidth: widthFor(TABLE_COL_ROW_ACTIONS),
                  }}
                >
                  <button type="button" className="btn md-btn primary" onClick={() => void doSave()} disabled={saving}>
                    保存
                  </button>
                  <button type="button" className="btn md-btn" onClick={cancelEdit} disabled={saving}>
                    取消
                  </button>
                </td>
              </tr>
            )}
            {filtered.map((row) => {
              const key = rowKey(row);
              const editing = editingKey === key && !isCreating;
              const renderRow = editing && draft ? draft : row;
              const violations = resolveViolations(renderRow);
              const hasViolation = violations.length > 0;
              const highlighted = highlightRowKey != null && highlightRowKey === key;
              const extraRowClass = getRowClassName?.(row);
              return (
                <tr
                  key={key}
                  data-row-key={key}
                  className={[
                    editing ? 'md-row-editing' : '',
                    hasViolation ? 'md-row-has-warn' : '',
                    highlighted ? 'md-row-highlight' : '',
                    extraRowClass ?? '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  {renderChromeCells(renderRow, editing)}
                  {visibleColumns.map((col) =>
                    editing ? (
                      <td key={col.key} data-col-key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                        {renderCellEdit(col, renderRow)}
                      </td>
                    ) : (
                      <td key={col.key} data-col-key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                        {renderCellRead(col, renderRow)}
                      </td>
                    ),
                  )}
                  <td
                    className="md-actions-col"
                    style={{
                      width: widthFor(TABLE_COL_ROW_ACTIONS),
                      minWidth: widthFor(TABLE_COL_ROW_ACTIONS),
                    }}
                  >
                    {editing ? (
                      <>
                        <button
                          type="button"
                          className="btn md-btn primary"
                          onClick={() => void doSave()}
                          disabled={saving}
                        >
                          保存
                        </button>
                        <button type="button" className="btn md-btn" onClick={cancelEdit} disabled={saving}>
                          取消
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="btn md-btn"
                          onClick={() => startEdit(row)}
                          disabled={editingKey !== null}
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          className="btn md-btn danger"
                          onClick={() => void doDelete(row)}
                          disabled={editingKey !== null}
                        >
                          删除
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {filtered.length === 0 && !isCreating && (
          <p className="empty md-empty">{loading ? '加载中…' : emptyText}</p>
        )}
      </div>
    </div>
  );
}
