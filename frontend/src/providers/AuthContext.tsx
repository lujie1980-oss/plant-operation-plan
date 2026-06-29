import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchAuthConfig, login as apiLogin } from '../api/authClient';
import { fetchCurrentUser } from '../api/iamClient';
import { setAuthToken, setRedirectOn401, getAuthToken } from '../api/http';
import { buildEnabledModuleMap } from '../config/workspaceModules';
import type { AuthConfigDto } from '../types/auth';
import type { CurrentUser, WorkspaceCreatePayload, WorkspaceMembership } from '../types/workspace';

const STORAGE_KEY = 'plantops.workspaceId';

type AuthState = {
  isLoading: boolean;
  isAuthenticated: boolean;
  needsLogin: boolean;
  devMode: boolean;
  registrationEnabled: boolean;
  error: string | null;
  currentUser: CurrentUser | null;
  workspaces: WorkspaceMembership[];
  hasWorkspaces: boolean;
  enabledModules: Record<string, boolean>;
};

type AuthContextValue = AuthState & {
  refresh: () => Promise<void>;
  login: (loginName: string, password: string) => Promise<void>;
  logout: () => void;
  createWorkspaceAndSelect: (payload: WorkspaceCreatePayload) => Promise<string>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [authConfig, setAuthConfig] = useState<AuthConfigDto | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);

  const workspaces = currentUser?.workspaces ?? [];
  const hasWorkspaces = currentUser?.hasWorkspaces ?? false;
  const devMode = authConfig?.devMode ?? true;
  const needsLogin = authConfig != null && !authConfig.devMode && !getAuthToken() && !isLoading;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const config = await fetchAuthConfig();
      setAuthConfig(config);
      setRedirectOn401(!config.devMode);
      if (!config.devMode && !getAuthToken()) {
        setCurrentUser(null);
        return;
      }
      const user = await fetchCurrentUser();
      setCurrentUser(user);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setCurrentUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const enabledModules = useMemo((): Record<string, boolean> => {
    const wsId = localStorage.getItem(STORAGE_KEY);
    if (!wsId) return {};
    const member = workspaces.find((w) => w.workspaceId === wsId);
    if (!member) return {};
    return buildEnabledModuleMap(member.enabledModules);
  }, [workspaces]);

  const login = useCallback(async (loginName: string, password: string) => {
    const token = await apiLogin(loginName, password);
    setAuthToken(token.accessToken);
    await load();
  }, [load]);

  const logout = useCallback(() => {
    setAuthToken(null);
    setCurrentUser(null);
    if (!devMode) {
      window.location.hash = '#/login';
      window.location.reload();
    }
  }, [devMode]);

  const createWorkspaceAndSelect = useCallback(async (payload: WorkspaceCreatePayload): Promise<string> => {
    const res = await fetch('/api/v1/workspaces', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {}),
      },
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
    needsLogin: needsLogin && !isLoading,
    devMode,
    registrationEnabled: authConfig?.registrationEnabled ?? false,
    error,
    currentUser,
    workspaces,
    hasWorkspaces,
    enabledModules,
    refresh: load,
    login,
    logout,
    createWorkspaceAndSelect,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
