import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import type { MasterPlanCapacityStrategy } from '../types/api';
import type { MasterPlanObjective } from '../types/masterPlanObjectives';
import {
  CAPACITY_STRATEGY_LABELS,
  type MasterPlanStrategyDetail,
  type MasterPlanStrategySummary,
} from '../types/masterPlanStrategies';
import './MasterPlanObjectivesPage.css';

type ObjectiveRow = MasterPlanObjective & { draftEnabled: boolean; draftWeight: number };

type StrategyDraft = {
  name: string;
  capacityStrategy: MasterPlanCapacityStrategy;
  setAsDefault: boolean;
  objectives: ObjectiveRow[];
};

function objectivesToDraft(objectives: MasterPlanObjective[]): ObjectiveRow[] {
  return objectives.map((o) => ({
    ...o,
    draftEnabled: o.enabled,
    draftWeight: o.weight,
  }));
}

function draftToUpdates(rows: ObjectiveRow[]) {
  return rows.map((r) => ({
    id: r.id,
    enabled: r.draftEnabled,
    weight: r.draftEnabled ? r.draftWeight : 0,
  }));
}

export function MasterPlanObjectivesPage() {
  const [strategies, setStrategies] = useState<MasterPlanStrategySummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selectedIdRef = useRef<string | null>(null);
  /** 递增以丢弃过期的策略详情请求（避免初始加载覆盖用户点击）。 */
  const selectionGenRef = useRef(0);
  const [draft, setDraft] = useState<StrategyDraft | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  selectedIdRef.current = selectedId;

  const applyDetail = useCallback((detail: MasterPlanStrategyDetail) => {
    setSelectedId(detail.id);
    setDraft({
      name: detail.name,
      capacityStrategy: detail.capacityStrategy,
      setAsDefault: detail.isDefault,
      objectives: objectivesToDraft(detail.objectives),
    });
  }, []);

  const loadStrategyDetail = useCallback(
    async (strategyId: string, expectedGen: number) => {
      const detail = await api.getMasterPlanStrategy(strategyId);
      if (expectedGen !== selectionGenRef.current) {
        return null;
      }
      applyDetail(detail);
      return detail;
    },
    [applyDetail],
  );

  const refreshStrategies = useCallback(
    async (preferId?: string | null, options?: { force?: boolean }) => {
      const list = await api.listMasterPlanStrategies();
      setStrategies(list);
      if (list.length === 0) {
        selectionGenRef.current += 1;
        setSelectedId(null);
        setDraft(null);
        return list;
      }
      const keepId = preferId ?? selectedIdRef.current;
      const targetId =
        keepId && list.some((s) => s.id === keepId)
          ? keepId
          : list.find((s) => s.isDefault)?.id ?? list[0].id;
      const force = options?.force === true;
      if (!force && selectedIdRef.current && selectedIdRef.current !== targetId) {
        return list;
      }
      const gen = ++selectionGenRef.current;
      await loadStrategyDetail(targetId, gen);
      return list;
    },
    [loadStrategyDetail],
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        await refreshStrategies(null, { force: true });
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshStrategies]);

  const selectStrategy = async (strategyId: string) => {
    const gen = ++selectionGenRef.current;
    setSelectedId(strategyId);
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await loadStrategyDetail(strategyId, gen);
    } catch (e) {
      if (gen === selectionGenRef.current) {
        setError(e instanceof Error ? e.message : '加载策略失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const updateObjective = (id: string, patch: Partial<Pick<ObjectiveRow, 'draftEnabled' | 'draftWeight'>>) => {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            objectives: prev.objectives.map((r) => (r.id === id ? { ...r, ...patch } : r)),
          }
        : prev,
    );
    setSuccess(null);
  };

  const save = async () => {
    if (!draft || !selectedId) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      for (const r of draft.objectives) {
        if (r.draftEnabled && r.draftWeight <= 0) {
          throw new Error(`「${r.name}」已启用时惩罚系数须大于 0`);
        }
      }
      if (!draft.name.trim()) {
        throw new Error('策略名称不能为空');
      }
      const payload: Parameters<typeof api.updateMasterPlanStrategy>[1] = {
        name: draft.name.trim(),
        capacityStrategy: draft.capacityStrategy,
        objectives: draftToUpdates(draft.objectives),
      };
      if (draft.setAsDefault) {
        payload.setAsDefault = true;
      }
      const saved = await api.updateMasterPlanStrategy(selectedId, payload);
      applyDetail(saved);
      await refreshStrategies(saved.id);
      setSuccess('策略已保存；下次主计划运行选用该策略时生效');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const createStrategy = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      let objectives = draft && draft.objectives.length > 0 ? draftToUpdates(draft.objectives) : null;
      if (!objectives) {
        const templateId = selectedIdRef.current ?? strategies[0]?.id;
        if (templateId) {
          const template = await api.getMasterPlanStrategy(templateId);
          objectives = template.objectives.map((o) => ({
            id: o.id,
            enabled: o.enabled,
            weight: o.weight,
          }));
        } else {
          objectives = [];
        }
      }
      const created = await api.createMasterPlanStrategy({
        name: `新策略 ${strategies.length + 1}`,
        capacityStrategy: draft?.capacityStrategy ?? 'UNCONSTRAINED',
        objectives,
      });
      applyDetail(created);
      await refreshStrategies(created.id);
      setSuccess('已创建新策略，请修改名称后保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建失败');
    } finally {
      setSaving(false);
    }
  };

  const duplicateStrategy = async () => {
    if (!selectedId) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const copy = await api.duplicateMasterPlanStrategy(selectedId);
      applyDetail(copy);
      await refreshStrategies(copy.id);
      setSuccess('已复制策略');
    } catch (e) {
      setError(e instanceof Error ? e.message : '复制失败');
    } finally {
      setSaving(false);
    }
  };

  const deleteStrategy = async () => {
    if (!selectedId || strategies.length <= 1) return;
    const current = strategies.find((s) => s.id === selectedId);
    if (!window.confirm(`确定删除策略「${current?.name ?? selectedId}」？`)) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await api.deleteMasterPlanStrategy(selectedId);
      await refreshStrategies(null, { force: true });
      setSuccess('策略已删除');
    } catch (e) {
      setError(e instanceof Error ? e.message : '删除失败');
    } finally {
      setSaving(false);
    }
  };

  const resetObjectives = () => {
    if (!draft) return;
    setDraft({
      ...draft,
      objectives: draft.objectives.map((r) => ({
        ...r,
        draftEnabled: r.defaultWeight > 0,
        draftWeight: r.defaultWeight,
      })),
    });
    setSuccess('已恢复默认惩罚系数（尚未保存，请点保存）');
  };

  const reload = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      await refreshStrategies(selectedIdRef.current);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="master-data-page plan-objectives-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="优化目标"
        description="定义主计划运行策略：每个策略包含产能模式（无限/有限）与软优化目标权重；计划运行时选择策略。"
        actions={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => void reload()} disabled={loading || saving}>
              刷新
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => void createStrategy()} disabled={loading || saving}>
              新建策略
            </button>
            <button type="button" className="btn primary" onClick={() => void save()} disabled={loading || saving || !draft}>
              {saving ? '保存中…' : '保存当前策略'}
            </button>
          </>
        }
      />
      <StatusBanner loading={loading || saving} error={error} />
      {success && <p className="plan-objectives-success">{success}</p>}

      <div className="plan-objectives-layout">
        <aside className="card plan-strategy-list">
          <h3 className="plan-strategy-list-title">策略列表</h3>
          <ul className="plan-strategy-items">
            {strategies.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  className={`plan-strategy-item${selectedId === s.id ? ' is-active' : ''}`}
                  onClick={() => void selectStrategy(s.id)}
                  disabled={loading || saving}
                >
                  <span className="plan-strategy-item-name">{s.name}</span>
                  <span className="plan-strategy-item-meta">
                    {CAPACITY_STRATEGY_LABELS[s.capacityStrategy]}
                    {s.isDefault ? ' · 默认' : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <div className="plan-objectives-scroll">
          {!draft ? (
            <p className="plan-objectives-hint">请选择或新建策略</p>
          ) : (
            <>
              <div className="card plan-strategy-form">
                <div className="plan-strategy-form-row">
                  <label>
                    策略名称
                    <input
                      className="input"
                      value={draft.name}
                      onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                      disabled={saving}
                    />
                  </label>
                  <label>
                    产能模式
                    <select
                      className="input"
                      value={draft.capacityStrategy}
                      onChange={(e) =>
                        setDraft({ ...draft, capacityStrategy: e.target.value as MasterPlanCapacityStrategy })
                      }
                      disabled={saving}
                    >
                      {(Object.keys(CAPACITY_STRATEGY_LABELS) as MasterPlanCapacityStrategy[]).map((key) => (
                        <option key={key} value={key}>
                          {CAPACITY_STRATEGY_LABELS[key]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="plan-strategy-default-check" title="默认策略仅能有一个；请选中目标策略后勾选并保存，或切换到其他策略再设为默认">
                    <input
                      type="checkbox"
                      checked={draft.setAsDefault}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setDraft({ ...draft, setAsDefault: true });
                        }
                      }}
                      disabled={saving || draft.setAsDefault}
                    />
                    设为默认策略
                  </label>
                </div>
                <div className="plan-strategy-form-actions">
                  <button type="button" className="btn btn-secondary" onClick={() => void duplicateStrategy()} disabled={saving}>
                    复制
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => void deleteStrategy()}
                    disabled={saving || strategies.length <= 1}
                  >
                    删除
                  </button>
                  <button type="button" className="btn btn-secondary" onClick={resetObjectives} disabled={saving}>
                    恢复默认系数
                  </button>
                </div>
              </div>

              <p className="plan-objectives-hint">
                修改策略名称或目标权重后，请点击右上角「保存当前策略」。惩罚系数越大，求解器越优先满足该目标。
              </p>
              <div className="card plan-objectives-table-wrap">
                {draft && (
                  <FilterableTable
                    tableId="master-plan-objectives"
                    tableClassName="plan-objectives-table"
                    wrapClassName="ft-table-wrap"
                    rows={draft.objectives}
                    rowKey={(row) => row.id}
                    columns={[
                      {
                        key: 'enabled',
                        header: '启用',
                        filterable: false,
                        className: 'col-enable',
                        render: (row) => (
                          <input
                            type="checkbox"
                            checked={row.draftEnabled}
                            onChange={(e) => updateObjective(row.id, { draftEnabled: e.target.checked })}
                            aria-label={`启用 ${row.name}`}
                          />
                        ),
                      },
                      { key: 'name', header: '优化目标', className: 'col-name', render: (row) => row.name },
                      {
                        key: 'description',
                        header: '说明',
                        className: 'col-desc',
                        render: (row) => row.description,
                      },
                      {
                        key: 'penaltyUnit',
                        header: '惩罚计量',
                        className: 'col-unit',
                        render: (row) => row.penaltyUnit,
                      },
                      {
                        key: 'weight',
                        header: '惩罚系数',
                        filterable: false,
                        className: 'col-weight',
                        render: (row) => (
                          <input
                            type="number"
                            min={0}
                            step={1}
                            value={row.draftWeight}
                            disabled={!row.draftEnabled}
                            onChange={(e) =>
                              updateObjective(row.id, {
                                draftWeight: Math.max(0, Number.parseInt(e.target.value, 10) || 0),
                              })
                            }
                          />
                        ),
                      },
                      {
                        key: 'defaultWeight',
                        header: '默认值',
                        className: 'col-default',
                        render: (row) => row.defaultWeight,
                      },
                    ]}
                  />
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
