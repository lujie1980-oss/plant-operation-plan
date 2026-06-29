import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchAdminWorkspaces } from '../api/iamClient';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { useAuth } from '../providers/AuthContext';
import type { AdminWorkspace } from '../types/auth';
import './AdminUsersPage.css';

export function AdminWorkspacesPage() {
  const { currentUser } = useAuth();
  const [workspaces, setWorkspaces] = useState<AdminWorkspace[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setWorkspaces(await fetchAdminWorkspaces());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (!currentUser?.isSuperAdmin) {
    return (
      <div className="admin-workspaces-page">
        <PageHeader title="平台 Workspace" description="需要 Super Admin 权限。" />
        <p className="admin-forbidden">无权访问</p>
      </div>
    );
  }

  return (
    <div className="admin-workspaces-page">
      <PageHeader title="平台 Workspace 总览" description="查看所有数据集、Owner 与成员数。" />
      <p className="admin-nav">
        <Link to="/admin/users">← 用户管理</Link>
      </p>
      <StatusBanner loading={loading} error={error ?? undefined} />
      <section className="admin-card">
        <FilterableTable
          tableId="admin-workspaces"
          rows={workspaces}
          rowKey={(w) => w.workspaceId}
          emptyText="暂无 Workspace"
          columns={[
            { key: 'id', header: 'ID', render: (w) => <code>{w.workspaceId}</code> },
            { key: 'name', header: '名称', render: (w) => w.name },
            { key: 'owner', header: 'Owner', render: (w) => w.ownerUserId ?? '—' },
            { key: 'type', header: '类型', render: (w) => w.workspaceType ?? '—' },
            { key: 'members', header: '成员数', render: (w) => w.memberCount },
            {
              key: 'settings',
              header: '',
              filterable: false,
              render: (w) => (
                <Link to={`/workspaces/${w.workspaceId}/settings`} className="btn">
                  设置
                </Link>
              ),
            },
          ]}
        />
      </section>
    </div>
  );
}
