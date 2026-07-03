import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createAdminUser, fetchAdminUsers, patchAdminUser } from '../api/iamClient';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { useAuth } from '../providers/AuthContext';
import type { AdminUser } from '../types/auth';
import './AdminUsersPage.css';

export function AdminUsersPage() {
  const { currentUser } = useAuth();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [userId, setUserId] = useState('');
  const [loginName, setLoginName] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await fetchAdminUsers());
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
      <div className="admin-users-page">
        <PageHeader title="平台用户管理" description="需要 Super Admin 权限。" />
        <p className="admin-forbidden">无权访问</p>
      </div>
    );
  }

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await createAdminUser({
        userId: userId.trim(),
        loginName: loginName.trim(),
        displayName: displayName.trim(),
        password,
        isSuperAdmin,
      });
      setMessage(`已创建用户 ${loginName}`);
      setUserId('');
      setLoginName('');
      setDisplayName('');
      setPassword('');
      setIsSuperAdmin(false);
      await load();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const toggleStatus = async (user: AdminUser) => {
    const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    setBusy(true);
    try {
      await patchAdminUser(user.userId, { status: next });
      await load();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="admin-users-page">
      <PageHeader
        title="平台用户管理"
        description="Super Admin 创建与禁用用户、授予平台超管。"
      />
      <p className="admin-nav">
        <Link to="/admin/workspaces">全部 Workspace →</Link>
      </p>
      <StatusBanner loading={loading} error={error ?? undefined} />
      {message && <p className="admin-hint">{message}</p>}

      <section className="admin-card">
        <h2>新建用户</h2>
        <form className="admin-form" onSubmit={(e) => void onCreate(e)}>
          <label>
            userId
            <input value={userId} onChange={(e) => setUserId(e.target.value)} required pattern="[a-z][a-z0-9_-]+" />
          </label>
          <label>
            登录名
            <input value={loginName} onChange={(e) => setLoginName(e.target.value)} required />
          </label>
          <label>
            显示名
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          </label>
          <label>
            密码
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <label className="admin-checkbox">
            <input type="checkbox" checked={isSuperAdmin} onChange={(e) => setIsSuperAdmin(e.target.checked)} />
            Super Admin
          </label>
          <button type="submit" className="btn primary" disabled={busy}>
            创建
          </button>
        </form>
      </section>

      <section className="admin-card">
        <h2>用户列表</h2>
        <FilterableTable
          tableId="admin-users"
          rows={users}
          rowKey={(u) => u.userId}
          emptyText="暂无用户"
          columns={[
            { key: 'id', header: 'userId', render: (u) => <code>{u.userId}</code> },
            { key: 'login', header: '登录名', render: (u) => u.loginName },
            { key: 'name', header: '显示名', render: (u) => u.displayName },
            {
              key: 'super',
              header: '超管',
              render: (u) => (u.isSuperAdmin ? '是' : '—'),
            },
            { key: 'status', header: '状态', render: (u) => u.status },
            {
              key: 'actions',
              header: '',
              filterable: false,
              render: (u) =>
                u.userId !== currentUser.userId ? (
                  <button type="button" className="btn" disabled={busy} onClick={() => void toggleStatus(u)}>
                    {u.status === 'ACTIVE' ? '禁用' : '启用'}
                  </button>
                ) : null,
            },
          ]}
        />
      </section>
    </div>
  );
}
