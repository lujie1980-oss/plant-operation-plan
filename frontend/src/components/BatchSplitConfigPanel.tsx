import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { StatusBanner } from './StatusBanner';
import {
  BATCH_REMAINDER_MODE_OPTIONS,
  BATCH_SPLIT_MODE_OPTIONS,
  DEFAULT_BATCH_SPLIT_CONFIG,
  type BatchSplitConfig,
  type BatchSplitMode,
} from '../types/batchSplitConfig';
import {
  loadBatchSplitParameterRows,
  parseBatchSplitConfig,
  saveBatchSplitConfig,
} from '../utils/batchSplitConfig';
import './BatchSplitConfigPanel.css';

type BatchSplitConfigPanelProps = {
  onSavingChange?: (saving: boolean) => void;
};

export function BatchSplitConfigPanel({ onSavingChange }: BatchSplitConfigPanelProps) {
  const [draft, setDraft] = useState<BatchSplitConfig>(DEFAULT_BATCH_SPLIT_CONFIG);
  const [paramRows, setParamRows] = useState<Awaited<ReturnType<typeof loadBatchSplitParameterRows>>>([]);
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
      const rows = await loadBatchSplitParameterRows();
      setParamRows(rows);
      setDraft(parseBatchSplitConfig(rows));
      setDirty(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载批次拆解参数失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const update = <K extends keyof BatchSplitConfig>(key: K, value: BatchSplitConfig[K]) => {
    setDraft((prev) => {
      const next = { ...prev, [key]: value };
      if (key === 'minQty' && next.maxQty < next.minQty) {
        next.maxQty = next.minQty;
      }
      if (key === 'maxQty' && next.maxQty < next.minQty) {
        next.minQty = next.maxQty;
      }
      return next;
    });
    setDirty(true);
    setSuccess(null);
  };

  const handleSave = async () => {
    setSaving(true);
    onSavingChange?.(true);
    setError(null);
    setSuccess(null);
    try {
      await saveBatchSplitConfig(draft, paramRows);
      await load();
      setSuccess('批次拆解规则已保存；在批次计划页对工单执行「自动拆批」时生效');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
      onSavingChange?.(false);
    }
  };

  const handleResetDefaults = () => {
    setDraft({ ...DEFAULT_BATCH_SPLIT_CONFIG });
    setDirty(true);
    setSuccess(null);
  };

  const modeHint = BATCH_SPLIT_MODE_OPTIONS.find((o) => o.value === draft.mode)?.hint;
  const showFixed = draft.mode === 'FIXED_QTY' || draft.mode === 'AUTO';
  const showRemainder = draft.mode === 'FIXED_QTY';
  const showAutoBounds = draft.mode === 'AUTO';
  const showKitting = draft.mode === 'KITTING' || draft.mode === 'AUTO';

  const previewText = useMemo(() => buildPreview(draft), [draft]);

  return (
    <div className="batch-split-panel">
      <StatusBanner loading={loading || saving} error={error} success={success} />

      <section className="batch-split-section card">
        <h3 className="batch-split-section-title">拆批策略</h3>
        <p className="batch-split-section-desc">
          作用于已下发工单；拆批后仅批次进入 S05 排程。与 MRP 无关。
        </p>
        <div className="mf-catalog-form-grid">
          <label className="batch-split-span-2">
            策略模式
            <select
              className="input"
              value={draft.mode}
              disabled={loading || saving}
              onChange={(e) => update('mode', e.target.value as BatchSplitMode)}
            >
              {BATCH_SPLIT_MODE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            {modeHint && <span className="batch-split-field-hint">{modeHint}</span>}
          </label>
        </div>
      </section>

      {showFixed && (
        <section className="batch-split-section card">
          <h3 className="batch-split-section-title">
            {draft.mode === 'AUTO' ? '自动拆批 · 基准批量' : '固定批量'}
          </h3>
          <div className="mf-catalog-form-grid">
            <label>
              {draft.mode === 'AUTO' ? '首选批量' : '每批数量'}
              <input
                className="input"
                type="number"
                min={1}
                step={1}
                value={draft.fixedQty}
                disabled={loading || saving}
                onChange={(e) =>
                  update('fixedQty', Math.max(1, Number.parseInt(e.target.value, 10) || 1))
                }
              />
            </label>
            {showRemainder && (
              <label>
                余数处理
                <select
                  className="input"
                  value={draft.remainderMode}
                  disabled={loading || saving}
                  onChange={(e) =>
                    update(
                      'remainderMode',
                      e.target.value as BatchSplitConfig['remainderMode'],
                    )
                  }
                >
                  {BATCH_REMAINDER_MODE_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <span className="batch-split-field-hint">
                  {
                    BATCH_REMAINDER_MODE_OPTIONS.find((o) => o.value === draft.remainderMode)
                      ?.hint
                  }
                </span>
              </label>
            )}
          </div>
        </section>
      )}

      {showAutoBounds && (
        <section className="batch-split-section card">
          <h3 className="batch-split-section-title">自动拆批 · 批量上下限</h3>
          <p className="batch-split-section-desc">
            目标批量会根据交期（≤3 天缩小、≥21 天放大）与工艺总工时相对产线班产能（85%）自动调整，并限制在本范围内。
          </p>
          <div className="mf-catalog-form-grid">
            <label>
              最小批量
              <input
                className="input"
                type="number"
                min={1}
                step={1}
                value={draft.minQty}
                disabled={loading || saving}
                onChange={(e) =>
                  update('minQty', Math.max(1, Number.parseInt(e.target.value, 10) || 1))
                }
              />
            </label>
            <label>
              最大批量
              <input
                className="input"
                type="number"
                min={draft.minQty}
                step={1}
                value={draft.maxQty}
                disabled={loading || saving}
                onChange={(e) =>
                  update(
                    'maxQty',
                    Math.max(draft.minQty, Number.parseInt(e.target.value, 10) || draft.minQty),
                  )
                }
              />
            </label>
          </div>
        </section>
      )}

      {showKitting && (
        <section className="batch-split-section card">
          <h3 className="batch-split-section-title">齐套相关</h3>
          <label className="batch-split-checkbox">
            <input
              type="checkbox"
              checked={draft.kittingCreateShortBatch}
              disabled={loading || saving}
              onChange={(e) => update('kittingCreateShortBatch', e.target.checked)}
            />
            未齐套剩余量创建 SHORT 批次
          </label>
          <p className="batch-split-field-hint">
            关闭时，无法齐套的部分留在父工单（PARTIAL），不生成缺料批次。
          </p>
        </section>
      )}

      {draft.mode === 'NONE' && (
        <section className="batch-split-section card">
          <h3 className="batch-split-section-title">下发时行为</h3>
          <p className="batch-split-field-hint">
            策略为「不拆批次」时，工单下发后自动为每张工单创建 1 个整单默认批次（数量 = 工单量），无需手工拆批。
          </p>
        </section>
      )}

      {draft.mode !== 'NONE' && (
        <section className="batch-split-section card">
          <h3 className="batch-split-section-title">下发时行为</h3>
          <label className="batch-split-checkbox">
            <input
              type="checkbox"
              checked={draft.autoOnDispatch}
              disabled={loading || saving}
              onChange={(e) => update('autoOnDispatch', e.target.checked)}
            />
            工单下发后自动按当前策略拆批
          </label>
        </section>
      )}

      {draft.mode !== 'NONE' && (
        <section className="batch-split-preview card">
          <h3 className="batch-split-section-title">规则摘要</h3>
          <p className="batch-split-preview-text">{previewText}</p>
          <p className="batch-split-field-hint">
            保存后前往 <Link to="/scheduling/batch-plan">批次计划</Link> 对工单右键「自动拆批」，或依赖下发时自动拆批。
          </p>
        </section>
      )}

      <div className="batch-split-actions">
        <button
          type="button"
          className="btn btn-primary"
          disabled={loading || saving || !dirty}
          onClick={() => void handleSave()}
        >
          {saving ? '保存中…' : '保存'}
        </button>
        <button type="button" className="btn" disabled={loading || saving} onClick={handleResetDefaults}>
          恢复默认
        </button>
        <button type="button" className="btn" disabled={loading || saving} onClick={() => void load()}>
          重新加载
        </button>
      </div>
    </div>
  );
}

function buildPreview(config: BatchSplitConfig): string {
  const modeLabel = BATCH_SPLIT_MODE_OPTIONS.find((o) => o.value === config.mode)?.label ?? config.mode;
  if (config.mode === 'NONE') {
    return '每工单 1 个整单默认批次 · 下发后自动创建';
  }
  const parts = [`策略：${modeLabel}`];
  if (config.mode === 'FIXED_QTY') {
    const rem =
      BATCH_REMAINDER_MODE_OPTIONS.find((o) => o.value === config.remainderMode)?.label ??
      config.remainderMode;
    parts.push(`每批 ${config.fixedQty}，余数：${rem}`);
  }
  if (config.mode === 'AUTO') {
    parts.push(`批量 ${config.minQty}–${config.maxQty}（基准 ${config.fixedQty}）`);
  }
  if (config.mode === 'KITTING' || config.mode === 'AUTO') {
    parts.push(config.kittingCreateShortBatch ? '缺料建 SHORT 批' : '缺料留父工单');
  }
  if (config.autoOnDispatch) {
    parts.push('下发时自动拆批');
  }
  return parts.join(' · ');
}
