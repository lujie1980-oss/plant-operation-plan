import { useState } from 'react';
import { useAuth } from '../providers/AuthContext';
import './CreateWorkspacePage.css';

export function CreateWorkspacePage() {
  const { currentUser, createWorkspaceAndSelect } = useAuth();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;

    const id = trimmed
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
      .replace(/^-+|-+$/g, '');

    if (!id) {
      setError('名称无效，请输入英文字母或数字');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await createWorkspaceAndSelect({ id, name: trimmed, description: description.trim() || undefined });
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建工作区失败');
      setSaving(false);
    }
  };

  return (
    <div className="create-workspace-page">
      <div className="cw-card">
        <h1 className="cw-title">Plant Operation Plan</h1>
        <p className="cw-subtitle">工厂运营计划系统</p>
        {currentUser && (
          <p className="cw-welcome">
            欢迎，{currentUser.displayName}！你尚未加入任何工作区。
            <br />
            请手动创建一个数据集后再开始计划（不会自动创建）。
          </p>
        )}
        <form onSubmit={handleSubmit} className="cw-form">
          <label className="cw-label">
            工作区名称
            <input
              className="cw-input"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="例如：晶华新材、demo-project"
              autoFocus
              disabled={saving}
            />
          </label>
          <label className="cw-label">
            描述（可选）
            <input
              className="cw-input"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="简要说明用途"
              disabled={saving}
            />
          </label>
          {error && <p className="cw-error">{error}</p>}
          <button type="submit" className="cw-btn" disabled={saving || !name.trim()}>
            {saving ? '创建中…' : '创建工作区'}
          </button>
        </form>
      </div>
    </div>
  );
}
