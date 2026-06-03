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

/** 左右可调节分割：拖拽中间竖条改变左/右区域宽度比例 */
export function HorizontalResizeSplit({
  storageKey,
  minLeftRatio = 0.18,
  maxLeftRatio = 0.72,
  defaultLeftRatio = DEFAULT_RATIO,
  left,
  right,
  className = '',
}: HorizontalResizeSplitProps) {
  const [leftRatio, setLeftRatio] = useState(() => readRatio(storageKey, defaultLeftRatio));
  const containerRef = useRef<HTMLDivElement>(null);
  const ratioRef = useRef(leftRatio);

  useEffect(() => {
    ratioRef.current = leftRatio;
  }, [leftRatio]);

  const onResizeStart = useCallback(
    (e: React.MouseEvent) => {
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

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    [maxLeftRatio, minLeftRatio, storageKey],
  );

  const leftPct = `${leftRatio * 100}%`;

  return (
    <div ref={containerRef} className={`horizontal-resize-split ${className}`.trim()}>
      <div className="horizontal-resize-left" style={{ flexBasis: leftPct }}>
        {left}
      </div>
      <div
        className="horizontal-resize-handle"
        role="separator"
        aria-orientation="vertical"
        aria-label="调节左右区域宽度"
        tabIndex={0}
        onMouseDown={onResizeStart}
        onKeyDown={(e) => {
          if (e.key === 'ArrowLeft') {
            setLeftRatio((r) => {
              const next = Math.max(minLeftRatio, r - 0.05);
              ratioRef.current = next;
              return next;
            });
          } else if (e.key === 'ArrowRight') {
            setLeftRatio((r) => {
              const next = Math.min(maxLeftRatio, r + 0.05);
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
      <div className="horizontal-resize-right">{right}</div>
    </div>
  );
}
