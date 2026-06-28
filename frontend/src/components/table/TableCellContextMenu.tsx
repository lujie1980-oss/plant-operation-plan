import { useEffect, useRef } from 'react';
import './TableCellContextMenu.css';

export type TableCellContextMenuItem = {
  id: string;
  label: string;
  disabled?: boolean;
  onSelect: () => void;
};

type TableCellContextMenuProps = {
  x: number;
  y: number;
  items: TableCellContextMenuItem[];
  onClose: () => void;
};

export function TableCellContextMenu({ x, y, items, onClose }: TableCellContextMenuProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose();
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const maxX = window.innerWidth - rect.width - 8;
    const maxY = window.innerHeight - rect.height - 8;
    el.style.left = `${Math.min(x, maxX)}px`;
    el.style.top = `${Math.min(y, maxY)}px`;
  }, [x, y]);

  return (
    <div ref={ref} className="ft-cell-ctx-menu" style={{ left: x, top: y }} role="menu">
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          role="menuitem"
          className="ft-cell-ctx-menu-item"
          disabled={item.disabled}
          onClick={() => {
            if (item.disabled) return;
            item.onSelect();
            onClose();
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
