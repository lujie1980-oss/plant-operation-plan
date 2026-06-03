import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { StatusBanner } from './StatusBanner';
import {
  DEFAULT_SCHEDULE_CONTRACT,
  SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS,
  type ScheduleContractConfig,
} from '../types/scheduleContract';
import {
  parseScheduleContractJson,
  SCHEDULE_CONTRACT_PARAM_ID,
  serializeScheduleContractJson,
} from '../utils/scheduleContractConfig';
import type { SystemParameterMd } from '../types/masterData';
import './ScheduleContractConfigPanel.css';

type ScheduleContractConfigPanelProps = {
  /** 可选：通知父级保存状态（如统一禁用刷新按钮） */
  onSavingChange?: (saving: boolean) => void;
};

export function ScheduleContractConfigPanel({ onSavingChange }: ScheduleContractConfigPanelProps) {
  const [paramRow, setParamRow] = useState<SystemParameterMd | null>(null);
  const [draft, setDraft] = useState<ScheduleContractConfig>(DEFAULT_SCHEDULE_CONTRACT);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const rows = await api.masterData.parameters.list();
      const row =
        rows.find((r) => r.paramId === SCHEDULE_CONTRACT_PARAM_ID) ?? null;
      setParamRow(row);
      setDraft(parseScheduleContractJson(row?.paramValue));
      setDirty(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载主计划衔接参数失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const updateField = <K extends keyof ScheduleContractConfig>(
    key: K,
    value: ScheduleContractConfig[K],
  ) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
    setDirty(true);
    setSuccess(null);
  };

  const handleSave = async () => {
    setSaving(true);
    onSavingChange?.(true);
    setError(null);
    setSuccess(null);
    try {
      const payload: SystemParameterMd = {
        id: paramRow?.id ?? null,
        paramId: SCHEDULE_CONTRACT_PARAM_ID,
        paramValue: serializeScheduleContractJson(draft),
        description:
          paramRow?.description ??
          'Default tuned contract; stronger quadratic early penalty',
      };
      const saved = await api.masterData.parameters.save(payload);
      setParamRow(saved);
      setDraft(parseScheduleContractJson(saved.paramValue));
      setDirty(false);
      setSuccess('主计划衔接参数已保存，下次排程求解时生效');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
      onSavingChange?.(false);
    }
  };

  const handleResetDefaults = () => {
    setDraft({ ...DEFAULT_SCHEDULE_CONTRACT });
    setDirty(true);
    setSuccess(null);
  };

  const lateModeHint = SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS.find(
    (o) => o.value === draft.mpLateMode,
  )?.hint;
  const earlyModeHint = SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS.find(
    (o) => o.value === draft.mpEarlyMode,
  )?.hint;

  return (
    <div className="schedule-contract-panel">
      <StatusBanner loading={loading || saving} error={error} success={success} />
      <div className="schedule-contract-sections">
        <section className="schedule-contract-section card">
          <h3 className="schedule-contract-section-title">L1 · 交期软约束</h3>
          <p className="schedule-contract-section-desc">
            工序完成日晚于工单交期时，按天累加惩罚（线性）。
          </p>
          <div className="mf-catalog-form-grid">
            <label>
              交期延误权重
              <input
                className="input"
                type="number"
                min={0}
                step={1}
                value={draft.weightDue}
                disabled={loading || saving}
                onChange={(e) =>
                  updateField('weightDue', Math.max(0, Number.parseInt(e.target.value, 10) || 0))
                }
              />
            </label>
          </div>
        </section>

        <section className="schedule-contract-section card">
          <h3 className="schedule-contract-section-title">主计划契约 · 最早开工</h3>
          <p className="schedule-contract-section-desc">
            开启后，工序不得早于主计划分配的开始日（mpContractStartDate）开工；关闭后可在产线空闲时提前排产，契约日仅作参考。
          </p>
          <label className="schedule-contract-toggle">
            <input
              type="checkbox"
              checked={draft.enableMpContractStartWait}
              disabled={loading || saving}
              onChange={(e) => updateField('enableMpContractStartWait', e.target.checked)}
            />
            启用契约开始日最早开工等待
          </label>
        </section>

        <section className="schedule-contract-section card">
          <h3 className="schedule-contract-section-title">L2 · 主计划目标软约束</h3>
          <p className="schedule-contract-section-desc">
            相对主计划目标完成日的偏差惩罚；偏早与偏晚可使用不同权重与公式。关闭后求解器不再计入该软约束。
          </p>
          <label className="schedule-contract-toggle">
            <input
              type="checkbox"
              checked={draft.enableMpTarget}
              disabled={loading || saving}
              onChange={(e) => updateField('enableMpTarget', e.target.checked)}
            />
            启用主计划目标软约束
          </label>
          <div className="mf-catalog-form-grid">
            <label>
              偏晚权重
              <input
                className="input"
                type="number"
                min={0}
                step={1}
                value={draft.weightMpLate}
                disabled={loading || saving || !draft.enableMpTarget}
                onChange={(e) =>
                  updateField(
                    'weightMpLate',
                    Math.max(0, Number.parseInt(e.target.value, 10) || 0),
                  )
                }
              />
            </label>
            <label>
              偏晚惩罚公式
              <select
                className="input"
                value={draft.mpLateMode}
                disabled={loading || saving || !draft.enableMpTarget}
                onChange={(e) =>
                  updateField('mpLateMode', e.target.value as ScheduleContractConfig['mpLateMode'])
                }
              >
                {SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS.filter((o) => o.value !== 'CAPPED').map(
                  (opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ),
                )}
              </select>
              {lateModeHint && <span className="schedule-contract-field-hint">{lateModeHint}</span>}
            </label>
            <label>
              偏早权重
              <input
                className="input"
                type="number"
                min={0}
                step={1}
                value={draft.weightMpEarly}
                disabled={loading || saving || !draft.enableMpTarget}
                onChange={(e) =>
                  updateField(
                    'weightMpEarly',
                    Math.max(0, Number.parseInt(e.target.value, 10) || 0),
                  )
                }
              />
            </label>
            <label>
              偏早惩罚公式
              <select
                className="input"
                value={draft.mpEarlyMode}
                disabled={loading || saving || !draft.enableMpTarget}
                onChange={(e) =>
                  updateField('mpEarlyMode', e.target.value as ScheduleContractConfig['mpEarlyMode'])
                }
              >
                {SCHEDULE_CONTRACT_PENALTY_MODE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
              {earlyModeHint && (
                <span className="schedule-contract-field-hint">{earlyModeHint}</span>
              )}
            </label>
            {draft.mpEarlyMode === 'CAPPED' && (
              <label>
                偏早惩罚上限（天）
                <input
                  className="input"
                  type="number"
                  min={0}
                  step={1}
                  value={draft.mpEarlyCapDays}
                  disabled={loading || saving || !draft.enableMpTarget}
                  onChange={(e) =>
                    updateField(
                      'mpEarlyCapDays',
                      Math.max(0, Number.parseInt(e.target.value, 10) || 0),
                    )
                  }
                />
              </label>
            )}
          </div>
        </section>
      </div>

      <div className="schedule-contract-actions">
        <button
          type="button"
          className="btn btn-primary"
          disabled={loading || saving || !dirty}
          onClick={() => void handleSave()}
        >
          {saving ? '保存中…' : '保存'}
        </button>
        <button
          type="button"
          className="btn"
          disabled={loading || saving}
          onClick={handleResetDefaults}
        >
          恢复默认
        </button>
        <button type="button" className="btn" disabled={loading || saving} onClick={() => void load()}>
          重新加载
        </button>
      </div>
    </div>
  );
}
