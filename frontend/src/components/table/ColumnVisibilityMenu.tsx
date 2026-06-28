import { useEffect, useRef, useState } from 'react';
import type { ColumnOption } from './useConfigurableColumns';
import './ColumnVisibilityMenu.css';

type Props = {
  options: ColumnOption[];
  visibleSet: Set<string>;
  onToggle: (key: string) => void;
  onReset?: () => void;
  label?: string;
};

export function ColumnVisibilityMenu({ options, visibleSet, onToggle, onReset, label = '列' }: Props) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  return (
    <div className="col-vis-menu" ref={wrapRef}>
      <button type="button" className="btn btn-sm col-vis-trigger" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        {label}
      </button>
      {open && (
        <div className="col-vis-dropdown" role="dialog" aria-label="显示列">
          <p className="col-vis-title">显示列</p>
          <ul className="col-vis-list">
            {options.map((opt) => (
              <li key={opt.key}>
                <label className="col-vis-item">
                  <input
                    type="checkbox"
                    checked={visibleSet.has(opt.key)}
                    disabled={opt.required}
                    onChange={() => onToggle(opt.key)}
                  />
                  <span>{opt.label}</span>
                </label>
              </li>
            ))}
          </ul>
          {onReset ? (
            <button type="button" className="col-vis-reset" onClick={() => onReset()}>
              恢复默认
            </button>
          ) : null}
        </div>
      )}
    </div>
  );
}
