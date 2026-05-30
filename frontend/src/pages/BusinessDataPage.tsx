import { useEffect, useMemo, useState, type ComponentType } from 'react';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { MasterDataTabBody, type TabConfig } from '../components/MasterDataTabBody';
import type { MasterDataRecord } from '../types/masterData';
import { consumeMasterDataTableFocus, type MasterDataTableFocus } from '../utils/masterDataFocus';
import { BUSINESS_DATA_TABS } from './masterDataTabConfigs';
import './MasterDataPage.css';

const TabBodyAny = MasterDataTabBody as ComponentType<{
  config: TabConfig<MasterDataRecord>;
  tableFocus?: MasterDataTableFocus | null;
}>;

const PLACEHOLDER_TABS = [
  { id: 'purchase-orders', label: '采购单', hint: '采购单与到货计划接口预留，可对接 ERP 采购订单。' },
  { id: 'production-feedback', label: '生产反馈', hint: 'MES 报工、完工与异常反馈预留，可对接生产执行系统。' },
] as const;

export function BusinessDataPage() {
  const [activeTabId, setActiveTabId] = useState(BUSINESS_DATA_TABS[0]?.id ?? 'sales-orders');
  const [tableFocus, setTableFocus] = useState<MasterDataTableFocus | null>(null);
  const activeTab = useMemo(
    () => BUSINESS_DATA_TABS.find((t) => t.id === activeTabId),
    [activeTabId],
  );
  const placeholder = PLACEHOLDER_TABS.find((t) => t.id === activeTabId);

  useEffect(() => {
    const focus = consumeMasterDataTableFocus('business-data');
    if (focus) {
      setActiveTabId(focus.tabId);
      setTableFocus(focus);
    }
  }, []);

  useEffect(() => {
    if (tableFocus && activeTabId !== tableFocus.tabId) {
      setTableFocus(null);
    }
  }, [activeTabId, tableFocus]);

  return (
    <div className="master-data-page">
      <PageHeader
        title="业务数据"
        description="维护订单、库存等交易类业务数据（采购单、生产反馈接口预留）"
      />
      <StatusBanner loading={false} error={null} />

      <div className="md-tab-bar" role="tablist">
        {BUSINESS_DATA_TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            className={`md-tab-btn ${t.id === activeTabId ? 'is-active' : ''}`}
            onClick={() => setActiveTabId(t.id)}
          >
            {t.label}
          </button>
        ))}
        {PLACEHOLDER_TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            className={`md-tab-btn ${t.id === activeTabId ? 'is-active' : ''}`}
            onClick={() => setActiveTabId(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="md-tab-content">
        {activeTab && (
          <TabBodyAny key={activeTab.id} config={activeTab} tableFocus={tableFocus} />
        )}
        {placeholder && (
          <div className="md-tab-body card">
            <p className="md-tab-desc">{placeholder.hint}</p>
            <p className="plan-param-empty">功能开发中，当前可通过演示数据或 ERP 集成扩展。</p>
          </div>
        )}
      </div>
    </div>
  );
}
