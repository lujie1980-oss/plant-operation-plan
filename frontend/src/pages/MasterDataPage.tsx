import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MasterDataExcelToolbar } from '../components/MasterDataExcelToolbar';
import { MasterDataHealthPanel } from '../components/MasterDataHealthPanel';
import { MasterFieldCatalogPanel } from '../components/MasterFieldCatalogPanel';
import { MasterDataSummaryTab } from '../components/MasterDataSummaryTab';
import { TabbedSectionPage } from '../components/TabbedSectionPage';
import {
  consumeMasterDataTableFocus,
  setMasterDataTableFocus,
  type MasterDataTableFocus,
} from '../utils/masterDataFocus';
import { routeForFocusPage } from '../utils/masterDataHealthNav';
import { MASTER_DATA_TABS } from './masterDataTabConfigs';

const SUMMARY_TAB_ID = 'product-summary';
const DATA_HEALTH_TAB_ID = 'data-health';
const FIELD_CATALOG_TAB_ID = 'field-catalog';

export function MasterDataPage() {
  const navigate = useNavigate();
  const [dataRevision, setDataRevision] = useState(0);
  const [activeTabId, setActiveTabId] = useState(SUMMARY_TAB_ID);
  const [tableFocus, setTableFocus] = useState<MasterDataTableFocus | null>(null);

  useEffect(() => {
    const focus = consumeMasterDataTableFocus('master-data');
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

  const handleNavigateFromHealth = useCallback(
    (focus: MasterDataTableFocus) => {
      if (focus.page === 'master-data') {
        setActiveTabId(focus.tabId);
        setTableFocus(focus);
        return;
      }
      setMasterDataTableFocus(focus);
      navigate(routeForFocusPage(focus.page, focus.tabId));
    },
    [navigate],
  );

  return (
    <TabbedSectionPage
      title="主数据管理"
      description="维护物料、BOM、资源、工艺、产线、日历等静态主数据；支持 Excel 模板下载与批量导入导出"
      tabs={MASTER_DATA_TABS}
      dataRevision={dataRevision}
      defaultTabId={SUMMARY_TAB_ID}
      activeTabId={activeTabId}
      onActiveTabChange={setActiveTabId}
      tableFocus={tableFocus}
      leadingTabs={[
        { id: SUMMARY_TAB_ID, label: '产品汇总' },
        { id: DATA_HEALTH_TAB_ID, label: '数据健康' },
        { id: FIELD_CATALOG_TAB_ID, label: '字段目录' },
      ]}
      renderCustomTab={(tabId) => {
        if (tabId === SUMMARY_TAB_ID) {
          return <MasterDataSummaryTab dataRevision={dataRevision} />;
        }
        if (tabId === DATA_HEALTH_TAB_ID) {
          return (
            <MasterDataHealthPanel
              dataRevision={dataRevision}
              onNavigateToRecord={handleNavigateFromHealth}
            />
          );
        }
        if (tabId === FIELD_CATALOG_TAB_ID) {
          return <MasterFieldCatalogPanel dataRevision={dataRevision} />;
        }
        return undefined;
      }}
      headerActions={<MasterDataExcelToolbar onImported={() => setDataRevision((n) => n + 1)} />}
    />
  );
}

// 兼容旧引用
export { MasterDataTabBody, type TabConfig } from '../components/MasterDataTabBody';
