import { useCallback, useEffect, useRef, useState } from 'react';
import './HorizontalResizeSplit.css';

const DEFAULT_RATIO = 0.35;

interface HorizontalResizeSplitProps {
  storageKey?: string;
  minLeftRatio?: number;
  maxLeftRatio?: number;
  defaultLeftRatio?: number;
  left: React.ReactNode;
  right: React.ReactNode;
  className?: string;
  /** 允许收起左侧面板，最大化右侧（如甘特工作区） */
  collapsibleLeft?: boolean;
  collapseBarLabel?: string;
}

function readRatio(
  key: string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  if (!key) return fallback;
  try {
    const n = parseFloat(localStorage.getItem(key) ?? '');
    if (Number.isFinite(n) && n >= min && n <= max) return n;
  } catch {
    /* ignore */
  }
  return fallback;
}

function readCollapsed(key: string | undefined): boolean {
  if (!key) return false;
  try {
    return localStorage.getItem(`${key}:left-collapsed`) === '1';
  } catch {
    return false;
  }
}

/** 左右可调节分割：拖拽中间竖条改变左/右区域宽度比例 */
export function HorizontalResizeSplit({
  storageKey,
  minLeftRatio = 0.18,
  maxLeftRatio = 0.72,
  defaultLeftRatio = DEFAULT_RATIO,
  left,
  right,
  className = '',
  collapsibleLeft = false,
  collapseBarLabel = '左侧面板',
}: HorizontalResizeSplitProps) {
  const [leftRatio, setLeftRatio] = useState(() =>
    readRatio(storageKey, defaultLeftRatio, minLeftRatio, maxLeftRatio),
  );
  const [collapsed, setCollapsed] = useState(() => readCollapsed(storageKey));
  const containerRef = useRef<HTMLDivElement>(null);
  const ratioRef = useRef(leftRatio);
  const expandedRatioRef = useRef(leftRatio);

  useEffect(() => {
    ratioRef.current = leftRatio;
  }, [leftRatio]);

  const persistRatio = useCallback(() => {
    if (storageKey) {
      localStorage.setItem(storageKey, String(ratioRef.current));
    }
  }, [storageKey]);

  const persistCollapsed = useCallback(
    (value: boolean) => {
      if (storageKey) {
        localStorage.setItem(`${storageKey}:left-collapsed`, value ? '1' : '0');
      }
    },
    [storageKey],
  );

  const collapseLeft = useCallback(() => {
    expandedRatioRef.current = ratioRef.current;
    setCollapsed(true);
    persistCollapsed(true);
  }, [persistCollapsed]);

  const expandLeft = useCallback(() => {
    setCollapsed(false);
    persistCollapsed(false);
    const restored = expandedRatioRef.current;
    const clamped = Math.min(maxLeftRatio, Math.max(minLeftRatio, restored));
    setLeftRatio(clamped);
    ratioRef.current = clamped;
    persistRatio();
  }, [maxLeftRatio, minLeftRatio, persistCollapsed, persistRatio]);

  const onResizeStart = useCallback(
    (e: React.MouseEvent) => {
      if (collapsed) return;
      e.preventDefault();
      const container = containerRef.current;
      if (!container) return;

      const startX = e.clientX;
      const startRatio = ratioRef.current;
      const rect = container.getBoundingClientRect();

      const onMove = (ev: MouseEvent) => {
        const delta = ev.clientX - startX;
        const next = startRatio + delta / rect.width;
        const clamped = Math.min(maxLeftRatio, Math.max(minLeftRatio, next));
        setLeftRatio(clamped);
        ratioRef.current = clamped;
        expandedRatioRef.current = clamped;
      };

      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        persistRatio();
      };

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    [collapsed, maxLeftRatio, minLeftRatio, persistRatio],
  );

  const leftPct = `${leftRatio * 100}%`;

  return (
    <div
      ref={containerRef}
      className={`horizontal-resize-split ${collapsed ? 'is-left-collapsed' : ''} ${className}`.trim()}
    >
      <div
        className={`horizontal-resize-left ${collapsed ? 'is-collapsed' : ''}`}
        style={collapsed ? undefined : { flexBasis: leftPct }}
      >
        {collapsed ? (
          <div className="horizontal-resize-collapsed-bar">
            <span className="horizontal-resize-collapsed-label">{collapseBarLabel}</span>
            <button type="button" className="btn horizontal-resize-toggle-btn" onClick={expandLeft}>
              展开 ▸
            </button>
          </div>
        ) : (
          left
        )}
      </div>
      <div
        className="horizontal-resize-handle"
        role="separator"
        aria-orientation="vertical"
        aria-label="调节左右区域宽度"
        tabIndex={0}
        onMouseDown={onResizeStart}
        onKeyDown={(e) => {
          if (collapsed) return;
          if (e.key === 'ArrowLeft') {
            setLeftRatio((r) => {
              const next = Math.max(minLeftRatio, r - 0.05);
              ratioRef.current = next;
              expandedRatioRef.current = next;
              return next;
            });
          } else if (e.key === 'ArrowRight') {
            setLeftRatio((r) => {
              const next = Math.min(maxLeftRatio, r + 0.05);
              ratioRef.current = next;
              expandedRatioRef.current = next;
              return next;
            });
          }
        }}
        onBlur={persistRatio}
      >
        {collapsibleLeft && !collapsed && (
          <button
            type="button"
            className="horizontal-resize-collapse-btn"
            title="收起左侧，最大化工作区"
            onClick={(e) => {
              e.stopPropagation();
              collapseLeft();
            }}
          >
            ◂ 收起
          </button>
        )}
      </div>
      <div className="horizontal-resize-right">{right}</div>
    </div>
  );
}
