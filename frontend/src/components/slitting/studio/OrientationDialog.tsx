import type { OrderOrientation } from '../../../types/slittingStudio';
import type { ChildSlittingOrder } from '../../../types/slitting';

type Props = {
  open: boolean;
  order: ChildSlittingOrder | null;
  regionLabel: string;
  onConfirm: (orientation: OrderOrientation) => void;
  onClose: () => void;
};

export function OrientationDialog({ open, order, regionLabel, onConfirm, onClose }: Props) {
  if (!open || !order) return null;

  return (
    <div className="slitting-modal-backdrop" role="presentation" onClick={onClose}>
      <div className="slitting-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>放入订单</h3>
        <p className="slitting-modal-sub">
          {order.orderCode} · {order.widthMm}×{order.lengthMm} mm → {regionLabel}
        </p>
        <p className="slitting-modal-hint">选择纹路方向后校验长宽是否适配区域。</p>
        <div className="slitting-modal-actions slitting-modal-actions--stack">
          <button type="button" className="btn primary slitting-btn-accent" onClick={() => onConfirm('horizontal')}>
            横向（订单长度沿区域长度）
          </button>
          <button type="button" className="btn" onClick={() => onConfirm('vertical')}>
            纵向（旋转 90° 放入）
          </button>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
        </div>
      </div>
    </div>
  );
}
