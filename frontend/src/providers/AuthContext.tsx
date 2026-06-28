import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CurrentUser, WorkspaceMembership, WorkspaceCreatePayload } from '../types/workspace';

const STORAGE_KEY = 'plantops.workspaceId';

type AuthState = {
  isLoading: boolean;
  isAuthenticated: boolean;
  error: string | null;
  currentUser: CurrentUser | null;
  workspaces: WorkspaceMembership[];
  hasWorkspaces: boolean;
  enabledModules: Record<string, boolean>;
};

type AuthContextValue = AuthState & {
  refresh: () => Promise<void>;
  createWorkspaceAndSelect: (payload: WorkspaceCreatePayload) => Promise<string>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}

async function fetchCurrentUser(): Promise<CurrentUser> {
  const res = await fetch('/api/v1/iam/me', { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const workspaces = currentUser?.workspaces ?? [];
  const hasWorkspaces = currentUser?.hasWorkspaces ?? false;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const user = await fetchCurrentUser();
      setCurrentUser(user);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setCurrentUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const enabledModules = useMemo((): Record<string, boolean> => {
    const wsId = localStorage.getItem(STORAGE_KEY);
    if (!wsId) return {};
    const member = workspaces.find((w) => w.workspaceId === wsId);
    if (!member) return {};
    const map: Record<string, boolean> = {};
    for (const modId of member.enabledModules) {
      map[modId] = true;
    }
    return map;
  }, [workspaces]);

  const createWorkspaceAndSelect = useCallback(async (payload: WorkspaceCreatePayload): Promise<string> => {
    const res = await fetch('/api/v1/workspaces', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await res.text());
    const created = await res.json() as { workspaceId: string };
    localStorage.setItem(STORAGE_KEY, created.workspaceId);
    await load();
    window.location.reload();
    return created.workspaceId;
  }, [load]);

  const value: AuthContextValue = {
    isLoading,
    isAuthenticated: currentUser != null && !error,
    error,
    currentUser,
    workspaces,
    hasWorkspaces,
    enabledModules,
    refresh: load,
    createWorkspaceAndSelect,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
