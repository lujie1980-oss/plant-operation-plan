import type { ReactNode } from 'react';

export type TableColumnDef<T> = {
  key: string;
  header: string;
  width?: number;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  className?: string;
  render: (row: T) => ReactNode;
  /** 用于表头列过滤；默认取 render 结果的纯文本 */
  getFilterText?: (row: T) => string;
};

export type TableHeadColumn = {
  key: string;
  header: string;
  width?: number;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  filterable?: boolean;
  resizable?: boolean;
  align?: 'left' | 'right' | 'center';
  className?: string;
  required?: boolean;
};
