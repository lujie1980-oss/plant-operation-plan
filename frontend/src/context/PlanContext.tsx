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
import type { DetailScheduleResult, MasterPlanResult, PipelineResult, PlanningScenario } from '../types/api';
import { useWorkspace } from './WorkspaceContext';

interface PlanState {
  masterPlan: MasterPlanResult | null;
  detailSchedule: DetailScheduleResult | null;
  pipeline: PipelineResult | null;
  scenarios: PlanningScenario[];
  /** 选中的计划场景 ID（SCN-*） */
  selectedScenarioId: string | null;
  /** 当前场景生效的主计划版本（MP-*），供分析 API 使用 */
  activePlanVersionId: string | null;
  scenariosLoading: boolean;
  scenarioLoading: boolean;
  setMasterPlan: (r: MasterPlanResult | null) => void;
  setDetailSchedule: (r: DetailScheduleResult | null) => void;
  setPipeline: (r: PipelineResult | null) => void;
  refreshScenarios: () => Promise<PlanningScenario[]>;
  selectScenario: (scenarioId: string | null) => Promise<void>;
}

const PlanContext = createContext<PlanState | null>(null);

function storageKey(workspaceId: string) {
  return `plantops.plan.${workspaceId}`;
}

function loadStored(workspaceId: string): { scenarioId?: string } {
  try {
    return JSON.parse(localStorage.getItem(storageKey(workspaceId)) ?? '{}');
  } catch {
    return {};
  }
}

function saveStored(workspaceId: string, scenarioId?: string) {
  localStorage.setItem(storageKey(workspaceId), JSON.stringify({ scenarioId }));
}

function activeVersionFor(scenario: PlanningScenario | undefined): string | null {
  if (!scenario) return null;
  return scenario.currentPlanVersionId ?? scenario.planVersionId ?? null;
}

export function PlanProvider({ children }: { children: ReactNode }) {
  const { workspaceId } = useWorkspace();
  const [masterPlan, setMasterPlanState] = useState<MasterPlanResult | null>(null);
  const [detailSchedule, setDetailScheduleState] = useState<DetailScheduleResult | null>(null);
  const [pipeline, setPipeline] = useState<PipelineResult | null>(null);
  const [scenarios, setScenarios] = useState<PlanningScenario[]>([]);
  const [selectedScenarioId, setSelectedScenarioId] = useState<string | null>(null);
  const [activePlanVersionId, setActivePlanVersionId] = useState<string | null>(null);
  const [scenariosLoading, setScenariosLoading] = useState(false);
  const [scenarioLoading, setScenarioLoading] = useState(false);
  const restoredRef = useRef<string | null>(null);

  const setMasterPlan = useCallback(
    (r: MasterPlanResult | null) => {
      setMasterPlanState(r);
      if (r?.planVersionId) {
        setActivePlanVersionId(r.planVersionId);
      }
    },
    [],
  );

  const setDetailSchedule = useCallback((r: DetailScheduleResult | null) => {
    setDetailScheduleState(r);
  }, []);

  const refreshScenarios = useCallback(async () => {
    setScenariosLoading(true);
    try {
      const list = await api.listScenarioCatalog();
      setScenarios(list);
      return list;
    } finally {
      setScenariosLoading(false);
    }
  }, []);

  const loadMasterPlanForScenario = useCallback(
    async (scenario: PlanningScenario | undefined) => {
      const versionId = activeVersionFor(scenario);
      setActivePlanVersionId(versionId);
      if (!versionId) {
        setMasterPlanState(null);
        return;
      }
      setScenarioLoading(true);
      try {
        const result = await api.getMasterPlan(versionId);
        setMasterPlanState(result);
      } finally {
        setScenarioLoading(false);
      }
    },
    [],
  );

  const selectScenario = useCallback(
    async (scenarioId: string | null) => {
      if (!scenarioId) {
        setSelectedScenarioId(null);
        setActivePlanVersionId(null);
        setMasterPlanState(null);
        saveStored(workspaceId);
        return;
      }
      setSelectedScenarioId(scenarioId);
      saveStored(workspaceId, scenarioId);
      const scenario = scenarios.find((s) => s.scenarioId === scenarioId)
        ?? (await refreshScenarios()).find((s) => s.scenarioId === scenarioId);
      await loadMasterPlanForScenario(scenario);
    },
    [scenarios, workspaceId, loadMasterPlanForScenario, refreshScenarios],
  );

  useEffect(() => {
    if (restoredRef.current === workspaceId) return;
    restoredRef.current = workspaceId;
    void (async () => {
      const list = await refreshScenarios();
      const storedId = loadStored(workspaceId).scenarioId;
      const pick =
        (storedId && list.find((s) => s.scenarioId === storedId))
        ?? list.find((s) => s.isDefault)
        ?? list[0];
      if (pick) {
        setSelectedScenarioId(pick.scenarioId);
        saveStored(workspaceId, pick.scenarioId);
        await loadMasterPlanForScenario(pick);
      } else {
        setSelectedScenarioId(null);
        setActivePlanVersionId(null);
        setMasterPlanState(null);
      }
    })();
  }, [workspaceId, refreshScenarios, loadMasterPlanForScenario]);

  const value = useMemo(
    () => ({
      masterPlan,
      detailSchedule,
      pipeline,
      scenarios,
      selectedScenarioId,
      activePlanVersionId,
      scenariosLoading,
      scenarioLoading,
      setMasterPlan,
      setDetailSchedule,
      setPipeline,
      refreshScenarios,
      selectScenario,
    }),
    [
      masterPlan,
      detailSchedule,
      pipeline,
      scenarios,
      selectedScenarioId,
      activePlanVersionId,
      scenariosLoading,
      scenarioLoading,
      setMasterPlan,
      setDetailSchedule,
      refreshScenarios,
      selectScenario,
    ],
  );

  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const ctx = useContext(PlanContext);
  if (!ctx) throw new Error('usePlan must be used within PlanProvider');
  return ctx;
}
