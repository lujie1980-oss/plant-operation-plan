import type { ReactNode } from 'react';

export const UNIFIED_COL_ACTIONS = '__actions';
export const UNIFIED_COL_VIOLATIONS = '__violations';
/** 行操作列（可编辑表尾部） */
export const TABLE_COL_ROW_ACTIONS = '__row_actions';

export const VIOLATION_HEADER_ARIA = 'Constraint Violation（预警）';

export type RowViolationLevel = 'error' | 'warn' | 'info';

export type RowViolation = {
  level: RowViolationLevel;
  ruleCode?: string;
  message: string;
};

export type RowRelationLink = {
  id: string;
  label: string;
  /** 路由路径 */
  to: string;
  /** 查询串（不含 ?） */
  search?: string;
  /** 主数据跨页定位（优先于 search） */
  masterDataFocus?: import('../../utils/masterDataFocus').MasterDataTableFocus;
};

export type TableColumnDef<T> = {
  key: string;
  header: string;
  headerNode?: ReactNode;
  ariaLabel?: string;
  width?: number;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  /** 默认 true；统一列 __actions / __violations 除外 */
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  className?: string;
  render: (row: T) => ReactNode;
  getFilterText?: (row: T) => string;
  sortable?: boolean;
  getSortValue?: (row: T) => string | number;
};

export type TableHeadColumn = {
  key: string;
  header: string;
  headerNode?: ReactNode;
  ariaLabel?: string;
  width?: number;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  className?: string;
  required?: boolean;
  sortable?: boolean;
};

export type TableSortState = {
  key: string;
  dir: 'asc' | 'desc';
} | null;
