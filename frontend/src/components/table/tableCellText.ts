import { isValidElement, type ReactNode } from 'react';
import type { TableColumnDef } from './types';
import { UNIFIED_COL_ACTIONS, UNIFIED_COL_VIOLATIONS } from './types';

/** 将单元格 render 结果转为单行纯文本（供 tooltip / 过滤使用）。 */
export function extractCellText(node: ReactNode): string {
  if (node == null || node === false) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (typeof node === 'boolean') return node ? '是' : '否';
  if (Array.isArray(node)) {
    return node.map(extractCellText).filter(Boolean).join(' ');
  }
  if (isValidElement(node)) {
    const props = node.props as { children?: ReactNode };
    if (props.children != null) {
      return extractCellText(props.children);
    }
  }
  return '';
}

export function buildRowTooltipLinesFromColumns<T>(
  row: T,
  columns: TableColumnDef<T>[],
): string[] {
  return columns
    .filter((col) => col.key !== UNIFIED_COL_ACTIONS && col.key !== UNIFIED_COL_VIOLATIONS)
    .map((col) => {
      const text =
        col.getFilterText?.(row)?.trim() ||
        extractCellText(col.render(row)).trim() ||
        '—';
      return `${col.header}: ${text}`;
    });
}
