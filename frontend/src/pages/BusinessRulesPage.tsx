import { useCallback, useEffect, useMemo, useState, type ComponentType } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { consumeMasterDataTableFocus, type MasterDataTableFocus } from '../utils/masterDataFocus';
import { api } from '../api/client';
import { BusinessRulesExcelToolbar } from '../components/BusinessRulesExcelToolbar';
import { BusinessRuleDescriptionHeader } from '../components/BusinessRuleDescriptionHeader';
import { BusinessRuleScopePanel, useBusinessRuleScopes } from '../components/BusinessRuleScopePanel';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { MasterDataTabBody, type TabConfig } from '../components/MasterDataTabBody';
import type { MasterDataRecord } from '../types/masterData';
import type { RuleSetVersion } from '../types/api';
import {
  BUSINESS_RULE_CATEGORIES,
  defaultCategoryId,
  isRuleCategoryId,
  tabsForCategory,
  type RuleCategoryDef,
} from './businessRuleCategories';
import './BusinessRulesPage.css';
import './MasterDataPage.css';

const TabBodyAny = MasterDataTabBody as ComponentType<{
  config: TabConfig<MasterDataRecord>;
  onDataChange?: () => void;
  tableFocus?: MasterDataTableFocus | null;
}>;

export function BusinessRulesPage() {
  const { categoryId: categoryIdParam } = useParams<{ categoryId: string }>();
  const categoryId = isRuleCategoryId(categoryIdParam ?? '') ? categoryIdParam : null;

  const category: RuleCategoryDef | undefined = BUSINESS_RULE_CATEGORIES.find((c) => c.id === categoryId);
  const categoryTabs = useMemo(() => (categoryId ? tabsForCategory(categoryId) : []), [categoryId]);

  const [activeTabId, setActiveTabId] = useState('');
  const [ruleVersions, setRuleVersions] = useState<RuleSetVersion[]>([]);
  const [selectedRuleVersionId, setSelectedRuleVersionId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [newVersionName, setNewVersionName] = useState('');
  const [dataRevision, setDataRevision] = useState(0);
  const [tableFocus, setTableFocus] = useState<MasterDataTableFocus | null>(null);
  const { scopesById, scopeError, updateScope, replaceScope } = useBusinessRuleScopes();
  const [descSaving, setDescSaving] = useState(false);

  useEffect(() => {
    if (categoryTabs.length === 0) return;
    setActiveTabId((prev) => {
      if (prev && categoryTabs.some((t) => t.id === prev)) return prev;
      return categoryTabs[0].id;
    });
  }, [categoryTabs]);

  const activeTab = useMemo(
    () => categoryTabs.find((t) => t.id === activeTabId) ?? categoryTabs[0],
    [categoryTabs, activeTabId],
  );

  useEffect(() => {
    const focus = consumeMasterDataTableFocus('business-rules');
    if (focus && categoryTabs.some((t) => t.id === focus.tabId)) {
      setActiveTabId(focus.tabId);
      setTableFocus(focus);
    }
  }, [categoryTabs]);

  useEffect(() => {
    if (tableFocus && activeTabId !== tableFocus.tabId) {
      setTableFocus(null);
    }
  }, [activeTabId, tableFocus]);

  const loadVersions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await api.listRuleSetVersions();
      setRuleVersions(list);
      setSelectedRuleVersionId((prev) => {
        if (prev && list.some((r) => r.ruleSetVersionId === prev)) return prev;
        return list.find((r) => r.isDefault)?.ruleSetVersionId ?? list[0]?.ruleSetVersionId ?? '';
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载规则版本失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadVersions();
  }, [loadVersions]);

  const createVersion = async () => {
    if (!newVersionName.trim()) {
      setError('请输入规则版本名称');
      return;
    }
    setError(null);
    setSuccess(null);
    try {
      const created = await api.createRuleSetVersion({
        name: newVersionName.trim(),
        copyFromRuleSetVersionId: selectedRuleVersionId || undefined,
      });
      setNewVersionName('');
      await loadVersions();
      setSelectedRuleVersionId(created.ruleSetVersionId);
      setSuccess(`已创建规则版本「${created.name}」`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建失败');
    }
  };

  const syncFromWorkspace = async () => {
    if (!selectedRuleVersionId) return;
    setError(null);
    setSuccess(null);
    try {
      await api.syncRuleSetFromWorkspace(selectedRuleVersionId);
      setSuccess('已从当前工作区主数据同步规则快照');
      await loadVersions();
    } catch (e) {
      setError(e instanceof Error ? e.message : '同步失败');
    }
  };

  if (!categoryId || !category) {
    return <Navigate to={`/business-rules/${defaultCategoryId()}`} replace />;
  }

  const selectedRule = ruleVersions.find((r) => r.ruleSetVersionId === selectedRuleVersionId);
  const editingLiveData = selectedRule?.isDefault ?? true;

  return (
    <div className="master-data-page business-rules-page">
      <PageHeader title={category.label} description={category.description} />
      <StatusBanner loading={loading} error={error ?? scopeError} success={success} />

      <section className="card br-version-panel">
        <div className="br-version-toolbar">
          <label className="br-version-select">
            <span>规则版本</span>
            <select
              className="input"
              value={selectedRuleVersionId}
              onChange={(e) => setSelectedRuleVersionId(e.target.value)}
            >
              {ruleVersions.map((r) => (
                <option key={r.ruleSetVersionId} value={r.ruleSetVersionId}>
                  {r.name}
                  {r.isDefault ? '（默认）' : ''}
                </option>
              ))}
            </select>
          </label>
          <input
            className="input br-version-name"
            placeholder="新版本名称"
            value={newVersionName}
            onChange={(e) => setNewVersionName(e.target.value)}
          />
          <button type="button" className="btn" onClick={() => void createVersion()}>
            新建版本
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => void syncFromWorkspace()}
            disabled={!selectedRuleVersionId}
            title="将当前工作区规则写入所选版本快照"
          >
            同步快照
          </button>
        </div>
        {!editingLiveData && (
          <p className="br-version-hint">
            当前选中「{selectedRule?.name}」为非默认版本：下方表格仍编辑工作区实时数据；修改后请点「同步快照」保存到该版本。
          </p>
        )}
        {editingLiveData && (
          <p className="br-version-hint">
            默认规则版本与工作区主数据一致；计划运行时可选用非默认规则版本。
          </p>
        )}
      </section>

      <div className="br-rules-layout">
        <aside className="card br-rules-nav" aria-label="规则项目">
          <h3 className="br-rules-nav-title">规则项目</h3>
          <ul className="br-rules-nav-list" role="listbox">
            {categoryTabs.map((tab) => (
              <li key={tab.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={tab.id === activeTabId}
                  className={`br-rules-nav-item ${tab.id === activeTabId ? 'is-active' : ''}`}
                  onClick={() => setActiveTabId(tab.id)}
                >
                  <span className="br-rules-nav-item-label">{tab.label}</span>
                  {(scopesById[tab.id]?.description ?? tab.description) && (
                    <span className="br-rules-nav-item-desc">
                      {scopesById[tab.id]?.description ?? tab.description}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <div className="card br-rules-main">
          {categoryTabs.length === 0 ? (
            <div className="br-rules-main-inner">
              <p className="empty-hint">
                当前分类暂无独立规则页签。产能利用率与负荷由主数据中的生产资源日历及「计划分析 → 产能平衡」计算。
              </p>
            </div>
          ) : activeTab ? (
            <div className="br-rules-main-inner">
              <BusinessRuleDescriptionHeader
                label={activeTab.label}
                description={scopesById[activeTabId]?.description ?? activeTab.description ?? ''}
                saving={descSaving}
                onSave={async (description) => {
                  setDescSaving(true);
                  try {
                    await updateScope(activeTabId, { description });
                    setSuccess('规则说明已保存');
                  } finally {
                    setDescSaving(false);
                  }
                }}
              />
              <BusinessRuleScopePanel
                ruleTypeId={activeTabId}
                scope={scopesById[activeTabId] ?? null}
                onScopeUpdated={replaceScope}
              />
              <BusinessRulesExcelToolbar
                activeTabId={activeTabId}
                onImported={() => setDataRevision((n) => n + 1)}
              />
              <div className="md-tab-content">
                <TabBodyAny
                  key={`${activeTab.id}-${dataRevision}`}
                  config={activeTab}
                  onDataChange={() => setDataRevision((n) => n + 1)}
                  tableFocus={tableFocus}
                />
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
