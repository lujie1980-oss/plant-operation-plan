import { useCallback, useState } from 'react';
import { api } from '../api/client';
import type { DetailSchedulePlanningPreview } from '../types/detailSchedulePlanningPreview';
import type { ScheduleSession, SessionStepPatch } from '../types/scheduleSession';

export function useScheduleSession(masterPlanVersionId: string | null) {
  const [session, setSession] = useState<ScheduleSession | null>(null);
  const [preview, setPreview] = useState<DetailSchedulePlanningPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [simulating, setSimulating] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createSession = useCallback(
    async (opts?: { seedInitialQueues?: boolean; solve?: boolean }) => {
      if (!masterPlanVersionId) {
        setError('请先选择主计划版本');
        return null;
      }
      setLoading(true);
      setError(null);
      try {
        const result = await api.createScheduleSession({
          masterPlanVersionId,
          seedInitialQueues: opts?.seedInitialQueues ?? true,
          solve: opts?.solve ?? false,
        });
        setSession(result);
        setPreview(result.preview);
        return result;
      } catch (e: unknown) {
        setSession(null);
        setPreview(null);
        setError(e instanceof Error ? e.message : String(e));
        return null;
      } finally {
        setLoading(false);
      }
    },
    [masterPlanVersionId],
  );

  /** 全量链式赋时（工具栏「全量推演」等，不依赖 patch 种子）。 */
  const simulateFull = useCallback(async () => {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return null;
    }
    setSimulating(true);
    setError(null);
    try {
      const result = await api.simulateScheduleSession(session.sessionId, {
        fullReschedule: true,
      });
      setSession(result.session);
      setPreview(result.session.preview);
      return result;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      return null;
    } finally {
      setSimulating(false);
    }
  }, [session?.sessionId]);

  /** 应用 patch 后按受影响工序做增量推演（批次/工序排产、甘特拖拽等）。 */
  const simulateIncremental = useCallback(
    async (patches: SessionStepPatch[]) => {
      if (!session?.sessionId) {
        setError('请先创建 Session');
        return null;
      }
      if (patches.length === 0) {
        setError('没有可推演的工序变更');
        return null;
      }
      setSimulating(true);
      setError(null);
      try {
        const result = await api.simulateScheduleSession(session.sessionId, {
          stepPatches: patches,
          fullReschedule: false,
        });
        setSession(result.session);
        setPreview(result.session.preview);
        return result;
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : String(e));
        return null;
      } finally {
        setSimulating(false);
      }
    },
    [session?.sessionId],
  );

  const simulate = useCallback(
    async (patches?: SessionStepPatch[], fullReschedule?: boolean) => {
      if (fullReschedule && (!patches || patches.length === 0)) {
        return simulateFull();
      }
      if (patches && patches.length > 0 && !fullReschedule) {
        return simulateIncremental(patches);
      }
      if (!session?.sessionId) {
        setError('请先创建 Session');
        return null;
      }
      setSimulating(true);
      setError(null);
      try {
        const result = await api.simulateScheduleSession(session.sessionId, {
          stepPatches: patches,
          fullReschedule,
        });
        setSession(result.session);
        setPreview(result.session.preview);
        return result;
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : String(e));
        return null;
      } finally {
        setSimulating(false);
      }
    },
    [session?.sessionId, simulateFull, simulateIncremental],
  );

  const confirm = useCallback(async () => {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return null;
    }
    setConfirming(true);
    setError(null);
    try {
      const result = await api.confirmScheduleSession(session.sessionId);
      setSession(null);
      setPreview(null);
      return result;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      return null;
    } finally {
      setConfirming(false);
    }
  }, [session?.sessionId]);

  const optimize = useCallback(async () => {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await api.optimizeScheduleSession(session.sessionId);
      setSession(result);
      setPreview(result.preview);
      return result;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      return null;
    } finally {
      setLoading(false);
    }
  }, [session?.sessionId]);

  return {
    session,
    preview,
    loading,
    simulating,
    confirming,
    error,
    setError,
    createSession,
    simulate,
    simulateFull,
    simulateIncremental,
    confirm,
    optimize,
    hasSession: Boolean(session?.sessionId),
  };
}
