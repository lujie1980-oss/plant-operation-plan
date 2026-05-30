import { useCallback, useEffect, useMemo, useState } from 'react';
import { EditableTable } from './EditableTable';
import { PageHeader } from './PageHeader';
import { StatusBanner } from './StatusBanner';
import { parameterTab } from '../pages/businessRulesTabs';
import {
  PARAMS_MANAGED_ELSEWHERE,
  type ParamGroupDef,
} from '../pages/planParameterGroups';
import type { SystemParameterMd } from '../types/masterData';
import '../pages/MasterDataPage.css';

type ParamSection = ParamGroupDef & {
  rows: SystemParameterMd[];
};

type PlanParametersViewProps = {
  groups: ParamGroupDef[];
  title: string;
  description: string;
  /** 展示未归入任一分组且非专用页维护的参数（单独「其他」页签） */
  showOtherGroup?: boolean;
  otherKnownParamIds?: Set<string>;
};

export function PlanParametersView({
  groups,
  title,
  description,
  showOtherGroup = false,
  otherKnownParamIds,
}: PlanParametersViewProps) {
  const [rows, setRows] = useState<SystemParameterMd[]>([]);
  const [activeTabId, setActiveTabId] = useState(groups[0]?.id ?? '');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await parameterTab.api.list());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const sections = useMemo(() => {
    const known =
      otherKnownParamIds ??
      new Set([...groups.flatMap((g) => g.paramIds), ...PARAMS_MANAGED_ELSEWHERE]);
    const byId = new Map(rows.map((r) => [r.paramId, r]));
    const result: ParamSection[] = groups.map((g) => ({
      ...g,
      rows: g.paramIds.map((id) => byId.get(id)).filter((r): r is SystemParameterMd => r != null),
    }));
    if (showOtherGroup) {
      const other = rows.filter(
        (r) => !known.has(r.paramId) && !PARAMS_MANAGED_ELSEWHERE.has(r.paramId),
      );
      if (other.length > 0) {
        result.push({
          id: 'other',
          label: '其他',
          description:
            '尚未归入标准分组的参数。请确认是否应纳入上述页签；优化目标与策略请在「优化目标」页维护。',
          paramIds: [],
          rows: other,
        });
      }
    }
    return result;
  }, [rows, groups, showOtherGroup, otherKnownParamIds]);

  useEffect(() => {
    if (sections.length === 0) return;
    if (!sections.some((s) => s.id === activeTabId)) {
      setActiveTabId(sections[0].id);
    }
  }, [sections, activeTabId]);

  const activeSection = sections.find((s) => s.id === activeTabId) ?? sections[0];

  const handleSave = async (row: SystemParameterMd) => {
    setSaving(true);
    try {
      const saved = await parameterTab.api.save(row);
      setRows((prev) => (row.id == null ? [...prev, saved] : prev.map((r) => (r.id === row.id ? saved : r))));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: SystemParameterMd) => {
    if (row.id == null) return;
    await parameterTab.api.delete(row.id);
    setRows((prev) => prev.filter((r) => r.id !== row.id));
  };

  return (
    <div className="master-data-page plan-params-page">
      <PageHeader
        title={title}
        description={description}
        actions={
          <button type="button" className="btn" onClick={() => void load()} disabled={loading}>
            刷新
          </button>
        }
      />
      <StatusBanner loading={loading || saving} error={error} />

      {sections.length > 0 && (
        <>
          <div className="md-tab-bar" role="tablist">
            {sections.map((s) => (
              <button
                key={s.id}
                type="button"
                role="tab"
                aria-selected={s.id === activeTabId}
                className={`md-tab-btn ${s.id === activeTabId ? 'is-active' : ''}`}
                onClick={() => setActiveTabId(s.id)}
              >
                {s.label}
              </button>
            ))}
          </div>

          <div className="md-tab-content plan-param-tab-panel">
            {activeSection && (
              <section className="card plan-param-group">
                <p className="md-tab-desc">{activeSection.description}</p>
                {activeSection.rows.length === 0 ? (
                  <p className="plan-param-empty">
                    暂无参数记录。请刷新页面或联系管理员执行参数初始化。
                  </p>
                ) : (
                  <EditableTable<SystemParameterMd>
                    tableId={`plan-params-${activeSection.id}`}
                    rows={activeSection.rows}
                    columns={parameterTab.columns}
                    rowKey={parameterTab.rowKey}
                    emptyRow={parameterTab.emptyRow}
                    onSave={handleSave}
                    onDelete={handleDelete}
                    loading={loading}
                    saving={saving}
                    search={parameterTab.search}
                  />
                )}
              </section>
            )}
          </div>
        </>
      )}
    </div>
  );
}
