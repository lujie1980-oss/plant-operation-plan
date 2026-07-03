import { createPortal } from 'react-dom';
import './TableRowHoverTip.css';

export type TableRowHoverTipState = {
  lines: string[];
  x: number;
  y: number;
} | null;

type TableRowHoverTipProps = {
  tip: TableRowHoverTipState;
};

export function TableRowHoverTip({ tip }: TableRowHoverTipProps) {
  if (!tip || tip.lines.length === 0) return null;

  const maxX = typeof window !== 'undefined' ? window.innerWidth - 320 : tip.x;
  const maxY = typeof window !== 'undefined' ? window.innerHeight - 24 : tip.y;
  const left = Math.min(tip.x + 12, maxX);
  const top = Math.min(tip.y + 12, maxY);

  return createPortal(
    <div className="ft-row-hover-tip" style={{ left, top }} role="tooltip">
      {tip.lines.map((line) => (
        <div key={line} className="ft-row-hover-tip-line">
          {line}
        </div>
      ))}
    </div>,
    document.body,
  );
}
