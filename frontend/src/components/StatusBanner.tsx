interface StatusBannerProps {
  loading?: boolean;
  error?: string | null;
  success?: string | null;
}

export function StatusBanner({ loading, error, success }: StatusBannerProps) {
  if (!loading && !error && !success) return null;
  return (
    <div className="status-banner">
      {loading && <span className="status loading">处理中…</span>}
      {error && <span className="status error">{error}</span>}
      {success && <span className="status success">{success}</span>}
    </div>
  );
}
