import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { MasterDataRecord } from '../types/masterData';
import { applyColumnFilters } from './table/filterRows';
import { TableHead } from './table/TableHead';
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
  /** 用于格式化展示（非编辑时） */
  format?: (value: T[keyof T & string], row: T) => ReactNode;
  /** 是否允许编辑（默认 true） */
  editable?: boolean;
  filterable?: boolean;
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
  /** 行级预警文案（如 BOM 缺失） */
  rowWarning?: (row: T) => string | null;
  /** 外部注入的搜索词（如从数据健康跳转） */
  externalSearchQuery?: string;
  /** 高亮并滚动到该行（rowKey 返回值） */
  highlightRowKey?: string | null;
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
  externalSearchQuery,
  highlightRowKey,
}: EditableTableProps<T>) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [draft, setDraft] = useState<T | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [query, setQuery] = useState('');
  const [pendingError, setPendingError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const headColumns = useMemo(
    () =>
      columns.map((col) => ({
        key: col.key,
        header: col.label,
        width: col.width,
        defaultWidth: col.width ?? 120,
        filterable: col.filterable,
        resizable: col.resizable,
        required: col.required,
      })),
    [columns],
  );

  const { filters, setFilter, getColumnWidth, onResizeStart, hasActiveFilters, clearFilters } =
    useTableLayout(tableId, headColumns);

  const widthFor = (key: string) => {
    const headCol = headColumns.find((c) => c.key === key);
    return headCol ? getColumnWidth(headCol) : undefined;
  };

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
      columns.map((c) => ({ key: c.key })),
      (row, key) => {
      const col = columns.find((c) => c.key === key);
      if (!col) return '';
      const value = row[col.key];
      if (col.getFilterText) {
        return col.getFilterText(value, row);
      }
      if (col.format) {
        const rendered = col.format(value, row);
        if (typeof rendered === 'string' || typeof rendered === 'number') {
          return String(rendered);
        }
      }
      if (col.type === 'boolean') {
        return value ? '是 true' : '否 false';
      }
      if (value == null) return '';
      return String(value);
    });
    return result;
  }, [rows, query, search, filters, columns]);

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

  const updateField = (key: keyof T & string, type: EditableFieldType, raw: string) => {
    setDraft((prev) => (prev ? { ...prev, [key]: parseInput(raw, type) as T[typeof key] } : prev));
  };

  const validate = (row: T): string | null => {
    for (const col of columns) {
      if (col.required) {
        const v = row[col.key];
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
    const raw = inputValue(row[col.key], col.type);
    if (col.editable === false) {
      return <span className="md-readonly">{String(raw)}</span>;
    }
    if (col.type === 'boolean') {
      return (
        <input
          type="checkbox"
          checked={raw === 'true'}
          onChange={(e) => updateField(col.key, col.type, e.target.checked ? 'true' : 'false')}
        />
      );
    }
    if (col.type === 'select' && col.options) {
      return (
        <select
          className="md-input"
          value={raw}
          onChange={(e) => updateField(col.key, col.type, e.target.value)}
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
    const inputType = col.type === 'date' ? 'date' : col.type === 'number' || col.type === 'integer' ? 'number' : 'text';
    const step = col.step ?? (col.type === 'integer' ? 1 : col.type === 'number' ? 0.0001 : undefined);
    return (
      <input
        className="md-input"
        type={inputType}
        value={raw}
        step={step}
        onChange={(e) => updateField(col.key, col.type, e.target.value)}
      />
    );
  };

  const renderCellRead = (col: EditableColumn<T>, row: T): ReactNode => {
    const value = row[col.key];
    if (col.format) {
      return col.format(value, row);
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
        <table className="data-table md-table ft-table">
          <thead>
            <TableHead
              columns={headColumns}
              filters={filters}
              setFilter={setFilter}
              getColumnWidth={getColumnWidth}
              onResizeStart={onResizeStart}
              trailingLabelCells={
                <>
                  {rowWarning && <th className="md-warn-col ft-th">预警</th>}
                  <th className="md-actions-col ft-th">操作</th>
                </>
              }
            />
          </thead>
          <tbody>
            {isCreating && draft && (
              <tr className="md-row-editing md-row-new">
                {columns.map((col) => (
                  <td key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                    {renderCellEdit(col, draft)}
                  </td>
                ))}
                {rowWarning && (
                  <td className="md-warn-col">
                    <span className="md-muted">—</span>
                  </td>
                )}
                <td className="md-actions-col">
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
              const warn = rowWarning?.(renderRow) ?? null;
              const highlighted = highlightRowKey != null && highlightRowKey === key;
              return (
                <tr
                  key={key}
                  data-row-key={key}
                  className={[
                    editing ? 'md-row-editing' : '',
                    warn ? 'md-row-has-warn' : '',
                    highlighted ? 'md-row-highlight' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                {columns.map((col) =>
                  editing ? (
                    <td key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                      {renderCellEdit(col, renderRow)}
                    </td>
                  ) : (
                    <td key={col.key} style={{ width: widthFor(col.key), minWidth: widthFor(col.key) }}>
                      {renderCellRead(col, renderRow)}
                    </td>
                  ),
                )}
                  {rowWarning && (
                    <td className="md-warn-col">
                      {warn ? <span className="md-bom-warn">{warn}</span> : <span className="md-muted">—</span>}
                    </td>
                  )}
                  <td className="md-actions-col">
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
