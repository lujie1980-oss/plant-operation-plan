import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  fetchWorkspaceAdapters,
  fetchWorkspaceModules,
  updateWorkspaceAdapters,
  updateWorkspaceModules,
} from '../api/iamClient';
import { WorkspaceMembersPanel } from '../components/WorkspaceMembersPanel';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { WORKSPACE_MODULE_CATEGORIES } from '../config/workspaceModules';
import { useAuth } from '../providers/AuthContext';
import type { AdapterToggle, ModuleToggle } from '../types/workspace';
import './WorkspaceSettingsPage.css';

function isWorkspaceAdmin(role: string | undefined, isSuperAdmin: boolean) {
  return isSuperAdmin || role === 'OWNER' || role === 'WS_ADMIN';
}

export function WorkspaceSettingsPage() {
  const { workspaceId = '' } = useParams<{ workspaceId: string }>();
  const { currentUser, workspaces, refresh } = useAuth();
  const membership = workspaces.find((w) => w.workspaceId === workspaceId);
  const canEdit = isWorkspaceAdmin(membership?.role, currentUser?.isSuperAdmin ?? false);

  const [modules, setModules] = useState<ModuleToggle[]>([]);
  const [adapters, setAdapters] = useState<AdapterToggle[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const [mods, adps] = await Promise.all([
        fetchWorkspaceModules(workspaceId),
        fetchWorkspaceAdapters(workspaceId),
      ]);
      setModules(mods);
      setAdapters(adps);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    void load();
  }, [load]);

  const modulesByCategory = useMemo(() => {
    const map = new Map<string, ModuleToggle[]>();
    for (const cat of WORKSPACE_MODULE_CATEGORIES) {
      map.set(cat.id, modules.filter((m) => m.categoryId === cat.id));
    }
    return map;
  }, [modules]);

  const toggleModule = (moduleId: string, enabled: boolean) => {
    setModules((prev) => prev.map((m) => (m.moduleId === moduleId ? { ...m, enabled } : m)));
  };

  const toggleAdapter = (adapterId: string, enabled: boolean) => {
    setAdapters((prev) => prev.map((a) => (a.adapterId === adapterId ? { ...a, enabled } : a)));
  };

  const onSave = async () => {
    if (!canEdit || !workspaceId) return;
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      await updateWorkspaceModules(
        workspaceId,
        modules.map((m) => ({ moduleId: m.moduleId, enabled: m.enabled })),
      );
      await updateWorkspaceAdapters(
        workspaceId,
        adapters.map((a) => ({ adapterId: a.adapterId, enabled: a.enabled })),
      );
      await refresh();
      setMessage('模块与适配器配置已保存；侧栏将立即反映变更。');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const title = membership?.name ?? workspaceId;

  return (
    <div className="workspace-settings-page">
      <PageHeader
        title={`数据集设置：${title}`}
        description="启用或关闭计划模块与数据集成适配器。关闭后对应侧栏入口隐藏，API 返回 403 MODULE_DISABLED。"
      />
      <p className="workspace-settings-back">
        <Link to="/workspaces">← 返回数据集管理</Link>
      </p>
      <StatusBanner loading={loading} error={error ?? undefined} />
      {message && <p className="workspace-settings-hint">{message}</p>}
      {!canEdit && !loading && (
        <p className="workspace-settings-readonly">当前角色为只读（需 OWNER 或 WS_ADMIN 方可修改）。</p>
      )}

      <section className="workspace-settings-card">
        <h2>计划与集成模块</h2>
        {WORKSPACE_MODULE_CATEGORIES.map((cat) => {
          const items = modulesByCategory.get(cat.id) ?? [];
          if (items.length === 0) return null;
          return (
            <div key={cat.id} className="workspace-settings-group">
              <h3>{cat.name}</h3>
              <ul className="workspace-settings-checklist">
                {items.map((m) => (
                  <li key={m.moduleId}>
                    <label>
                      <input
                        type="checkbox"
                        checked={m.enabled}
                        disabled={!canEdit || saving}
                        onChange={(e) => toggleModule(m.moduleId, e.target.checked)}
                      />
                      <span className="workspace-settings-label">{m.name}</span>
                      <span className="workspace-settings-id">{m.moduleId}</span>
                    </label>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </section>

      <section className="workspace-settings-card">
        <h2>数据集成适配器</h2>
        <p className="workspace-settings-note">适配器开关在 MOD-DI 启用时生效。</p>
        <ul className="workspace-settings-checklist">
          {adapters.map((a) => (
            <li key={a.adapterId}>
              <label>
                <input
                  type="checkbox"
                  checked={a.enabled}
                  disabled={!canEdit || saving}
                  onChange={(e) => toggleAdapter(a.adapterId, e.target.checked)}
                />
                <span className="workspace-settings-label">{a.name}</span>
                <span className="workspace-settings-id">{a.adapterId}</span>
              </label>
            </li>
          ))}
        </ul>
      </section>

      {canEdit && (
        <div className="workspace-settings-actions">
          <button type="button" className="btn primary" disabled={saving || loading} onClick={() => void onSave()}>
            {saving ? '保存中…' : '保存模块设置'}
          </button>
        </div>
      )}

      <WorkspaceMembersPanel workspaceId={workspaceId} canEdit={canEdit} />
    </div>
  );
}
