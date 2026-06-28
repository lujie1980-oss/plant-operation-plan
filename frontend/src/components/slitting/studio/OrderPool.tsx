import { useMemo } from 'react';
import type { ChildSlittingOrder } from '../../../types/slitting';
import { FilterableTable } from '../../table/FilterableTable';
import {
  ORDER_COLUMN_OPTIONS,
  ORDER_COLUMNS,
  ORDER_DEFAULT_VISIBLE,
} from './poolColumns';

const DRAG_TYPE = 'slitting/child-order';

export function orderDragPayload(order: ChildSlittingOrder): string {
  return JSON.stringify({ orderCode: order.orderCode });
}

export function parseOrderDrag(data: string): { orderCode: string } | null {
  try {
    return JSON.parse(data) as { orderCode: string };
  } catch {
    return null;
  }
}

export { DRAG_TYPE as ORDER_DRAG_TYPE };

type Props = {
  orders: ChildSlittingOrder[];
};

export function OrderPool({ orders }: Props) {
  const columns = useMemo(() => ORDER_COLUMNS, []);

  return (
    <section className="slitting-studio-panel slitting-studio-panel--pool">
      <div className="slitting-pool-head">
        <h3 className="slitting-panel-title" title="表头可排序、筛选；拖到区域或画板">
          待分切订单
        </h3>
      </div>
      <FilterableTable
        tableId="studio-order-pool"
        columns={columns}
        rows={orders}
        rowKey={(r) => r.orderCode}
        emptyText="订单已全部排入"
        wrapClassName="ft-table-wrap slitting-pool-table-wrap"
        tableClassName="slitting-pool-table"
        cellWrap
        entityType="ChildSlittingOrder"
        columnOptions={ORDER_COLUMN_OPTIONS}
        defaultVisibleKeys={ORDER_DEFAULT_VISIBLE}
        getRowProps={(row) => ({
          draggable: true,
          className: 'slitting-pool-row slitting-pool-row--order',
          onDragStart: (e: React.DragEvent<HTMLTableRowElement>) => {
            e.dataTransfer.setData(DRAG_TYPE, orderDragPayload(row));
            e.dataTransfer.effectAllowed = 'copy';
          },
        })}
      />
      <p className="slitting-pool-foot">{orders.length} 单待排</p>
    </section>
  );
}
