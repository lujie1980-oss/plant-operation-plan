import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  exchangeOidcCode,
  fetchAuthConfig,
  login as apiLogin,
  oidcRedirectUri,
} from '../api/authClient';
import { fetchCurrentUser } from '../api/iamClient';
import { setAuthToken, setRedirectOn401, getAuthToken } from '../api/http';
import { buildEnabledModuleMap } from '../config/workspaceModules';
import type { AuthConfigDto, OidcConfigDto } from '../types/auth';
import type { CurrentUser, WorkspaceCreatePayload, WorkspaceMembership } from '../types/workspace';

const STORAGE_KEY = 'plantops.workspaceId';
const AUTH_INTENT_KEY = 'plantops.authIntent';

function getAuthIntent(): 'switch' | 'logout' | null {
  const value = sessionStorage.getItem(AUTH_INTENT_KEY);
  if (value === 'switch' || value === 'logout') return value;
  return null;
}

function setAuthIntent(intent: 'switch' | 'logout' | null) {
  if (intent) {
    sessionStorage.setItem(AUTH_INTENT_KEY, intent);
  } else {
    sessionStorage.removeItem(AUTH_INTENT_KEY);
  }
}

type AuthState = {
  isLoading: boolean;
  isAuthenticated: boolean;
  needsLogin: boolean;
  devMode: boolean;
  localLoginEnabled: boolean;
  registrationEnabled: boolean;
  oidc: OidcConfigDto | null;
  error: string | null;
  currentUser: CurrentUser | null;
  workspaces: WorkspaceMembership[];
  hasWorkspaces: boolean;
  enabledModules: Record<string, boolean>;
};

type AuthContextValue = AuthState & {
  refresh: () => Promise<void>;
  login: (loginName: string, password: string) => Promise<void>;
  switchUser: () => void;
  logout: () => void;
  clearAuthIntent: () => void;
  showLoginPage: boolean;
  createWorkspaceAndSelect: (payload: WorkspaceCreatePayload) => Promise<string>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}

function useOidcCallback(onComplete: () => Promise<void>) {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    if (!code) return;

    let cancelled = false;
    void (async () => {
      try {
        const token = await exchangeOidcCode(code, oidcRedirectUri());
        if (cancelled) return;
        setAuthToken(token.accessToken);
        const clean = window.location.pathname + (window.location.hash || '#/');
        window.history.replaceState({}, '', clean);
        await onComplete();
      } catch (e) {
        if (!cancelled) {
          console.error('OIDC callback failed', e);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [onComplete]);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [authConfig, setAuthConfig] = useState<AuthConfigDto | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);

  const workspaces = currentUser?.workspaces ?? [];
  const hasWorkspaces = currentUser?.hasWorkspaces ?? false;
  const devMode = authConfig?.devMode ?? true;
  const localLoginEnabled = authConfig?.localLoginEnabled ?? true;
  const oidc = authConfig?.oidc ?? null;
  const needsLogin = authConfig != null && !authConfig.devMode && !getAuthToken() && !isLoading;
  const authIntent = getAuthIntent();
  const showLoginPage = (needsLogin && !isLoading) || authIntent != null;

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
      if (!user.hasWorkspaces) {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setCurrentUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useOidcCallback(load);

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
    setAuthIntent(null);
    await load();
  }, [load]);

  const clearAuthIntent = useCallback(() => {
    setAuthIntent(null);
  }, []);

  const goToLogin = useCallback((intent: 'switch' | 'logout') => {
    setAuthToken(null);
    setCurrentUser(null);
    setAuthIntent(intent);
    window.location.hash = '#/login';
    window.location.reload();
  }, []);

  const switchUser = useCallback(() => {
    goToLogin('switch');
  }, [goToLogin]);

  const logout = useCallback(() => {
    goToLogin('logout');
  }, [goToLogin]);

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
    showLoginPage,
    devMode,
    localLoginEnabled,
    registrationEnabled: authConfig?.registrationEnabled ?? false,
    oidc,
    error,
    currentUser,
    workspaces,
    hasWorkspaces,
    enabledModules,
    refresh: load,
    login,
    switchUser,
    logout,
    clearAuthIntent,
    createWorkspaceAndSelect,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
