/** 从 DOM 表头 + 行单元格构建悬停 tooltip 文本（供全局表格委托使用）。 */
export function buildTooltipLinesFromDomRow(
  table: HTMLTableElement,
  row: HTMLTableRowElement,
): string[] {
  const headerRow = table.querySelector('thead tr:last-child') ?? table.querySelector('thead tr');
  const headers = headerRow
    ? Array.from(headerRow.querySelectorAll('th')).map((th) => {
        const label = th.querySelector('.ft-th-label, .ft-th-filter-trigger');
        return (label?.textContent ?? th.textContent ?? '').trim();
      })
    : [];

  return Array.from(row.querySelectorAll(':scope > td'))
    .map((td, index) => {
      if (
        td.classList.contains('md-actions-col') ||
        td.classList.contains('ft-td-unified') ||
        td.classList.contains('mpdm-tree-toggle') ||
        td.querySelector('input, select, textarea')
      ) {
        return null;
      }
      const text = (td.textContent ?? '').trim().replace(/\s+/g, ' ') || '—';
      const header = headers[index]?.trim();
      if (!header) {
        return text && text !== '—' ? text : null;
      }
      return `${header}: ${text}`;
    })
    .filter((line): line is string => Boolean(line));
}
