import { useMemo } from 'react';
import type { ChildSlittingOrder } from '../../../types/slitting';
import type { StudioSourceSelection } from '../../../utils/slitting/studioBomLevels';
import { FilterableTable } from '../../table/FilterableTable';
import {
  ORDER_COLUMN_OPTIONS,
  ORDER_COLUMNS,
  ORDER_DEFAULT_VISIBLE,
} from './poolColumns';
import { orderDragPayload, ORDER_DRAG_TYPE } from './OrderPool';

type Props = {
  demands: ChildSlittingOrder[];
  selectedSource: StudioSourceSelection | null;
  sourceLabel: string;
};

export function DemandPoolPanel({ demands, selectedSource, sourceLabel }: Props) {
  const columns = useMemo(() => ORDER_COLUMNS, []);

  return (
    <section className="slitting-studio-panel slitting-studio-panel--pool">
      <div className="slitting-pool-head">
        <h3 className="slitting-panel-title">可分切需求</h3>
      </div>
      <p className="slitting-panel-hint">
        {selectedSource
          ? `来源：${sourceLabel} · 下列订单可由该来源直接分切产出（${demands.length} 单）`
          : '请先在左侧选择母卷或 BOM 物料'}
      </p>
      <FilterableTable
        tableId="studio-demand-pool"
        columns={columns}
        rows={demands}
        rowKey={(r) => r.orderCode}
        emptyText={selectedSource ? '暂无匹配的分切订单' : '未选择来源'}
        wrapClassName="ft-table-wrap slitting-pool-table-wrap"
        tableClassName="slitting-pool-table"
        cellWrap
        entityType="ChildSlittingOrder"
        columnOptions={ORDER_COLUMN_OPTIONS}
        defaultVisibleKeys={ORDER_DEFAULT_VISIBLE}
        enableSort
        getRowProps={(row) => ({
          draggable: Boolean(selectedSource),
          className: 'slitting-pool-row slitting-pool-row--order',
          onDragStart: (e: React.DragEvent<HTMLTableRowElement>) => {
            e.dataTransfer.setData(ORDER_DRAG_TYPE, orderDragPayload(row));
            e.dataTransfer.effectAllowed = 'copy';
          },
        })}
      />
      <p className="slitting-pool-foot">可拖到分切树区域或右下画板</p>
    </section>
  );
}
