import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { PispInventoryChart } from '../components/PispInventoryChart';
import { PispPeriodInventoryTable } from '../components/PispPeriodInventoryTable';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import type {
  MasterPlanSessionConfirmResultDto,
  MasterPlanSessionDto,
  MasterPlanSessionOptimizeResultDto,
  PispPeriodSnapshotDto,
  PispSummaryDto,
  SimulateMasterPlanSessionRequest,
} from '../types/ontology';
import './MasterPlanOntologyPage.css';

function mergeSnapshots(
  existing: PispPeriodSnapshotDto[],
  updates: PispPeriodSnapshotDto[],
): PispPeriodSnapshotDto[] {
  if (updates.length === 0) return existing;
  const updateById = new Map(updates.map((snapshot) => [snapshot.id, snapshot]));
  const merged = existing.map((snapshot) => updateById.get(snapshot.id) ?? snapshot);
  for (const update of updates) {
    if (!existing.some((snapshot) => snapshot.id === update.id)) {
      merged.push(update);
    }
  }
  return merged;
}

export function MasterPlanOntologyPage() {
  const { activePlanVersionId } = usePlan();
  const [planVersionId, setPlanVersionId] = useState(activePlanVersionId ?? '');
  const [session, setSession] = useState<MasterPlanSessionDto | null>(null);
  const [pisps, setPisps] = useState<PispSummaryDto[]>([]);
  const [selectedPispId, setSelectedPispId] = useState<string>('');
  const [periods, setPeriods] = useState<PispPeriodSnapshotDto[]>([]);
  const [simulatePeriodId, setSimulatePeriodId] = useState('');
  const [simulateProperty, setSimulateProperty] =
    useState<SimulateMasterPlanSessionRequest['property']>('plannedSupplyTotal');
  const [simulateValue, setSimulateValue] = useState('0');
  const [optimizeResult, setOptimizeResult] = useState<MasterPlanSessionOptimizeResultDto | null>(null);
  const [confirmResult, setConfirmResult] = useState<MasterPlanSessionConfirmResultDto | null>(null);

  const [creating, setCreating] = useState(false);
  const [loadingPisps, setLoadingPisps] = useState(false);
  const [loadingPeriods, setLoadingPeriods] = useState(false);
  const [simulating, setSimulating] = useState(false);
  const [optimizing, setOptimizing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!planVersionId && activePlanVersionId) {
      setPlanVersionId(activePlanVersionId);
    }
  }, [activePlanVersionId, planVersionId]);

  const loading = creating || loadingPisps || loadingPeriods || simulating || optimizing || confirming;

  const selectedPisp = useMemo(
    () => pisps.find((pisp) => pisp.pispId === selectedPispId) ?? null,
    [pisps, selectedPispId],
  );

  async function loadPeriods(sessionId: string, pispId: string) {
    setLoadingPeriods(true);
    try {
      const rows = await api.masterPlanSessions.listPeriods(sessionId, pispId);
      setPeriods(rows);
      setSimulatePeriodId((prev) => {
        if (rows.length === 0) return '';
        if (prev && rows.some((row) => row.id === prev)) return prev;
        return rows[0].id;
      });
    } finally {
      setLoadingPeriods(false);
    }
  }

  async function loadPisps(sessionId: string) {
    setLoadingPisps(true);
    try {
      const items = await api.masterPlanSessions.listPisps(sessionId);
      setPisps(items);
      const firstPispId = items[0]?.pispId ?? '';
      setSelectedPispId(firstPispId);
      if (firstPispId) {
        await loadPeriods(sessionId, firstPispId);
      } else {
        setPeriods([]);
        setSimulatePeriodId('');
      }
    } finally {
      setLoadingPisps(false);
    }
  }

  async function handleCreateSession() {
    if (!planVersionId.trim()) {
      setError('请输入 planVersionId');
      return;
    }
    setCreating(true);
    setError(null);
    setOptimizeResult(null);
    setConfirmResult(null);
    try {
      const created = await api.masterPlanSessions.create(planVersionId.trim());
      setSession(created);
      await loadPisps(created.sessionId);
    } catch (e) {
      setSession(null);
      setPisps([]);
      setSelectedPispId('');
      setPeriods([]);
      setSimulatePeriodId('');
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCreating(false);
    }
  }

  async function handleSelectPisp(pispId: string) {
    setSelectedPispId(pispId);
    if (!session?.sessionId) return;
    setError(null);
    try {
      await loadPeriods(session.sessionId, pispId);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleSimulate() {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return;
    }
    if (!simulatePeriodId) {
      setError('请选择周期');
      return;
    }
    const value = Number(simulateValue);
    if (!Number.isFinite(value)) {
      setError('请输入有效数值');
      return;
    }
    setSimulating(true);
    setError(null);
    try {
      const result = await api.masterPlanSessions.simulate(session.sessionId, {
        pispPeriodId: simulatePeriodId,
        property: simulateProperty,
        value,
      });
      setPeriods((prev) => mergeSnapshots(prev, result.snapshots));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSimulating(false);
    }
  }

  async function handleOptimize() {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return;
    }
    setOptimizing(true);
    setError(null);
    try {
      const result = await api.masterPlanSessions.optimize(session.sessionId);
      setOptimizeResult(result);
      if (selectedPispId) {
        await loadPeriods(session.sessionId, selectedPispId);
      } else {
        setPeriods((prev) => mergeSnapshots(prev, result.affectedSnapshots));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setOptimizing(false);
    }
  }

  async function handleConfirm() {
    if (!session?.sessionId) {
      setError('请先创建 Session');
      return;
    }
    setConfirming(true);
    setError(null);
    try {
      const result = await api.masterPlanSessions.confirm(session.sessionId);
      setConfirmResult(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setConfirming(false);
    }
  }

  return (
    <div className="master-plan-ontology-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="本体推演工作台"
        description="M2 本体 Session：选择 PISP 查看库存曲线，支持局部推演与优化确认"
        showScenarioSelector
      />
      <StatusBanner loading={loading} error={error} />

      <section className="card ontology-session-card">
        <div className="ontology-session-controls">
          <label>
            Plan Version
            <input
              className="input"
              value={planVersionId}
              onChange={(e) => setPlanVersionId(e.target.value)}
              placeholder="输入 planVersionId"
            />
          </label>
          <button type="button" className="btn primary" disabled={creating} onClick={() => void handleCreateSession()}>
            {creating ? '创建中…' : '创建 Session'}
          </button>
          <button type="button" className="btn" disabled={!session || optimizing} onClick={() => void handleOptimize()}>
            {optimizing ? '优化中…' : '优化'}
          </button>
          <button type="button" className="btn" disabled={!session || confirming} onClick={() => void handleConfirm()}>
            {confirming ? '确认中…' : '确认'}
          </button>
        </div>

        {session && (
          <div className="meta-row">
            <span>Session: {session.sessionId}</span>
            <span>Base Plan: {session.basePlanVersionId}</span>
            <span>PISP: {session.pispCount}</span>
            <span>Periods: {session.periodCount}</span>
            <span>Expires: {new Date(session.expiresAt).toLocaleString()}</span>
          </div>
        )}
        {optimizeResult && (
          <div className="meta-row">
            <span>优化 Score: {optimizeResult.score ?? 'N/A'}</span>
            <span>分配数: {optimizeResult.allocationCount}</span>
            <span>耗时: {optimizeResult.solveDurationMs} ms</span>
          </div>
        )}
        {confirmResult && (
          <div className="meta-row">
            <span>已确认 Plan Version: {confirmResult.planVersionId}</span>
            <span>Allocation: {confirmResult.allocationCount}</span>
          </div>
        )}
      </section>

      <div className="ontology-layout">
        <aside className="card ontology-pisp-list">
          <h3 className="panel-title">PISP 列表</h3>
          {pisps.length === 0 ? (
            <p className="empty">先创建 Session 后加载 PISP</p>
          ) : (
            <div className="ontology-list-scroll">
              {pisps.map((pisp) => (
                <button
                  key={pisp.pispId}
                  type="button"
                  className={`ontology-pisp-item ${selectedPispId === pisp.pispId ? 'active' : ''}`}
                  onClick={() => void handleSelectPisp(pisp.pispId)}
                >
                  <span className="ontology-pisp-code">{pisp.productCode ?? 'UNKNOWN'}</span>
                  <span className="ontology-pisp-id">{pisp.pispId}</span>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="card ontology-chart-panel">
          <div className="ontology-chart-head">
            <h3 className="panel-title">库存推演曲线</h3>
            <span className="ontology-chart-target">
              {selectedPisp ? `${selectedPisp.productCode ?? 'UNKNOWN'} · ${selectedPisp.pispId}` : '未选择 PISP'}
            </span>
          </div>

          <div className="ontology-simulate-controls">
            <label>
              周期
              <select
                className="input"
                value={simulatePeriodId}
                onChange={(e) => setSimulatePeriodId(e.target.value)}
                disabled={!session || periods.length === 0}
              >
                <option value="">请选择</option>
                {periods.map((row) => (
                  <option key={row.id} value={row.id}>
                    {row.periodId}
                  </option>
                ))}
              </select>
            </label>
            <label>
              属性
              <select
                className="input"
                value={simulateProperty}
                onChange={(e) =>
                  setSimulateProperty(e.target.value as SimulateMasterPlanSessionRequest['property'])
                }
                disabled={!session}
              >
                <option value="plannedSupplyTotal">plannedSupplyTotal</option>
                <option value="plannedDemandQuantityTotal">plannedDemandQuantityTotal</option>
              </select>
            </label>
            <label>
              值
              <input
                className="input"
                type="number"
                value={simulateValue}
                onChange={(e) => setSimulateValue(e.target.value)}
                disabled={!session}
              />
            </label>
            <button type="button" className="btn" disabled={!session || simulating} onClick={() => void handleSimulate()}>
              {simulating ? '推演中…' : '模拟'}
            </button>
          </div>

          <div className="ontology-detail-body">
            <div className="ontology-chart-body">
              <PispInventoryChart snapshots={periods} />
            </div>
            <PispPeriodInventoryTable
              productCode={selectedPisp?.productCode ?? null}
              snapshots={periods}
            />
          </div>
        </section>
      </div>
    </div>
  );
}
