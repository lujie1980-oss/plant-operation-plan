import './StatusBanner.css';

interface StatusBannerProps {
  loading?: boolean;
  error?: string | null;
  success?: string | null;
}

export function StatusBanner({ loading, error, success }: StatusBannerProps) {
  const hasInline = Boolean(error || success);

  if (!loading && !hasInline) {
    return null;
  }

  return (
    <>
      {loading && (
        <div className="status-loading-toast" role="status" aria-live="polite">
          <span className="status loading">处理中…</span>
        </div>
      )}
      {hasInline && (
        <div className="status-banner">
          {error && <span className="status error">{error}</span>}
          {success && <span className="status success">{success}</span>}
        </div>
      )}
    </>
  );
}
