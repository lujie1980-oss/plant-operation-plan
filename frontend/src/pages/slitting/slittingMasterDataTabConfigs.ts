import type { MasterDataRecord } from '../../types/masterData';
import type { TabConfig } from '../../components/MasterDataTabBody';
import { bomTab, materialTab, salesOrderTab } from '../masterDataTabConfigs';

/** 占位 Tab：实际内容由 SlittingMasterRollTabBody 渲染 */
export const masterRollTabPlaceholder: TabConfig<MasterDataRecord> = {
  id: 'master-rolls',
  label: '母卷库存',
  description: '分切排样用母卷（宽×长 mm）；支持新增、修改、删除',
  api: {
    list: async () => [],
    save: async (dto) => dto,
    delete: async () => {},
  },
  rowKey: () => '',
  emptyRow: () => ({ id: null }),
  columns: [],
};

export const SLITTING_MASTER_DATA_TABS: TabConfig<MasterDataRecord>[] = [
  materialTab as unknown as TabConfig<MasterDataRecord>,
  bomTab as unknown as TabConfig<MasterDataRecord>,
  masterRollTabPlaceholder,
  salesOrderTab as unknown as TabConfig<MasterDataRecord>,
];
