import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { EditableTable, type EditableColumn } from './EditableTable';
import type { MasterDataRecord } from '../types/masterData';
import type { MasterDataTableFocus } from '../utils/masterDataFocus';
import { customColumnsFromSchema } from '../utils/masterFieldSchema';
import { buildValidationIndexByEntityKey } from '../utils/tableViolations';
import { relationsForMasterDataRow } from '../utils/tableRelationRegistry';
import type { RowViolation } from './table/types';
import '../pages/MasterDataPage.css';

interface MasterDataApi<T extends MasterDataRecord> {
  list: () => Promise<T[]>;
  save: (dto: T) => Promise<T>;
  delete: (id: number) => Promise<void>;
}

export interface TabConfig<T extends MasterDataRecord> {
  id: string;
  label: string;
  description?: string;
  api: MasterDataApi<T>;
  columns: EditableColumn<T>[];
  rowKey: (row: T) => string;
  emptyRow: () => T;
  search?: (row: T) => string;
  /** 加载行级预警所需上下文（与 rowWarning 配合，如 BOM 父件集合） */
  warningContext?: () => Promise<unknown>;
  rowWarning?: (row: T, context: unknown) => string | null;
  /** 若设置，从字段目录 API 加载 Custom 列并追加在 columns 之后 */
  fieldSchemaEntityType?: string;
  /** Custom 列存入 row.extensions（物料等动态字段） */
  customFieldsUseExtensions?: boolean;
  /** 与后端校验 entityType 一致，用于预警列索引 */
  validationEntityType?: string;
  /** 校验 entityKey，默认与 rowKey 相同 */
  validationEntityKey?: (row: T) => string;
  /** 为 false 时不重复渲染 tab 说明（由专用组件展示） */
  showDescription?: boolean;
  /** 额外 tr className（如默认最长采购周期行高亮） */
  getRowClassName?: (row: T) => string | undefined;
}

export function MasterDataTabBody<T extends MasterDataRecord>({
  config,
  onDataChange,
  tableFocus,
}: {
  config: TabConfig<T>;
  onDataChange?: () => void;
  tableFocus?: MasterDataTableFocus | null;
}) {
  const [rows, setRows] = useState<T[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [warnContext, setWarnContext] = useState<unknown>(null);
  const [validationIndex, setValidationIndex] = useState<Map<string, RowViolation[]>>(new Map());
  const [fieldSchema, setFieldSchema] = useState<Awaited<ReturnType<typeof api.masterData.fieldSchema>>>([]);

  const mergedColumns = useMemo(() => {
    if (!config.fieldSchemaEntityType || fieldSchema.length === 0) {
      return config.columns;
    }
    const custom = customColumnsFromSchema(fieldSchema, {
      useExtensionKey: config.customFieldsUseExtensions,
    }) as unknown as EditableColumn<T>[];
    const generalKeys = new Set(config.columns.map((c) => c.key));
    const extra = custom.filter((c) => !generalKeys.has(c.key as keyof T & string));
    return [...config.columns, ...extra];
  }, [config.columns, config.fieldSchemaEntityType, config.customFieldsUseExtensions, fieldSchema]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const schemaPromise = config.fieldSchemaEntityType
        ? api.masterData.fieldSchema(config.fieldSchemaEntityType)
        : Promise.resolve([]);
      const validationPromise = config.validationEntityType
        ? api.masterData.validation().catch(() => null)
        : Promise.resolve(null);
      const [list, ctx, schema, report] = await Promise.all([
        config.api.list(),
        config.warningContext ? config.warningContext() : Promise.resolve(null),
        schemaPromise,
        validationPromise,
      ]);
      setRows(list);
      setWarnContext(ctx);
      setFieldSchema(schema);
      if (config.validationEntityType && report) {
        setValidationIndex(buildValidationIndexByEntityKey(report, config.validationEntityType));
      } else {
        setValidationIndex(new Map());
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [
    config.api,
    config.warningContext,
    config.fieldSchemaEntityType,
    config.validationEntityType,
  ]);

  useEffect(() => {
    void load();
  }, [load]);

  const appliedFocus =
    tableFocus?.tabId === config.id
      ? tableFocus
      : null;

  const handleSave = async (row: T) => {
    setSaving(true);
    try {
      const saved = await config.api.save(row);
      setRows((prev) => (row.id == null ? [...prev, saved] : prev.map((r) => (r.id === row.id ? saved : r))));
      if (config.warningContext) {
        setWarnContext(await config.warningContext());
      }
      onDataChange?.();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: T) => {
    if (row.id == null) return;
    await config.api.delete(row.id);
    setRows((prev) => prev.filter((r) => r.id !== row.id));
    onDataChange?.();
  };

  return (
    <div className="md-tab-body card">
      {config.description && config.showDescription !== false && (
        <p className="md-tab-desc">{config.description}</p>
      )}
      {error && <div className="editable-table-error">{error}</div>}
      <EditableTable<T>
        tableId={config.id}
        rows={rows}
        columns={mergedColumns}
        rowKey={config.rowKey}
        emptyRow={config.emptyRow}
        onSave={handleSave}
        onDelete={handleDelete}
        loading={loading}
        saving={saving}
        search={config.search}
        validationEntityKey={
          config.validationEntityKey ??
          (config.validationEntityType ? config.rowKey : undefined)
        }
        validationIndex={validationIndex}
        rowWarning={
          config.rowWarning && warnContext != null
            ? (row) => config.rowWarning!(row, warnContext)
            : undefined
        }
        getRowRelations={(row) =>
          relationsForMasterDataRow(
            config.validationEntityType,
            row as MasterDataRecord,
            config.rowKey as (r: MasterDataRecord) => string,
          )
        }
        getRowClassName={config.getRowClassName}
        externalSearchQuery={appliedFocus?.searchQuery}
        highlightRowKey={appliedFocus?.highlightRowKey ?? null}
      />
    </div>
  );
}
