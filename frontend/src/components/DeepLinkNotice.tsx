import './DeepLinkNotice.css';

export interface DeepLinkNoticeProps {
  message: string;
  onDismiss?: () => void;
}

/** §17.8: invalid deep-link query feedback (non-blocking). */
export function DeepLinkNotice({ message, onDismiss }: DeepLinkNoticeProps) {
  return (
    <div className="deep-link-notice" role="status">
      <span>{message}</span>
      {onDismiss && (
        <button type="button" className="deep-link-notice-dismiss" onClick={onDismiss}>
          关闭
        </button>
      )}
    </div>
  );
}
