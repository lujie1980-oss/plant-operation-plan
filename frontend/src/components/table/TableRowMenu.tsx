import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ColumnOption } from './useConfigurableColumns';
import { setMasterDataTableFocus } from '../../utils/masterDataFocus';
import { routeForFocusPage } from '../../utils/masterDataHealthNav';
import type { RowRelationLink } from './types';
import './TableRowMenu.css';

type Props = {
  columnOptions?: ColumnOption[];
  visibleSet?: Set<string>;
  onToggleColumn?: (key: string) => void;
  onResetColumns?: () => void;
  relations?: RowRelationLink[];
};

export function TableRowMenu({
  columnOptions,
  visibleSet,
  onToggleColumn,
  onResetColumns,
  relations = [],
}: Props) {
  const [open, setOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const wrapRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open || !triggerRef.current) {
      setMenuPos(null);
      return;
    }
    const update = () => {
      const rect = triggerRef.current!.getBoundingClientRect();
      setMenuPos({ top: rect.bottom + 4, left: rect.left });
    };
    update();
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      const t = e.target as Node;
      if (wrapRef.current?.contains(t)) return;
      const dropdown = document.querySelector('.ft-row-menu-dropdown-fixed');
      if (dropdown?.contains(t)) return;
      setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const hasColumns = columnOptions && columnOptions.length > 0 && visibleSet && onToggleColumn;
  const hasRelations = relations.length > 0;

  const followRelation = (rel: RowRelationLink) => {
    setOpen(false);
    if (rel.masterDataFocus) {
      setMasterDataTableFocus(rel.masterDataFocus);
      const page = rel.masterDataFocus.page;
      if (
        page === 'master-data' ||
        page === 'business-data' ||
        page === 'master-plan-rules' ||
        page === 'scheduling-rules' ||
        page === 'business-rules'
      ) {
        navigate(routeForFocusPage(page, rel.masterDataFocus.tabId));
        return;
      }
    }
    const path = rel.search ? `${rel.to}?${rel.search}` : rel.to;
    navigate(path);
  };

  return (
    <div className="ft-row-menu" ref={wrapRef}>
      <button
        ref={triggerRef}
        type="button"
        className="ft-row-menu-trigger"
        aria-label="行菜单"
        aria-expanded={open}
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
      >
        ⋯
      </button>
      {open && menuPos && (
        <div
          className="ft-row-menu-dropdown ft-row-menu-dropdown-fixed"
          role="menu"
          style={
            {
              position: 'fixed',
              top: menuPos.top,
              left: menuPos.left,
            } as CSSProperties
          }
        >
          {hasColumns && (
            <section className="ft-row-menu-section">
              <p className="ft-row-menu-section-title">选择属性</p>
              <ul className="ft-row-menu-list">
                {columnOptions!.map((opt) => (
                  <li key={opt.key}>
                    <label className="ft-row-menu-check">
                      <input
                        type="checkbox"
                        checked={visibleSet!.has(opt.key)}
                        disabled={opt.required}
                        onChange={() => onToggleColumn!(opt.key)}
                      />
                      <span>{opt.label}</span>
                    </label>
                  </li>
                ))}
              </ul>
              {onResetColumns ? (
                <button type="button" className="ft-row-menu-reset" onClick={() => onResetColumns()}>
                  恢复默认列
                </button>
              ) : null}
            </section>
          )}
          {hasRelations && (
            <section className="ft-row-menu-section">
              <p className="ft-row-menu-section-title">关联跳转</p>
              <ul className="ft-row-menu-list ft-row-menu-links">
                {relations.map((rel) => (
                  <li key={rel.id}>
                    <button type="button" className="ft-row-menu-link" onClick={() => followRelation(rel)}>
                      {rel.label}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {!hasColumns && !hasRelations && (
            <p className="ft-row-menu-empty">暂无菜单项</p>
          )}
        </div>
      )}
    </div>
  );
}
