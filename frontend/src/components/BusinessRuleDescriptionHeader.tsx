import { useEffect, useState } from 'react';
import './BusinessRuleDescriptionHeader.css';

type Props = {
  label: string;
  description: string;
  saving?: boolean;
  onSave: (description: string) => Promise<void>;
};

export function BusinessRuleDescriptionHeader({ label, description, saving, onSave }: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(description);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!editing) {
      setDraft(description);
    }
  }, [description, editing]);

  const startEdit = () => {
    setDraft(description);
    setError(null);
    setEditing(true);
  };

  const cancelEdit = () => {
    setDraft(description);
    setError(null);
    setEditing(false);
  };

  const save = async () => {
    setError(null);
    try {
      await onSave(draft.trim());
      setEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    }
  };

  return (
    <div className="br-desc-header">
      <div className="br-desc-title-row">
        <h3 className="br-rules-config-title">{label}</h3>
        {!editing && (
          <button
            type="button"
            className="br-desc-edit-btn"
            onClick={startEdit}
            disabled={saving}
            aria-label="编辑规则说明"
            title="编辑规则说明"
          >
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true">
              <path
                fill="currentColor"
                d="M13.586 3.586a2 2 0 0 1 2.828 2.828l-8.5 8.5a1 1 0 0 1-.434.262l-3.5 1.25a.75.75 0 0 1-.943-.943l1.25-3.5a1 1 0 0 1 .262-.434l8.5-8.5Z"
              />
              <path
                fill="currentColor"
                d="M12.172 5l2.828 2.828"
                stroke="currentColor"
                strokeWidth="1.2"
                strokeLinecap="round"
              />
            </svg>
          </button>
        )}
      </div>

      {editing ? (
        <div className="br-desc-editor">
          <textarea
            className="input br-desc-textarea"
            rows={4}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="输入规则说明…"
            disabled={saving}
            autoFocus
          />
          <div className="br-desc-editor-actions">
            <button type="button" className="btn" onClick={cancelEdit} disabled={saving}>
              取消
            </button>
            <button type="button" className="btn primary" onClick={() => void save()} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </div>
          {error && <p className="br-desc-error">{error}</p>}
        </div>
      ) : (
        description && <p className="md-tab-desc br-desc-text">{description}</p>
      )}
    </div>
  );
}
