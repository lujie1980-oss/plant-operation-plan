import { useEffect, useRef, useState } from 'react';
import { buildTooltipLinesFromDomRow } from './buildTooltipLinesFromDomRow';
import { TableRowHoverTip, type TableRowHoverTipState } from './TableRowHoverTip';

function isManagedTable(table: HTMLTableElement | null): boolean {
  return table?.dataset.ftManagedTip === 'true';
}

function isClippableRow(row: HTMLTableRowElement): boolean {
  if (row.classList.contains('md-row-editing') || row.classList.contains('ft-row-no-clip')) {
    return false;
  }
  const table = row.closest('table');
  if (!table || table.classList.contains('ft-table-no-clip')) return false;
  return true;
}

/** 为未标记 data-ft-managed-tip 的表格提供行悬停 tooltip。 */
export function TableRowHoverTipDelegate() {
  const [tip, setTip] = useState<TableRowHoverTipState>(null);
  const activeRowRef = useRef<HTMLTableRowElement | null>(null);

  useEffect(() => {
    const showForRow = (row: HTMLTableRowElement, x: number, y: number) => {
      const table = row.closest('table');
      if (!table || isManagedTable(table) || !isClippableRow(row)) return;

      const lines = buildTooltipLinesFromDomRow(table, row);
      if (lines.length === 0) return;

      activeRowRef.current = row;
      setTip({ lines, x, y });
    };

    const onMouseOver = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const row = target.closest('tbody tr');
      if (!(row instanceof HTMLTableRowElement)) return;
      if (row === activeRowRef.current) return;
      showForRow(row, event.clientX, event.clientY);
    };

    const onMouseMove = (event: MouseEvent) => {
      if (!activeRowRef.current) return;
      setTip((prev) =>
        prev ? { ...prev, x: event.clientX, y: event.clientY } : prev,
      );
    };

    const onMouseOut = (event: MouseEvent) => {
      const from = event.target;
      if (!(from instanceof Element)) return;
      const row = from.closest('tbody tr');
      if (!(row instanceof HTMLTableRowElement) || row !== activeRowRef.current) return;

      const related = event.relatedTarget;
      if (related instanceof Node && row.contains(related)) return;

      activeRowRef.current = null;
      setTip(null);
    };

    document.addEventListener('mouseover', onMouseOver);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseout', onMouseOut);
    return () => {
      document.removeEventListener('mouseover', onMouseOver);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseout', onMouseOut);
    };
  }, []);

  return <TableRowHoverTip tip={tip} />;
}
