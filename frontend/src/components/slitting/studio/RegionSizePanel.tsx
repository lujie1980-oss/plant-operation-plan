import { useEffect, useMemo, useState } from 'react';
import type { SlittingAssignment, SlittingRollNode } from '../../../types/slitting';
import { nodeLength, nodeWidth } from '../../../utils/slitting/studioGeometry';

type Props = {
  region: SlittingRollNode;
  parent: SlittingRollNode;
  assignment: SlittingAssignment;
  onApply: (lengthMm: number, widthMm: number) => { ok: true } | { ok: false; message: string };
  onClose: () => void;
};

export function RegionSizePanel({ region, parent, assignment, onApply, onClose }: Props) {
  const maxL = useMemo(
    () => Math.max(1, nodeLength(parent) - assignment.posXMm),
    [parent, assignment.posXMm],
  );
  const maxW = useMemo(
    () => Math.max(1, nodeWidth(parent) - assignment.posYMm),
    [parent, assignment.posYMm],
  );

  const [lengthMm, setLengthMm] = useState(() => String(nodeLength(region)));
  const [widthMm, setWidthMm] = useState(() => String(nodeWidth(region)));
  const [localErr, setLocalErr] = useState<string | null>(null);

  useEffect(() => {
    setLengthMm(String(nodeLength(region)));
    setWidthMm(String(nodeWidth(region)));
    setLocalErr(null);
  }, [region.nodeId, region.lengthMm, region.widthMm]);

  const submit = () => {
    const L = Number(lengthMm);
    const W = Number(widthMm);
    if (!Number.isFinite(L) || L <= 0 || !Number.isFinite(W) || W <= 0) {
      setLocalErr('请输入有效的正数尺寸');
      return;
    }
    if (L > maxL + 0.01 || W > maxW + 0.01) {
      setLocalErr(`超出父节点可用空间（最大 ${maxL.toFixed(0)}×${maxW.toFixed(0)} mm）`);
      return;
    }
    const result = onApply(L, W);
    if (!result.ok) {
      setLocalErr(result.message);
      return;
    }
    setLocalErr(null);
  };

  return (
    <aside className="slitting-region-size-panel" aria-label="区域尺寸">
      <div className="slitting-region-size-head">
        <strong>调整区域尺寸</strong>
        <button type="button" className="slitting-region-size-close" onClick={onClose} aria-label="关闭">
          ×
        </button>
      </div>
      <p className="slitting-panel-hint">
        父节点可用：{maxL.toFixed(0)} × {maxW.toFixed(0)} mm（长×宽）
      </p>
      <div className="slitting-region-size-fields">
        <label>
          长度 (mm)
          <input
            className="input"
            type="number"
            min={1}
            max={maxL}
            step={1}
            value={lengthMm}
            onChange={(e) => setLengthMm(e.target.value)}
          />
        </label>
        <label>
          宽度 (mm)
          <input
            className="input"
            type="number"
            min={1}
            max={maxW}
            step={1}
            value={widthMm}
            onChange={(e) => setWidthMm(e.target.value)}
          />
        </label>
      </div>
      {localErr ? <p className="slitting-region-size-err">{localErr}</p> : null}
      <button type="button" className="btn primary slitting-btn-accent" onClick={submit}>
        应用尺寸
      </button>
    </aside>
  );
}
