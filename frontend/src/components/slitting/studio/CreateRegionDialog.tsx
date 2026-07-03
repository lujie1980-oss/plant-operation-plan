import { useState } from 'react';
import type { SlitDirection } from '../../../types/slittingStudio';

export type RegionCreateMode = 'split' | 'full';

type Props = {
  open: boolean;
  targetLabel: string;
  maxHorizontalMm: number;
  maxVerticalMm: number;
  onConfirm: (mode: RegionCreateMode, direction: SlitDirection, cutSizeMm: number) => void;
  onClose: () => void;
};

export function CreateRegionDialog({
  open,
  targetLabel,
  maxHorizontalMm,
  maxVerticalMm,
  onConfirm,
  onClose,
}: Props) {
  const [mode, setMode] = useState<RegionCreateMode>('split');
  const [direction, setDirection] = useState<SlitDirection>('horizontal');
  const [cutSize, setCutSize] = useState('');

  if (!open) return null;

  const max = direction === 'horizontal' ? maxHorizontalMm : maxVerticalMm;
  const splitHint =
    direction === 'horizontal'
      ? `沿长度分切，尺寸须小于 ${maxHorizontalMm} mm（刀缝 2mm 后生成两块）`
      : `沿宽度分切，尺寸须小于 ${maxVerticalMm} mm（刀缝 2mm 后生成两块）`;

  return (
    <div className="slitting-modal-backdrop" role="presentation" onClick={onClose}>
      <div className="slitting-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>创建区域</h3>
        <p className="slitting-modal-sub">目标：{targetLabel}</p>
        <label className="slitting-modal-field">
          创建方式
          <select className="input" value={mode} onChange={(e) => setMode(e.target.value as RegionCreateMode)}>
            <option value="full">整卷单区域（与母卷/父区域同尺寸）</option>
            <option value="split">一分为二</option>
          </select>
        </label>
        {mode === 'split' ? (
          <>
            <label className="slitting-modal-field">
              分切模式
              <select
                className="input"
                value={direction}
                onChange={(e) => setDirection(e.target.value as SlitDirection)}
              >
                <option value="horizontal">横向（沿长度分切）</option>
                <option value="vertical">纵向（沿宽度分切）</option>
              </select>
            </label>
            <label className="slitting-modal-field">
              分切尺寸 (mm)
              <input
                className="input"
                type="number"
                min={1}
                max={max - 2}
                value={cutSize}
                onChange={(e) => setCutSize(e.target.value)}
              />
            </label>
            <p className="slitting-modal-hint">{splitHint}</p>
          </>
        ) : (
          <p className="slitting-modal-hint">
            将生成一块覆盖整个父卷的区域（{maxHorizontalMm}×{maxVerticalMm} mm，长×宽）。
          </p>
        )}
        <div className="slitting-modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="button"
            className="btn primary slitting-btn-accent"
            onClick={() => {
              if (mode === 'full') {
                onConfirm('full', direction, 0);
                onClose();
                return;
              }
              const v = Number(cutSize);
              if (!Number.isFinite(v) || v <= 0) return;
              onConfirm('split', direction, v);
              setCutSize('');
              onClose();
            }}
          >
            确认
          </button>
        </div>
      </div>
    </div>
  );
}
