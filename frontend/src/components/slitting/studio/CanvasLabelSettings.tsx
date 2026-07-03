import { useState } from 'react';
import {
  CANVAS_LABEL_OPTIONS,
  loadCanvasLabelKeys,
  saveCanvasLabelKeys,
  type CanvasLabelKey,
} from '../../../utils/slitting/canvasDisplayConfig';

type Props = {
  keys: CanvasLabelKey[];
  onChange: (keys: CanvasLabelKey[]) => void;
};

export function CanvasLabelSettings({ keys, onChange }: Props) {
  const [open, setOpen] = useState(false);

  const toggle = (key: CanvasLabelKey) => {
    const next = keys.includes(key) ? keys.filter((k) => k !== key) : [...keys, key];
    onChange(next);
    saveCanvasLabelKeys(next);
  };

  return (
    <div className="slitting-canvas-label-settings">
      <button type="button" className="btn btn--sm" onClick={() => setOpen((v) => !v)}>
        标注字段
      </button>
      {open ? (
        <div className="slitting-canvas-label-popover">
          {CANVAS_LABEL_OPTIONS.map((opt) => (
            <label key={opt.key} className="slitting-canvas-label-option">
              <input
                type="checkbox"
                checked={keys.includes(opt.key)}
                onChange={() => toggle(opt.key)}
              />
              {opt.label}
            </label>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export function useCanvasLabelKeys(): [CanvasLabelKey[], (keys: CanvasLabelKey[]) => void] {
  const [keys, setKeys] = useState<CanvasLabelKey[]>(() => loadCanvasLabelKeys());
  return [keys, setKeys];
}
