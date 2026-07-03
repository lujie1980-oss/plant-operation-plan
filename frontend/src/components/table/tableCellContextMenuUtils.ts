import { getTableFilters } from './tableFilterRegistry';

export function getCellTextFromElement(cell: HTMLElement): string {
  return (cell.textContent ?? '').trim().replace(/\s+/g, ' ');
}

export function resolveColumnKeyFromCell(
  table: HTMLTableElement,
  cell: HTMLTableCellElement,
): string {
  if (cell.dataset.colKey) return cell.dataset.colKey;
  const headerRow = table.querySelector('thead tr:last-child') ?? table.querySelector('thead tr');
  const th = headerRow?.children.item(cell.cellIndex);
  if (th instanceof HTMLElement && th.dataset.colKey) {
    return th.dataset.colKey;
  }
  return `col-${cell.cellIndex}`;
}

export function isTableCellContextMenuTarget(cell: HTMLTableCellElement): boolean {
  if (shouldDeferToRowContextMenu(cell)) return false;
  if (cell.closest('thead')) return false;
  if (cell.colSpan > 1) return false;
  if (
    cell.classList.contains('md-actions-col') ||
    cell.classList.contains('ft-td-unified') ||
    cell.classList.contains('mpdm-tree-toggle') ||
    cell.classList.contains('wo-tree-td-toggle') ||
    cell.classList.contains('wo-tree-td-check')
  ) {
    return false;
  }
  if (cell.querySelector('input, select, textarea')) return false;
  return true;
}

/** 行级自定义右键菜单优先（如需求满足、批次计划等）。 */
export function shouldDeferToRowContextMenu(cell: HTMLTableCellElement): boolean {
  const row = cell.closest('tr');
  if (!(row instanceof HTMLTableRowElement)) return false;
  if (row.dataset.ftRowCtx === 'true') return true;
  const table = cell.closest('table');
  return table?.dataset.ftTableRowCtx === 'true';
}

export function resolveTableId(table: HTMLTableElement): string | null {
  return table.dataset.tableId ?? null;
}

export async function copyTextToClipboard(text: string): Promise<boolean> {
  if (!text) return false;
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(textarea);
      return ok;
    } catch {
      return false;
    }
  }
}

export function toggleColumnFilter(tableId: string | null, columnKey: string, cellText: string): boolean {
  if (!tableId) return false;
  const api = getTableFilters(tableId);
  if (!api?.isFilterable(columnKey)) return false;

  const active = api.getFilter(columnKey).trim();
  if (active) {
    api.clearFilter(columnKey);
  } else {
    api.setFilter(columnKey, cellText);
  }
  return true;
}

export function isColumnFilterActive(tableId: string | null, columnKey: string): boolean {
  if (!tableId) return false;
  const api = getTableFilters(tableId);
  return Boolean(api?.getFilter(columnKey).trim());
}

export function isColumnFilterEnabled(tableId: string | null, columnKey: string): boolean {
  if (!tableId) return false;
  const api = getTableFilters(tableId);
  return Boolean(api?.isFilterable(columnKey));
}
