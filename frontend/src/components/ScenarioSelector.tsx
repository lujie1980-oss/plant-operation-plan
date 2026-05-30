import { usePlan } from '../context/PlanContext';
import { CAPACITY_STRATEGY_LABELS } from '../types/masterPlanStrategies';
import type { MasterPlanCapacityStrategy } from '../types/api';
import './ScenarioSelector.css';

function capacityLabel(strategy: string): string {
  return CAPACITY_STRATEGY_LABELS[strategy as MasterPlanCapacityStrategy] ?? strategy;
}

export function ScenarioSelector() {
  const {
    scenarios,
    selectedScenarioId,
    activePlanVersionId,
    scenariosLoading,
    scenarioLoading,
    refreshScenarios,
    selectScenario,
  } = usePlan();

  const busy = scenariosLoading || scenarioLoading;
  const current = scenarios.find((s) => s.scenarioId === selectedScenarioId);

  return (
    <div className="scenario-selector scenario-selector--topbar">
      <span className="scenario-selector-title">计划场景</span>
      <select
        className="scenario-selector-input"
        value={selectedScenarioId ?? ''}
        onChange={(e) => void selectScenario(e.target.value || null)}
        disabled={busy || scenarios.length === 0}
        aria-label="选择计划场景"
      >
        {scenarios.length === 0 ? (
          <option value="">{scenariosLoading ? '加载中…' : '暂无场景'}</option>
        ) : (
          scenarios.map((s) => (
            <option key={s.scenarioId} value={s.scenarioId}>
              {s.name}
              {s.isDefault ? '（默认）' : ''}
              {s.currentPlanVersionId ? '' : ' · 未运行'}
            </option>
          ))
        )}
      </select>
      <button
        type="button"
        className="scenario-selector-refresh"
        onClick={() => void refreshScenarios()}
        disabled={busy}
        title="刷新场景列表"
        aria-label="刷新场景列表"
      >
        ↻
      </button>
      {current && (
        <span className="scenario-selector-strategy" title="策略与生效版本">
          {current.strategyName ?? capacityLabel(current.capacityStrategy)}
          {activePlanVersionId ? ` · ${activePlanVersionId}` : ' · 无生效版本'}
        </span>
      )}
      {busy && <span className="scenario-selector-busy">加载中…</span>}
    </div>
  );
}
