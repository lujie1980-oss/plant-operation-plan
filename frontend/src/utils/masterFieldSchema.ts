import type { EditableColumn, EditableFieldType } from '../components/EditableTable';
import type { MasterFieldDefinitionMd } from '../types/masterData';

export function masterFieldDataTypeToEditable(type: MasterFieldDefinitionMd['dataType']): EditableFieldType {
  switch (type) {
    case 'NUMBER':
      return 'number';
    case 'INTEGER':
      return 'integer';
    case 'DATE':
      return 'date';
    case 'BOOL':
      return 'boolean';
    default:
      return 'text';
  }
}

/** 将 Catalog 中的 Custom 字段转为表格列 */
export function customColumnsFromSchema(
  schema: MasterFieldDefinitionMd[],
  options?: { useExtensionKey?: boolean },
): EditableColumn<Record<string, unknown>>[] {
  const useExtensionKey = options?.useExtensionKey ?? false;
  return schema
    .filter((f) => f.fieldCategory === 'CUSTOM' && f.visibleInGrid)
    .map((f) => ({
      key: useExtensionKey ? 'extensions' : f.fieldKey,
      extensionKey: useExtensionKey ? f.fieldKey : undefined,
      label: f.labelZh + (f.usedInRules ? ' ·规则' : ''),
      type: masterFieldDataTypeToEditable(f.dataType),
      required: f.required,
      width: f.dataType === 'NUMBER' || f.dataType === 'INTEGER' ? 100 : 110,
    }));
}
