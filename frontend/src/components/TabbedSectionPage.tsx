import { useMemo, useState, type ComponentType, type ReactNode } from 'react';
import { PageHeader } from './PageHeader';
import { StatusBanner } from './StatusBanner';
import type { MasterDataRecord } from '../types/masterData';
import type { MasterDataTableFocus } from '../utils/masterDataFocus';
import { MasterDataTabBody, type TabConfig } from './MasterDataTabBody';
import '../pages/MasterDataPage.css';

const TabBodyAny = MasterDataTabBody as ComponentType<{
  config: TabConfig<MasterDataRecord>;
  tableFocus?: MasterDataTableFocus | null;
  onDataChange?: () => void;
}>;

export interface SectionTabItem {
  id: string;
  label: string;
}

export function TabbedSectionPage({
  title,
  description,
  tabs,
  headerActions,
  statusBanner,
  dataRevision = 0,
  leadingTabs = [],
  renderCustomTab,
  defaultTabId,
  activeTabId: controlledTabId,
  onActiveTabChange,
  tableFocus,
  onDataChange,
}: {
  title: string;
  description?: string;
  tabs: TabConfig<MasterDataRecord>[];
  headerActions?: ReactNode;
  statusBanner?: ReactNode;
  /** 变更后递增以刷新当前 Tab 表格数据（如 Excel 导入后） */
  dataRevision?: number;
  /** 表格页签之前的自定义页签（如汇总视图） */
  leadingTabs?: SectionTabItem[];
  renderCustomTab?: (tabId: string) => ReactNode;
  defaultTabId?: string;
  activeTabId?: string;
  onActiveTabChange?: (tabId: string) => void;
  tableFocus?: MasterDataTableFocus | null;
  onDataChange?: () => void;
}) {
  const allTabItems = useMemo(
    () => [...leadingTabs, ...tabs.map((t) => ({ id: t.id, label: t.label }))],
    [leadingTabs, tabs],
  );
  const [internalTabId, setInternalTabId] = useState(
    defaultTabId ?? allTabItems[0]?.id ?? '',
  );
  const activeTabId = controlledTabId ?? internalTabId;
  const setActiveTabId = (id: string) => {
    if (controlledTabId == null) {
      setInternalTabId(id);
    }
    onActiveTabChange?.(id);
  };
  const activeTableTab = useMemo(
    () => tabs.find((t) => t.id === activeTabId) ?? null,
    [tabs, activeTabId],
  );
  const customContent = renderCustomTab?.(activeTabId);

  if (allTabItems.length === 0) {
    return null;
  }

  return (
    <div className="master-data-page">
      <PageHeader title={title} description={description} actions={headerActions} />
      {statusBanner ?? <StatusBanner loading={false} error={null} />}

      <div className="md-tab-bar" role="tablist">
        {allTabItems.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={t.id === activeTabId}
            className={`md-tab-btn ${t.id === activeTabId ? 'is-active' : ''}`}
            onClick={() => setActiveTabId(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="md-tab-content">
        {customContent ?? (
          activeTableTab && (
            <TabBodyAny
              key={`${activeTableTab.id}-${dataRevision}`}
              config={activeTableTab}
              tableFocus={tableFocus}
              onDataChange={onDataChange}
            />
          )
        )}
      </div>
    </div>
  );
}
