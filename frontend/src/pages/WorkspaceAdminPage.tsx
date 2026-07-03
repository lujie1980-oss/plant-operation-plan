import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { useWorkspace } from '../context/WorkspaceContext';
import { useAuth } from '../providers/AuthContext';
import './WorkspaceAdminPage.css';

export function WorkspaceAdminPage() {
  const { workspaces, loading, error, createWorkspace, deleteWorkspace, refreshWorkspaces } =
    useWorkspace();
  const { currentUser, workspaces: authWorkspaces } = useAuth();
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const created = await createWorkspace({
        id: id.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
      });
      setMessage(`已创建数据集：${created.name}`);
      setId('');
      setName('');
      setDescription('');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (workspaceId: string, workspaceName: string) => {
    if (workspaceId === 'default') {
      return;
    }
    const ok = window.confirm(
      `确定删除数据集「${workspaceName}」？该空间内全部业务数据将被永久删除，且不可恢复。`,
    );
    if (!ok) {
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await deleteWorkspace(workspaceId);
      setMessage(`已删除：${workspaceName}`);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="workspace-admin-page">
      <PageHeader
        title="数据集管理"
        description="每个数据集拥有独立的主数据、计划与排程结果；切换顶栏数据集后全站 API 自动隔离。"
      />
      <StatusBanner loading={loading} error={error ?? undefined} />
      {message && <p className="workspace-admin-hint">{message}</p>}

      <section className="workspace-admin-card">
        <h2>新建数据集</h2>
        <form className="workspace-admin-form" onSubmit={onCreate}>
          <label>
            ID（小写、数字、连字符）
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="dunan-lite"
              pattern="[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?"
              required
            />
          </label>
          <label>
            显示名称
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label>
            说明（可选）
            <input value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
          <button type="submit" className="btn primary" disabled={busy}>
            创建
          </button>
        </form>
      </section>

      <section className="workspace-admin-card">
        <div className="workspace-admin-list-head">
          <h2>已有数据集</h2>
          <button type="button" className="btn" onClick={() => void refreshWorkspaces()} disabled={busy}>
            刷新
          </button>
        </div>
        <FilterableTable
          tableId="workspace-admin"
          tableClassName="workspace-admin-table"
          wrapClassName="ft-table-wrap"
          rows={workspaces}
          rowKey={(w) => w.workspaceId}
          emptyText="暂无数据集"
          columns={[
            {
              key: 'id',
              header: 'ID',
              render: (w) => <code>{w.workspaceId}</code>,
            },
            {
              key: 'name',
              header: '名称',
              render: (w) => (
                <>
                  {w.name}
                  {w.isDefault ? '（默认）' : ''}
                </>
              ),
            },
            { key: 'description', header: '说明', render: (w) => w.description ?? '—' },
            {
              key: 'actions',
              header: '',
              filterable: false,
              render: (w) => {
                const membership = authWorkspaces.find((m) => m.workspaceId === w.workspaceId);
                const canManage =
                  currentUser?.isSuperAdmin ||
                  membership?.role === 'OWNER' ||
                  membership?.role === 'WS_ADMIN';
                return (
                  <span className="workspace-admin-actions">
                    {canManage && (
                      <Link to={`/workspaces/${w.workspaceId}/settings`} className="btn">
                        模块设置
                      </Link>
                    )}
                    {!w.isDefault ? (
                      <button
                        type="button"
                        className="btn danger"
                        disabled={busy}
                        onClick={() => void onDelete(w.workspaceId, w.name)}
                      >
                        删除
                      </button>
                    ) : null}
                  </span>
                );
              },
            },
          ]}
        />
      </section>
    </div>
  );
}
