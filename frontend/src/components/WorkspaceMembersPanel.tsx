import { FormEvent, useCallback, useEffect, useState } from 'react';
import {
  addWorkspaceMember,
  fetchMemberPermissions,
  fetchWorkspaceMembers,
  removeWorkspaceMember,
  updateMemberPermissions,
} from '../api/iamClient';
import type { ModulePermission, WorkspaceMember } from '../types/auth';

const ACCESS_LEVELS = ['NONE', 'VIEW', 'EDIT'] as const;

type Props = {
  workspaceId: string;
  canEdit: boolean;
};

export function WorkspaceMembersPanel({ workspaceId, canEdit }: Props) {
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [permissions, setPermissions] = useState<ModulePermission[]>([]);
  const [newUserId, setNewUserId] = useState('');
  const [newRole, setNewRole] = useState('MEMBER');
  const [busy, setBusy] = useState(false);

  const loadMembers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setMembers(await fetchWorkspaceMembers(workspaceId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    void loadMembers();
  }, [loadMembers]);

  const loadPermissions = async (userId: string) => {
    setSelectedUserId(userId);
    setPermissions(await fetchMemberPermissions(workspaceId, userId));
  };

  const onAdd = async (e: FormEvent) => {
    e.preventDefault();
    if (!canEdit) return;
    setBusy(true);
    try {
      await addWorkspaceMember(workspaceId, { userId: newUserId.trim(), role: newRole });
      setNewUserId('');
      await loadMembers();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const onRemove = async (userId: string) => {
    if (!canEdit || !window.confirm(`移除成员 ${userId}？`)) return;
    setBusy(true);
    try {
      await removeWorkspaceMember(workspaceId, userId);
      if (selectedUserId === userId) {
        setSelectedUserId(null);
        setPermissions([]);
      }
      await loadMembers();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const onSavePermissions = async () => {
    if (!canEdit || !selectedUserId) return;
    setBusy(true);
    try {
      const updated = await updateMemberPermissions(
        workspaceId,
        selectedUserId,
        permissions.map((p) => ({ moduleId: p.moduleId, accessLevel: p.accessLevel })),
      );
      setPermissions(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="workspace-settings-card">
      <h2>成员与模块权限</h2>
      <p className="workspace-settings-note">OWNER / WS_ADMIN 对已启用模块 implicit EDIT；MEMBER 须配置 NONE / VIEW / EDIT。</p>
      {error && <p className="workspace-settings-readonly">{error}</p>}
      {loading ? <p>加载成员…</p> : (
        <ul className="workspace-settings-checklist">
          {members.map((m) => (
            <li key={m.userId}>
              <div className="workspace-member-row">
                <button type="button" className="btn linkish" onClick={() => void loadPermissions(m.userId)}>
                  {m.displayName} <span className="workspace-settings-id">({m.role})</span>
                </button>
                {canEdit && m.role !== 'OWNER' && (
                  <button type="button" className="btn danger" disabled={busy} onClick={() => void onRemove(m.userId)}>
                    移除
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {canEdit && (
        <form className="workspace-member-add" onSubmit={(e) => void onAdd(e)}>
          <input
            value={newUserId}
            onChange={(e) => setNewUserId(e.target.value)}
            placeholder="userId（须已存在）"
            required
          />
          <select value={newRole} onChange={(e) => setNewRole(e.target.value)}>
            <option value="MEMBER">MEMBER</option>
            <option value="WS_ADMIN">WS_ADMIN</option>
          </select>
          <button type="submit" className="btn" disabled={busy}>
            添加成员
          </button>
        </form>
      )}

      {selectedUserId && permissions.length > 0 && (
        <div className="workspace-permissions-matrix">
          <h3>权限矩阵：{selectedUserId}</h3>
          {members.find((m) => m.userId === selectedUserId)?.role !== 'MEMBER' ? (
            <p className="workspace-settings-note">管理员角色拥有 implicit EDIT，无需矩阵。</p>
          ) : (
            <>
              <table className="workspace-permissions-table">
                <thead>
                  <tr>
                    <th>模块</th>
                    <th>权限</th>
                  </tr>
                </thead>
                <tbody>
                  {permissions.map((p) => (
                    <tr key={p.moduleId}>
                      <td>{p.name}</td>
                      <td>
                        <select
                          value={p.accessLevel}
                          disabled={!canEdit || busy}
                          onChange={(e) =>
                            setPermissions((prev) =>
                              prev.map((row) =>
                                row.moduleId === p.moduleId
                                  ? { ...row, accessLevel: e.target.value }
                                  : row,
                              ),
                            )
                          }
                        >
                          {ACCESS_LEVELS.map((level) => (
                            <option key={level} value={level}>
                              {level}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {canEdit && (
                <button type="button" className="btn primary" disabled={busy} onClick={() => void onSavePermissions()}>
                  保存权限矩阵
                </button>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}
