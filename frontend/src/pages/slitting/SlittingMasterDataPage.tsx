import { useCallback, useEffect, useState } from 'react';

import { api } from '../../api/client';
import { slittingClient } from '../../api/slittingClient';
import { SlittingDataOverviewTab } from '../../components/slitting/SlittingDataOverviewTab';
import { SlittingMasterRollTabBody } from '../../components/slitting/SlittingMasterRollTabBody';
import { MasterDataExcelToolbar } from '../../components/MasterDataExcelToolbar';
import { TabbedSectionPage } from '../../components/TabbedSectionPage';
import { StatusBanner } from '../../components/StatusBanner';
import type { BomMd, InventoryMd, MaterialMd, ProductResourceMd, SalesOrderMd } from '../../types/masterData';
import type { MasterRoll } from '../../types/slitting';
import { SLITTING_MASTER_DATA_TABS } from './slittingMasterDataTabConfigs';
import '../../components/slitting/slitting.css';
import '../MasterDataPage.css';

type OverviewData = {
  materials: MaterialMd[];
  boms: BomMd[];
  salesOrders: SalesOrderMd[];
  inventory: InventoryMd[];
  masterRolls: MasterRoll[];
  productResources: ProductResourceMd[];
};

const OVERVIEW_TAB_ID = 'overview';

export function SlittingMasterDataPage() {
  const [dataRevision, setDataRevision] = useState(0);
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const loadOverview = useCallback(async () => {
    setErr(null);
    try {
      const [masterRolls, salesOrders, inventory, boms, materials, productResources] = await Promise.all([
        slittingClient.listMasterRolls(),
        api.masterData.salesOrders.list(),
        api.masterData.inventory.list(),
        api.masterData.boms.list(),
        api.masterData.materials.list(),
        api.masterData.productResources.list(),
      ]);
      setOverview({
        materials,
        boms,
        salesOrders,
        inventory,
        masterRolls,
        productResources,
      });
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview, dataRevision]);

  const bumpRevision = useCallback(() => {
    setDataRevision((n) => n + 1);
  }, []);

  return (
    <div className="page slitting-module">
      <TabbedSectionPage
        title="分切基础数据"
        description="维护物料、BOM、母卷库存与销售订单；支持 Excel 批量导入导出"
        leadingTabs={[{ id: OVERVIEW_TAB_ID, label: '数据总览' }]}
        tabs={SLITTING_MASTER_DATA_TABS}
        dataRevision={dataRevision}
        statusBanner={<StatusBanner error={err} />}
        headerActions={<MasterDataExcelToolbar onImported={bumpRevision} />}
        onDataChange={bumpRevision}
        renderCustomTab={(tabId) => {
          if (tabId === OVERVIEW_TAB_ID && overview) {
            return (
              <SlittingDataOverviewTab
                materials={overview.materials}
                boms={overview.boms}
                salesOrders={overview.salesOrders}
                inventory={overview.inventory}
                masterRolls={overview.masterRolls}
                productResources={overview.productResources}
              />
            );
          }
          if (tabId === 'master-rolls') {
            return (
              <SlittingMasterRollTabBody
                key={dataRevision}
                onDataChange={bumpRevision}
              />
            );
          }
          return undefined;
        }}
      />
    </div>
  );
}
