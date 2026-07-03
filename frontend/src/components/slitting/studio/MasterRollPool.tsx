import { useMemo } from 'react';
import type { MasterRoll } from '../../../types/slitting';
import { FilterableTable } from '../../table/FilterableTable';
import {
  MASTER_COLUMN_OPTIONS,
  MASTER_COLUMNS,
  MASTER_DEFAULT_VISIBLE,
} from './poolColumns';

const DRAG_TYPE = 'slitting/master-roll';

export function masterRollDragPayload(roll: MasterRoll): string {
  return JSON.stringify({ rollCode: roll.rollCode });
}

export function parseMasterRollDrag(data: string): { rollCode: string } | null {
  try {
    return JSON.parse(data) as { rollCode: string };
  } catch {
    return null;
  }
}

export { DRAG_TYPE as MASTER_ROLL_DRAG_TYPE };

type Props = {
  masters: MasterRoll[];
};

export function MasterRollPool({ masters }: Props) {
  const columns = useMemo(() => MASTER_COLUMNS, []);

  return (
    <section className="slitting-studio-panel slitting-studio-panel--pool">
      <div className="slitting-pool-head">
        <h3 className="slitting-panel-title" title="拖到分切树以加入方案；行可拖拽">
          待分切母卷
        </h3>
      </div>
      <FilterableTable
        tableId="studio-master-pool"
        columns={columns}
        rows={masters}
        rowKey={(r) => r.rollCode}
        emptyText="母卷已全部加入树"
        wrapClassName="ft-table-wrap slitting-pool-table-wrap"
        tableClassName="slitting-pool-table"
        cellWrap
        entityType="MasterRoll"
        columnOptions={MASTER_COLUMN_OPTIONS}
        defaultVisibleKeys={MASTER_DEFAULT_VISIBLE}
        getRowProps={(row) => ({
          draggable: true,
          className: 'slitting-pool-row',
          onDragStart: (e: React.DragEvent<HTMLTableRowElement>) => {
            e.dataTransfer.setData(DRAG_TYPE, masterRollDragPayload(row));
            e.dataTransfer.effectAllowed = 'copy';
          },
        })}
      />
      <p className="slitting-pool-foot">{masters.length} 卷待排</p>
    </section>
  );
}
