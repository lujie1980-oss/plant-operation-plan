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

async function fetchWorkspaces(): Promise<Workspace[]> {
  const res = await fetch('/api/v1/workspaces', { headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(await res.text());
  }
  return res.json() as Promise<Workspace[]>;
}

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceId, setWorkspaceIdState] = useState(
    () => localStorage.getItem(STORAGE_KEY) ?? 'jinghua',
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refreshWorkspaces = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchWorkspaces();
      setWorkspaces(list);
      const ids = new Set(list.map((w) => w.workspaceId));
      if (!ids.has(workspaceId)) {
        const fallback =
          list.find((w) => w.isDefault)?.workspaceId ?? list.find((w) => w.workspaceId === 'jinghua')?.workspaceId ?? 'jinghua';
        setWorkspaceIdState(fallback);
        localStorage.setItem(STORAGE_KEY, fallback);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    void refreshWorkspaces();
  }, [refreshWorkspaces]);

  const setWorkspaceId = useCallback((id: string) => {
    setWorkspaceIdState(id);
    localStorage.setItem(STORAGE_KEY, id);
    window.location.reload();
  }, []);

  const createWorkspace = useCallback(async (payload: WorkspaceCreatePayload) => {
    const res = await fetch('/api/v1/workspaces', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      throw new Error(await res.text());
    }
    const created = (await res.json()) as Workspace;
    await refreshWorkspaces();
    return created;
  }, [refreshWorkspaces]);

  const deleteWorkspace = useCallback(
    async (id: string) => {
      const res = await fetch(`/api/v1/workspaces/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        throw new Error(await res.text());
      }
      await refreshWorkspaces();
      if (workspaceId === id) {
        const fallback = workspaces.find((w) => w.isDefault)?.workspaceId ?? 'default';
        setWorkspaceId(fallback);
      }
    },
    [refreshWorkspaces, setWorkspaceId, workspaceId, workspaces],
  );

  const value = useMemo(
    () => ({
      workspaceId,
      workspaces,
      loading,
      error,
      setWorkspaceId,
      refreshWorkspaces,
      createWorkspace,
      deleteWorkspace,
    }),
    [
      workspaceId,
      workspaces,
      loading,
      error,
      setWorkspaceId,
      refreshWorkspaces,
      createWorkspace,
      deleteWorkspace,
    ],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) {
    throw new Error('useWorkspace must be used within WorkspaceProvider');
  }
  return ctx;
}

export function getStoredWorkspaceId(): string {
  // 与 WorkspaceProvider 的初始值保持一致，避免首次访问时 API 误用 default workspace
  return localStorage.getItem(STORAGE_KEY) ?? 'jinghua';
}
