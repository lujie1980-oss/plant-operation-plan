import { useMemo } from 'react';
import type { MasterRoll } from '../../../types/slitting';
import type { MaterialCatalog } from '../../../utils/materialCatalog';
import type { BomLevel, SourceMode, StudioSourceSelection } from '../../../utils/slitting/studioBomLevels';
import { bomLevelChineseLabel } from '../../../utils/slitting/studioBomLevels';
import { FilterableTable } from '../../table/FilterableTable';
import type { TableColumnDef } from '../../table/FilterableTable';
import { MASTER_COLUMN_OPTIONS, MASTER_COLUMNS, MASTER_DEFAULT_VISIBLE } from './poolColumns';
import { MASTER_ROLL_DRAG_TYPE, masterRollDragPayload } from './MasterRollPool';
import { BOM_MATERIAL_DRAG_TYPE, bomMaterialDragPayload } from './bomMaterialDrag';

export type BomSourceRow = {
  key: string;
  productCode: string;
  materialName: string;
  level: BomLevel;
};

const BOM_COLUMNS: TableColumnDef<BomSourceRow>[] = [
  {
    key: 'productCode',
    header: '料号',
    defaultWidth: 160,
    filterable: true,
    sortable: true,
    render: (r) => r.productCode,
    getFilterText: (r) => r.productCode,
    getSortValue: (r) => r.productCode,
  },
  {
    key: 'materialName',
    header: '物料名称',
    defaultWidth: 120,
    filterable: true,
    sortable: true,
    render: (r) => r.materialName,
    getFilterText: (r) => r.materialName,
    getSortValue: (r) => r.materialName,
  },
  {
    key: 'level',
    header: '层级',
    defaultWidth: 56,
    align: 'center',
    sortable: true,
    render: (r) => bomLevelChineseLabel(r.level),
    getSortValue: (r) => r.level,
  },
];

type Props = {
  sourceMode: SourceMode;
  bomLevel: BomLevel;
  bomLevelOptions: number[];
  onSourceModeChange: (mode: SourceMode) => void;
  onBomLevelChange: (level: BomLevel) => void;
  inventoryRows: MasterRoll[];
  bomRows: BomSourceRow[];
  selectedSource: StudioSourceSelection | null;
  onSelectSource: (source: StudioSourceSelection | null) => void;
};

function isSameSource(a: StudioSourceSelection | null, b: StudioSourceSelection | null): boolean {
  if (!a || !b) return false;
  if (a.kind !== b.kind) return false;
  if (a.kind === 'roll' && b.kind === 'roll') return a.rollCode === b.rollCode;
  if (a.kind === 'bom' && b.kind === 'bom') return a.productCode === b.productCode;
  return false;
}

export function SourcePoolPanel({
  sourceMode,
  bomLevel,
  bomLevelOptions,
  onSourceModeChange,
  onBomLevelChange,
  inventoryRows,
  bomRows,
  selectedSource,
  onSelectSource,
}: Props) {
  const masterColumns = useMemo(() => MASTER_COLUMNS, []);
  const rowCount = sourceMode === 'inventory' ? inventoryRows.length : bomRows.length;

  return (
    <section className="slitting-studio-panel slitting-studio-panel--pool">
      <div className="slitting-pool-head slitting-source-pool-head">
        <h3 className="slitting-panel-title">分切来源</h3>
        <div className="slitting-source-filters">
          <label className="slitting-source-filter">
            <span>视图</span>
            <select
              value={sourceMode}
              onChange={(e) => onSourceModeChange(e.target.value as SourceMode)}
            >
              <option value="inventory">按库存</option>
              <option value="bom">按 BOM</option>
            </select>
          </label>
          <label className="slitting-source-filter">
            <span>BOM 层级</span>
            <select
              value={bomLevel}
              onChange={(e) => onBomLevelChange(Number(e.target.value))}
            >
              {bomLevelOptions.map((level) => (
                <option key={level} value={level}>
                  {bomLevelChineseLabel(level)}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <p className="slitting-panel-hint">
        {sourceMode === 'inventory'
          ? `展示 BOM ${bomLevelChineseLabel(bomLevel)} 对应库存母卷；点击选中，母卷行可拖到分切树。`
          : `展示 BOM ${bomLevelChineseLabel(bomLevel)} 物料；点击选中后在右侧查看可分切需求，物料行可拖到分切树。`}
      </p>
      {sourceMode === 'inventory' ? (
        <FilterableTable
          tableId="studio-source-inventory"
          columns={masterColumns}
          rows={inventoryRows}
          rowKey={(r) => r.rollCode}
          emptyText="该层级暂无库存母卷"
          wrapClassName="ft-table-wrap slitting-pool-table-wrap"
          tableClassName="slitting-pool-table"
          cellWrap
          entityType="MasterRoll"
          columnOptions={MASTER_COLUMN_OPTIONS}
          defaultVisibleKeys={MASTER_DEFAULT_VISIBLE}
          getRowProps={(row) => {
            const selected =
              selectedSource?.kind === 'roll' && selectedSource.rollCode === row.rollCode;
            return {
              draggable: true,
              className: selected ? 'slitting-pool-row is-selected' : 'slitting-pool-row',
              onClick: () =>
                onSelectSource({
                  kind: 'roll',
                  rollCode: row.rollCode,
                  productCode: row.productCode ?? row.finishedProductCode ?? row.materialCode ?? '',
                }),
              onDragStart: (e: React.DragEvent<HTMLTableRowElement>) => {
                e.dataTransfer.setData(MASTER_ROLL_DRAG_TYPE, masterRollDragPayload(row));
                e.dataTransfer.effectAllowed = 'copy';
              },
            };
          }}
        />
      ) : (
        <FilterableTable
          tableId="studio-source-bom"
          columns={BOM_COLUMNS}
          rows={bomRows}
          rowKey={(r) => r.key}
          emptyText="该层级暂无 BOM 物料"
          wrapClassName="ft-table-wrap slitting-pool-table-wrap"
          tableClassName="slitting-pool-table"
          cellWrap
          enableSort
          getRowProps={(row) => {
            const selected =
              selectedSource?.kind === 'bom' && selectedSource.productCode === row.productCode;
            return {
              draggable: true,
              className: selected ? 'slitting-pool-row is-selected' : 'slitting-pool-row',
              onClick: () => onSelectSource({ kind: 'bom', productCode: row.productCode }),
              onDragStart: (e: React.DragEvent<HTMLTableRowElement>) => {
                e.dataTransfer.setData(BOM_MATERIAL_DRAG_TYPE, bomMaterialDragPayload(row.productCode));
                e.dataTransfer.effectAllowed = 'copy';
              },
            };
          }}
        />
      )}
      <p className="slitting-pool-foot">
        {rowCount} 项
        {selectedSource ? ` · 已选 ${selectedSource.kind === 'roll' ? selectedSource.rollCode : selectedSource.productCode}` : ''}
      </p>
    </section>
  );
}

export function buildBomSourceRows(
  productCodes: string[],
  level: BomLevel,
  catalog: MaterialCatalog,
): BomSourceRow[] {
  return productCodes.map((code) => ({
    key: `${level}-${code}`,
    productCode: code,
    materialName: catalog.materialName(code),
    level,
  }));
}

export { isSameSource };
