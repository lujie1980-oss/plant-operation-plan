import { useEffect, useState } from 'react';
import { api } from '../api/client';
import './AssignLineDialog.css';

export interface AssignLineDialogProps {
  open: boolean;
  title: string;
  description?: string;
  sessionId: string | null;
  operationId: string | null;
  /** 无 session 或单工序时可直接传入候选产线 */
  candidateLineIds?: string[];
  onConfirm: (lineId: string) => void;
  onCancel: () => void;
  busy?: boolean;
}

export function AssignLineDialog({
  open,
  title,
  description,
  sessionId,
  operationId,
  candidateLineIds,
  onConfirm,
  onCancel,
  busy,
}: AssignLineDialogProps) {
  const [lines, setLines] = useState<string[]>([]);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setSelected('');
      setError(null);
      return;
    }
    if (candidateLineIds && candidateLineIds.length > 0) {
      setLines(candidateLineIds);
      setSelected(candidateLineIds[0] ?? '');
      return;
    }
    if (!sessionId || !operationId) {
      setLines([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    void api
      .scheduleSessionCandidateLines(sessionId, operationId)
      .then((ids) => {
        if (cancelled) return;
        setLines(ids);
        setSelected(ids[0] ?? '');
      })
      .catch((e) => {
        if (cancelled) return;
        setLines([]);
        setError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, sessionId, operationId, candidateLineIds]);

  if (!open) return null;

  const confirmDisabled = busy || loading || !selected;

  return (
    <div className="assign-line-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="assign-line-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="assign-line-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h4 id="assign-line-title">{title}</h4>
        {description && <p className="assign-line-desc">{description}</p>}
        {loading && <p className="assign-line-muted">加载可选产线…</p>}
        {error && <p className="assign-line-error">{error}</p>}
        {!loading && !error && lines.length === 0 && (
          <p className="assign-line-error">该工序无可选产线</p>
        )}
        {lines.length > 0 && (
          <label className="assign-line-field">
            <span>机台</span>
            <select
              className="input"
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
              disabled={busy}
            >
              {lines.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
        )}
        <div className="assign-line-actions">
          <button type="button" className="btn" onClick={onCancel} disabled={busy}>
            取消
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={confirmDisabled}
            onClick={() => onConfirm(selected)}
          >
            确认
          </button>
        </div>
      </div>
    </div>
  );
}
