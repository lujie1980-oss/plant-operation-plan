import { apiFetch, apiJson } from './http';
import type { ModulePermission, WorkspaceMember } from '../types/auth';
import type { AdapterToggle, ModuleToggle } from '../types/workspace';

export async function fetchCurrentUser() {
  return apiJson<import('../types/workspace').CurrentUser>('/api/v1/iam/me');
}

export async function fetchWorkspaceModules(workspaceId: string): Promise<ModuleToggle[]> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/modules`);
}

export async function updateWorkspaceModules(
  workspaceId: string,
  modules: { moduleId: string; enabled: boolean }[],
): Promise<ModuleToggle[]> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/modules`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ modules }),
  });
}

export async function fetchWorkspaceAdapters(workspaceId: string): Promise<AdapterToggle[]> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/adapters`);
}

export async function updateWorkspaceAdapters(
  workspaceId: string,
  adapters: { adapterId: string; enabled: boolean }[],
): Promise<AdapterToggle[]> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/adapters`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ adapters }),
  });
}

export async function fetchWorkspaceMembers(workspaceId: string): Promise<WorkspaceMember[]> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/members`);
}

export async function addWorkspaceMember(
  workspaceId: string,
  payload: { userId: string; role: string },
): Promise<WorkspaceMember> {
  return apiJson(`/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function removeWorkspaceMember(workspaceId: string, userId: string): Promise<void> {
  const res = await apiFetch(
    `/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}`,
    { method: 'DELETE' },
  );
  if (!res.ok) throw new Error(await res.text());
}

export async function fetchMemberPermissions(
  workspaceId: string,
  userId: string,
): Promise<ModulePermission[]> {
  return apiJson(
    `/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}/permissions`,
  );
}

export async function updateMemberPermissions(
  workspaceId: string,
  userId: string,
  permissions: { moduleId: string; accessLevel: string }[],
): Promise<ModulePermission[]> {
  return apiJson(
    `/api/v1/iam/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}/permissions`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ permissions }),
    },
  );
}

export async function fetchAdminUsers() {
  return apiJson<import('../types/auth').AdminUser[]>('/api/v1/admin/users');
}

export async function createAdminUser(payload: {
  userId: string;
  loginName: string;
  displayName: string;
  password: string;
  isSuperAdmin: boolean;
}) {
  return apiJson<import('../types/auth').AdminUser>('/api/v1/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function patchAdminUser(
  userId: string,
  payload: { displayName?: string; status?: string; isSuperAdmin?: boolean; password?: string },
) {
  return apiJson<import('../types/auth').AdminUser>(`/api/v1/admin/users/${encodeURIComponent(userId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function fetchAdminWorkspaces() {
  return apiJson<import('../types/auth').AdminWorkspace[]>('/api/v1/admin/workspaces');
}
