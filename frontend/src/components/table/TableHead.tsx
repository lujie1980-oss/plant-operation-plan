import { useEffect, useRef, useState, type CSSProperties } from 'react';
import type { TableHeadColumn } from './types';
import './FilterableTable.css';

interface TableHeadProps {
  columns: TableHeadColumn[];
  filters: Record<string, string>;
  setFilter: (key: string, value: string) => void;
  getColumnWidth: (col: TableHeadColumn) => number;
  onResizeStart: (key: string, event: React.MouseEvent) => void;
  trailingLabelCells?: React.ReactNode;
}

type DropdownPos = { top: number; left: number; minWidth: number };

function ColumnHeaderCell({
  col,
  width,
  filterValue,
  filterActive,
  dropdownOpen,
  onToggleDropdown,
  onCloseDropdown,
  setFilter,
  onResizeStart,
}: {
  col: TableHeadColumn;
  width: number;
  filterValue: string;
  filterActive: boolean;
  dropdownOpen: boolean;
  onToggleDropdown: () => void;
  onCloseDropdown: () => void;
  setFilter: (key: string, value: string) => void;
  onResizeStart: (key: string, event: React.MouseEvent) => void;
}) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [dropdownPos, setDropdownPos] = useState<DropdownPos | null>(null);

  const alignClass =
    col.align === 'right' ? 'ft-align-right' : col.align === 'center' ? 'ft-align-center' : '';
  const filterable = col.filterable !== false && col.header.trim().length > 0;

  useEffect(() => {
    if (!dropdownOpen || !triggerRef.current) {
      setDropdownPos(null);
      return;
    }
    const update = () => {
      const rect = triggerRef.current!.getBoundingClientRect();
      setDropdownPos({
        top: rect.bottom + 4,
        left: rect.left,
        minWidth: Math.max(rect.width, 168),
      });
    };
    update();
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [dropdownOpen]);

  const dropdownStyle: CSSProperties | undefined = dropdownPos
    ? {
        position: 'fixed',
        top: dropdownPos.top,
        left: dropdownPos.left,
        minWidth: dropdownPos.minWidth,
        zIndex: 1000,
      }
    : undefined;

  return (
    <th
      className={`ft-th ${alignClass} ${col.className ?? ''} ${filterActive ? 'is-filter-active' : ''}`.trim()}
      style={{ width, minWidth: width, maxWidth: width }}
    >
      <div className="ft-th-inner">
        {filterable ? (
          <button
            ref={triggerRef}
            type="button"
            className={`ft-th-filter-trigger ${dropdownOpen ? 'is-open' : ''} ${filterActive ? 'is-active' : ''}`}
            aria-expanded={dropdownOpen}
            aria-haspopup="dialog"
            aria-label={`${col.header} 列筛选`}
            onClick={onToggleDropdown}
          >
            <span className="ft-th-label">
              {col.header}
              {col.required && <span className="md-required">*</span>}
            </span>
            <span className="ft-th-filter-icon" aria-hidden>
              {filterActive ? '●' : '▾'}
            </span>
          </button>
        ) : (
          <span className="ft-th-label">
            {col.header}
            {col.required && <span className="md-required">*</span>}
          </span>
        )}

        {col.resizable !== false && (
          <span
            className="ft-col-resize"
            role="separator"
            aria-orientation="vertical"
            aria-label={`调整 ${col.header || col.key} 列宽`}
            onMouseDown={(e) => onResizeStart(col.key, e)}
          />
        )}
      </div>

      {filterable && dropdownOpen && dropdownPos && (
        <div
          className="ft-filter-dropdown ft-filter-dropdown-fixed"
          style={dropdownStyle}
          role="dialog"
          aria-label={`${col.header} 筛选`}
          onMouseDown={(e) => e.stopPropagation()}
        >
          <label className="ft-filter-dropdown-label">{col.header}</label>
          <input
            type="search"
            className="ft-filter-input"
            placeholder={`查询 ${col.header}`}
            value={filterValue}
            autoFocus
            onChange={(e) => setFilter(col.key, e.target.value)}
            aria-label={`查询 ${col.header}`}
          />
          <div className="ft-filter-dropdown-actions">
            {filterActive && (
              <button type="button" className="ft-filter-clear-btn" onClick={() => setFilter(col.key, '')}>
                清除
              </button>
            )}
            <button type="button" className="ft-filter-close-btn" onClick={onCloseDropdown}>
              完成
            </button>
          </div>
        </div>
      )}
    </th>
  );
}

export function TableHead({
  columns,
  filters,
  setFilter,
  getColumnWidth,
  onResizeStart,
  trailingLabelCells,
}: TableHeadProps) {
  const [openFilterKey, setOpenFilterKey] = useState<string | null>(null);
  const headRef = useRef<HTMLTableRowElement>(null);

  useEffect(() => {
    if (!openFilterKey) return;
    const onDocMouseDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (headRef.current?.contains(target)) return;
      const dropdown = document.querySelector('.ft-filter-dropdown-fixed');
      if (dropdown?.contains(target)) return;
      setOpenFilterKey(null);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenFilterKey(null);
    };
    document.addEventListener('mousedown', onDocMouseDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [openFilterKey]);

  return (
    <tr className="ft-head-labels" ref={headRef}>
      {columns.map((col) => {
        const width = getColumnWidth(col);
        const filterValue = filters[col.key] ?? '';
        const filterActive = filterValue.trim().length > 0;
        const dropdownOpen = openFilterKey === col.key;

        return (
          <ColumnHeaderCell
            key={col.key}
            col={col}
            width={width}
            filterValue={filterValue}
            filterActive={filterActive}
            dropdownOpen={dropdownOpen}
            onToggleDropdown={() => setOpenFilterKey((prev) => (prev === col.key ? null : col.key))}
            onCloseDropdown={() => setOpenFilterKey(null)}
            setFilter={setFilter}
            onResizeStart={onResizeStart}
          />
        );
      })}
      {trailingLabelCells}
    </tr>
  );
}
