import { useCallback, useEffect, useRef, useState } from 'react';
import './VerticalResizeSplit.css';

const DEFAULT_RATIO = 0.4;

interface VerticalResizeSplitProps {
  storageKey?: string;
  minTopRatio?: number;
  maxTopRatio?: number;
  top: React.ReactNode;
  bottom: React.ReactNode;
  className?: string;
}

function readRatio(key: string | undefined, fallback: number): number {
  if (!key) return fallback;
  try {
    const n = parseFloat(localStorage.getItem(key) ?? '');
    if (Number.isFinite(n) && n >= 0.15 && n <= 0.85) return n;
  } catch {
    /* ignore */
  }
  return fallback;
}

/** 上下可调节分割：拖拽中间横条改变上/下区域高度比例 */
export function VerticalResizeSplit({
  storageKey,
  minTopRatio = 0.2,
  maxTopRatio = 0.75,
  top,
  bottom,
  className = '',
}: VerticalResizeSplitProps) {
  const [topRatio, setTopRatio] = useState(() => readRatio(storageKey, DEFAULT_RATIO));
  const containerRef = useRef<HTMLDivElement>(null);
  const ratioRef = useRef(topRatio);

  useEffect(() => {
    ratioRef.current = topRatio;
  }, [topRatio]);

  const onResizeStart = useCallback(
    (e: React.MouseEvent) => {
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
      };

      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        if (storageKey) {
          localStorage.setItem(storageKey, String(ratioRef.current));
        }
      };

      document.body.style.cursor = 'row-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    [maxTopRatio, minTopRatio, storageKey],
  );

  const topPct = `${topRatio * 100}%`;

  return (
    <div
      ref={containerRef}
      className={`vertical-resize-split ${className}`.trim()}
    >
      <div className="vertical-resize-top" style={{ flexBasis: topPct }}>
        {top}
      </div>
      <div
        className="vertical-resize-handle"
        role="separator"
        aria-orientation="horizontal"
        aria-label="调节订单列表与满足链区域高度"
        tabIndex={0}
        onMouseDown={onResizeStart}
        onKeyDown={(e) => {
          if (e.key === 'ArrowUp') {
            setTopRatio((r) => {
              const next = Math.max(minTopRatio, r - 0.05);
              ratioRef.current = next;
              return next;
            });
          } else if (e.key === 'ArrowDown') {
            setTopRatio((r) => {
              const next = Math.min(maxTopRatio, r + 0.05);
              ratioRef.current = next;
              return next;
            });
          }
        }}
        onBlur={() => {
          if (storageKey) {
            localStorage.setItem(storageKey, String(ratioRef.current));
          }
        }}
      />
      <div className="vertical-resize-bottom">{bottom}</div>
    </div>
  );
}
