import { useCallback, useEffect, useState } from 'react';
import { TableCellContextMenu } from './TableCellContextMenu';
import {
  copyTextToClipboard,
  getCellTextFromElement,
  isColumnFilterActive,
  isColumnFilterEnabled,
  isTableCellContextMenuTarget,
  resolveColumnKeyFromCell,
  resolveTableId,
  toggleColumnFilter,
} from './tableCellContextMenuUtils';

type MenuState = {
  x: number;
  y: number;
  cellText: string;
  tableId: string | null;
  columnKey: string;
} | null;

/** 全局表格单元格右键菜单：复制、筛选/取消筛选。 */
export function TableCellContextMenuDelegate() {
  const [menu, setMenu] = useState<MenuState>(null);

  const closeMenu = useCallback(() => setMenu(null), []);

  useEffect(() => {
    const onContextMenu = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;

      const cell = target.closest('td');
      if (!(cell instanceof HTMLTableCellElement)) return;

      const table = cell.closest('table');
      if (!table || table.classList.contains('ft-table-no-ctx')) return;
      if (!isTableCellContextMenuTarget(cell)) return;

      event.preventDefault();
      event.stopPropagation();

      const tableId = resolveTableId(table);
      const columnKey = resolveColumnKeyFromCell(table, cell);
      const cellText = getCellTextFromElement(cell);

      setMenu({
        x: event.clientX,
        y: event.clientY,
        cellText,
        tableId,
        columnKey,
      });
    };

    document.addEventListener('contextmenu', onContextMenu, true);
    return () => document.removeEventListener('contextmenu', onContextMenu, true);
  }, []);

  if (!menu) return null;

  const filterEnabled = isColumnFilterEnabled(menu.tableId, menu.columnKey);
  const filterActive = isColumnFilterActive(menu.tableId, menu.columnKey);
  const filterLabel = filterActive ? '取消筛选' : '筛选';

  return (
    <TableCellContextMenu
      x={menu.x}
      y={menu.y}
      onClose={closeMenu}
      items={[
        {
          id: 'copy',
          label: '复制',
          disabled: !menu.cellText,
          onSelect: () => {
            void copyTextToClipboard(menu.cellText);
          },
        },
        {
          id: 'filter',
          label: filterLabel,
          disabled: !filterEnabled || (!filterActive && !menu.cellText),
          onSelect: () => {
            toggleColumnFilter(menu.tableId, menu.columnKey, menu.cellText);
          },
        },
      ]}
    />
  );
}
