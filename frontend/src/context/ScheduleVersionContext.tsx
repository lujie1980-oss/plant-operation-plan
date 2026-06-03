import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api } from '../api/client';
import type { DetailScheduleResult, DetailScheduleVersionSummary } from '../types/api';
import { useWorkspace } from './WorkspaceContext';

interface ScheduleVersionState {
  versions: DetailScheduleVersionSummary[];
  versionsLoading: boolean;
  /** 最新排程版本（默认查看） */
  currentVersionId: string | null;
  /** 勾选「查看历史版本」 */
  viewHistory: boolean;
  /** 历史模式下选中的版本 */
  historyVersionId: string | null;
  /** 当前页面应使用的排程版本 */
  activeVersionId: string | null;
  activeVersion: DetailScheduleVersionSummary | null;
  refreshVersions: () => Promise<DetailScheduleVersionSummary[]>;
  setViewHistory: (value: boolean) => void;
  selectHistoryVersion: (versionId: string | null) => void;
  /** 求解完成后更新当前版本 */
  registerNewVersion: (result: DetailScheduleResult) => void;
}

const ScheduleVersionContext = createContext<ScheduleVersionState | null>(null);

function storageKey(workspaceId: string) {
  return `plantops.scheduleVersion.${workspaceId}`;
}

function loadStored(workspaceId: string): { viewHistory?: boolean; historyVersionId?: string } {
  try {
    return JSON.parse(localStorage.getItem(storageKey(workspaceId)) ?? '{}');
  } catch {
    return {};
  }
}

function saveStored(workspaceId: string, viewHistory: boolean, historyVersionId: string | null) {
  localStorage.setItem(
    storageKey(workspaceId),
    JSON.stringify({ viewHistory, historyVersionId: historyVersionId ?? undefined }),
  );
}

export function ScheduleVersionProvider({ children }: { children: ReactNode }) {
  const { workspaceId } = useWorkspace();
  const [versions, setVersions] = useState<DetailScheduleVersionSummary[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [currentVersionId, setCurrentVersionId] = useState<string | null>(null);
  const [viewHistory, setViewHistoryState] = useState(false);
  const [historyVersionId, setHistoryVersionId] = useState<string | null>(null);
  const restoredRef = useRef<string | null>(null);

  const refreshVersions = useCallback(async () => {
    setVersionsLoading(true);
    try {
      const list = await api.listDetailScheduleVersions(50);
      setVersions(list);
      const latest = list[0]?.planVersionId ?? null;
      setCurrentVersionId((prev) => latest ?? prev);
      return list;
    } finally {
      setVersionsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (restoredRef.current === workspaceId) return;
    restoredRef.current = workspaceId;
    const stored = loadStored(workspaceId);
    setViewHistoryState(Boolean(stored.viewHistory));
    setHistoryVersionId(stored.historyVersionId ?? null);
    void refreshVersions();
  }, [workspaceId, refreshVersions]);

  const setViewHistory = useCallback(
    (value: boolean) => {
      setViewHistoryState(value);
      if (value && !historyVersionId && currentVersionId) {
        setHistoryVersionId(currentVersionId);
        saveStored(workspaceId, true, currentVersionId);
      } else {
        saveStored(workspaceId, value, historyVersionId);
      }
    },
    [workspaceId, historyVersionId, currentVersionId],
  );

  const selectHistoryVersion = useCallback(
    (versionId: string | null) => {
      setHistoryVersionId(versionId);
      saveStored(workspaceId, viewHistory, versionId);
    },
    [workspaceId, viewHistory],
  );

  const registerNewVersion = useCallback(
    (result: DetailScheduleResult) => {
      setCurrentVersionId(result.planVersionId);
      if (!viewHistory) {
        setHistoryVersionId(null);
      }
      void refreshVersions();
    },
    [viewHistory, refreshVersions],
  );

  const activeVersionId = viewHistory
    ? historyVersionId ?? currentVersionId
    : currentVersionId;

  const activeVersion = useMemo(
    () => versions.find((v) => v.planVersionId === activeVersionId) ?? null,
    [versions, activeVersionId],
  );

  const value = useMemo(
    () => ({
      versions,
      versionsLoading,
      currentVersionId,
      viewHistory,
      historyVersionId,
      activeVersionId,
      activeVersion,
      refreshVersions,
      setViewHistory,
      selectHistoryVersion,
      registerNewVersion,
    }),
    [
      versions,
      versionsLoading,
      currentVersionId,
      viewHistory,
      historyVersionId,
      activeVersionId,
      activeVersion,
      refreshVersions,
      setViewHistory,
      selectHistoryVersion,
      registerNewVersion,
    ],
  );

  return (
    <ScheduleVersionContext.Provider value={value}>{children}</ScheduleVersionContext.Provider>
  );
}

export function useScheduleVersion() {
  const ctx = useContext(ScheduleVersionContext);
  if (!ctx) {
    throw new Error('useScheduleVersion must be used within ScheduleVersionProvider');
  }
  return ctx;
}

/** 排程模块外使用时返回 null，避免强依赖 Provider */
export function useScheduleVersionOptional() {
  return useContext(ScheduleVersionContext);
}
