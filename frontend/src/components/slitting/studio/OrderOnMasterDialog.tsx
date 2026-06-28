type Props = {
  open: boolean;
  masterLabel: string;
  orderCode: string;
  onManualRegion: () => void;
  onAutoFullRegion: () => void;
  onClose: () => void;
};

export function OrderOnMasterDialog({
  open,
  masterLabel,
  orderCode,
  onManualRegion,
  onAutoFullRegion,
  onClose,
}: Props) {
  if (!open) return null;

  return (
    <div className="slitting-modal-backdrop" role="presentation" onClick={onClose}>
      <div className="slitting-modal" role="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>订单放入母卷</h3>
        <p className="slitting-modal-sub">
          将订单 <strong>{orderCode}</strong> 放入母卷 <strong>{masterLabel}</strong>
        </p>
        <p className="slitting-modal-hint">
          可先手动划分区域；若选择不划分，将自动创建与母卷同尺寸的整卷区域并继续放入订单。
        </p>
        <div className="slitting-modal-actions slitting-modal-actions--stack">
          <button type="button" className="btn primary slitting-btn-accent" onClick={onManualRegion}>
            是，手动创建区域
          </button>
          <button type="button" className="btn" onClick={onAutoFullRegion}>
            否，使用整卷区域并放入
          </button>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
        </div>
      </div>
    </div>
  );
}
