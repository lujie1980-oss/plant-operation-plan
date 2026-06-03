import { useCallback, useEffect, useRef, useState } from 'react';
import './VerticalResizeSplit.css';

const DEFAULT_RATIO = 0.4;

interface VerticalResizeSplitProps {
  storageKey?: string;
  minTopRatio?: number;
  maxTopRatio?: number;
  defaultTopRatio?: number;
  top: React.ReactNode;
  bottom: React.ReactNode;
  className?: string;
  /** 允许将上方面板收到底部条，最大化下方区域（如甘特） */
  collapsible?: boolean;
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
    return localStorage.getItem(`${key}:collapsed`) === '1';
  } catch {
    return false;
  }
}

/** 上下可调节分割：拖拽中间横条改变上/下区域高度比例 */
export function VerticalResizeSplit({
  storageKey,
  minTopRatio = 0.2,
  maxTopRatio = 0.75,
  defaultTopRatio = DEFAULT_RATIO,
  top,
  bottom,
  className = '',
  collapsible = false,
  collapseBarLabel = '上方面板',
}: VerticalResizeSplitProps) {
  const [topRatio, setTopRatio] = useState(() =>
    readRatio(storageKey, defaultTopRatio, minTopRatio, maxTopRatio),
  );
  const [collapsed, setCollapsed] = useState(() => readCollapsed(storageKey));
  const containerRef = useRef<HTMLDivElement>(null);
  const ratioRef = useRef(topRatio);
  const expandedRatioRef = useRef(topRatio);

  useEffect(() => {
    ratioRef.current = topRatio;
  }, [topRatio]);

  const persistRatio = useCallback(() => {
    if (storageKey) {
      localStorage.setItem(storageKey, String(ratioRef.current));
    }
  }, [storageKey]);

  const persistCollapsed = useCallback(
    (value: boolean) => {
      if (storageKey) {
        localStorage.setItem(`${storageKey}:collapsed`, value ? '1' : '0');
      }
    },
    [storageKey],
  );

  const collapseTop = useCallback(() => {
    expandedRatioRef.current = ratioRef.current;
    setCollapsed(true);
    persistCollapsed(true);
  }, [persistCollapsed]);

  const expandTop = useCallback(() => {
    setCollapsed(false);
    persistCollapsed(false);
    const restored = expandedRatioRef.current;
    const clamped = Math.min(maxTopRatio, Math.max(minTopRatio, restored));
    setTopRatio(clamped);
    ratioRef.current = clamped;
    persistRatio();
  }, [maxTopRatio, minTopRatio, persistCollapsed, persistRatio]);

  const onResizeStart = useCallback(
    (e: React.MouseEvent) => {
      if (collapsed) return;
      e.preventDefault();
      const container = containerRef.current;
      if (!container) return;

      const startY = e.clientY;
      const startRatio = ratioRef.current;
      const rect = container.getBoundingClientRect();

      const onMove = (ev: MouseEvent) => {
        const delta = ev.clientY - startY;
        const next = startRatio + delta / rect.height;
        const clamped = Math.min(maxTopRatio, Math.max(minTopRatio, next));
        setTopRatio(clamped);
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

      document.body.style.cursor = 'row-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    [collapsed, maxTopRatio, minTopRatio, persistRatio],
  );

  const topPct = `${topRatio * 100}%`;

  return (
    <div
      ref={containerRef}
      className={`vertical-resize-split ${collapsed ? 'is-top-collapsed' : ''} ${className}`.trim()}
    >
      <div
        className={`vertical-resize-top ${collapsed ? 'is-collapsed' : ''}`}
        style={collapsed ? undefined : { flexBasis: topPct }}
      >
        {collapsed ? (
          <div className="vertical-resize-collapsed-bar">
            <span className="vertical-resize-collapsed-label">{collapseBarLabel}</span>
            <button type="button" className="btn vertical-resize-toggle-btn" onClick={expandTop}>
              展开 ▲
            </button>
          </div>
        ) : (
          top
        )}
      </div>
      <div
        className="vertical-resize-handle"
        role="separator"
        aria-orientation="horizontal"
        aria-label="调节上下区域高度"
        tabIndex={0}
        onMouseDown={onResizeStart}
        onKeyDown={(e) => {
          if (collapsed) return;
          if (e.key === 'ArrowUp') {
            setTopRatio((r) => {
              const next = Math.max(minTopRatio, r - 0.05);
              ratioRef.current = next;
              expandedRatioRef.current = next;
              return next;
            });
          } else if (e.key === 'ArrowDown') {
            setTopRatio((r) => {
              const next = Math.min(maxTopRatio, r + 0.05);
              ratioRef.current = next;
              expandedRatioRef.current = next;
              return next;
            });
          }
        }}
        onBlur={persistRatio}
      >
        {collapsible && !collapsed && (
          <button
            type="button"
            className="vertical-resize-collapse-btn"
            title="收到底部，最大化甘特"
            onClick={(e) => {
              e.stopPropagation();
              collapseTop();
            }}
          >
            收起 ▼
          </button>
        )}
      </div>
      <div className="vertical-resize-bottom">{bottom}</div>
    </div>
  );
}
