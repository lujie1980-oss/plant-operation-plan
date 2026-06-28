import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { Workspace, WorkspaceCreatePayload } from '../types/workspace';
import { useAuth } from '../providers/AuthContext';

const STORAGE_KEY = 'plantops.workspaceId';

type WorkspaceContextValue = {
  workspaceId: string;
  workspaces: Workspace[];
  loading: boolean;
  error: string | null;
  setWorkspaceId: (id: string) => void;
  refreshWorkspaces: () => Promise<void>;
  createWorkspace: (payload: WorkspaceCreatePayload) => Promise<Workspace>;
  deleteWorkspace: (id: string) => Promise<void>;
};

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

function mapToWorkspace(w: { workspaceId: string; name: string }): Workspace {
  return {
    workspaceId: w.workspaceId,
    name: w.name,
    description: null,
    createdAt: '',
    isDefault: false,
  };
}

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { workspaces: authWorkspaces, refresh: refreshAuth } = useAuth();

  const workspaceList = useMemo(
    () => authWorkspaces.map(mapToWorkspace),
    [authWorkspaces],
  );

  const [workspaceId, setWorkspaceIdState] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && authWorkspaces.some((w) => w.workspaceId === stored)) return stored;
    return authWorkspaces[0]?.workspaceId ?? '';
  });

  const [loading] = useState(false);
  const [error] = useState<string | null>(null);

  useEffect(() => {
    if (!authWorkspaces.some((w) => w.workspaceId === workspaceId)) {
      const next = authWorkspaces[0]?.workspaceId ?? '';
      if (next) {
        setWorkspaceIdState(next);
        localStorage.setItem(STORAGE_KEY, next);
      }
    }
  }, [authWorkspaces, workspaceId]);

  const setWorkspaceId = useCallback((id: string) => {
    setWorkspaceIdState(id);
    localStorage.setItem(STORAGE_KEY, id);
    window.location.reload();
  }, []);

  const refreshWorkspaces = useCallback(async () => {
    await refreshAuth();
  }, [refreshAuth]);

  const createWorkspace = useCallback(async (payload: WorkspaceCreatePayload): Promise<Workspace> => {
    const res = await fetch('/api/v1/workspaces', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await res.text());
    const created = await res.json();
    await refreshAuth();
    setTimeout(() => {
      setWorkspaceIdState(payload.id);
      localStorage.setItem(STORAGE_KEY, payload.id);
      window.location.reload();
    }, 100);
    return created;
  }, [refreshAuth]);

  const deleteWorkspace = useCallback(async (id: string) => {
    const res = await fetch(`/api/v1/workspaces/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text());
    await refreshAuth();
    if (workspaceId === id) {
      const fallback = authWorkspaces.find((w) => w.workspaceId !== id)?.workspaceId ?? '';
      if (fallback) {
        setWorkspaceIdState(fallback);
        localStorage.setItem(STORAGE_KEY, fallback);
        window.location.reload();
      }
    }
  }, [refreshAuth, workspaceId, authWorkspaces]);

  const value = useMemo(
    () => ({
      workspaceId,
      workspaces: workspaceList,
      loading,
      error,
      setWorkspaceId,
      refreshWorkspaces,
      createWorkspace,
      deleteWorkspace,
    }),
    [workspaceId, workspaceList, loading, error, setWorkspaceId, refreshWorkspaces, createWorkspace, deleteWorkspace],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) throw new Error('useWorkspace must be inside WorkspaceProvider');
  return ctx;
}

export function getStoredWorkspaceId(): string {
  return localStorage.getItem(STORAGE_KEY) ?? 'jinghua';
}
