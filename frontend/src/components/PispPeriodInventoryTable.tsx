import type { PispPeriodSnapshotDto } from '../types/ontology';
import './PispPeriodInventoryTable.css';

interface PispPeriodInventoryTableProps {
  productCode: string | null;
  snapshots: PispPeriodSnapshotDto[];
}

function formatQty(value: number): string {
  if (!Number.isFinite(value)) return '—';
  if (Number.isInteger(value)) return String(value);
  return value.toFixed(2);
}

export function PispPeriodInventoryTable({ productCode, snapshots }: PispPeriodInventoryTableProps) {
  if (snapshots.length === 0) {
    return <p className="empty">暂无周期进销存数据</p>;
  }

  return (
    <div className="pisp-period-inventory-table">
      <div className="pisp-period-inventory-table-head">
        <h4 className="pisp-period-inventory-table-title">周期进销存明细</h4>
        <span className="pisp-period-inventory-table-product">{productCode ?? 'UNKNOWN'}</span>
      </div>
      <div className="table-wrap pisp-period-inventory-table-wrap">
        <table className="data-table pisp-period-inventory-table-grid">
          <thead>
            <tr>
              <th>周期</th>
              <th className="num">期初库存</th>
              <th className="num">计划供应</th>
              <th className="num">计划需求</th>
              <th className="num">期末库存</th>
              <th className="num">缺货量</th>
            </tr>
          </thead>
          <tbody>
            {snapshots.map((row) => {
              const shortage = row.stockShortageQuantity > 0;
              return (
                <tr key={row.id} className={shortage ? 'is-shortage' : undefined}>
                  <td className="period-cell">{row.periodId}</td>
                  <td className="num">{formatQty(row.onHand)}</td>
                  <td className="num">{formatQty(row.plannedSupplyTotal)}</td>
                  <td className="num">{formatQty(row.plannedDemandQuantityTotal)}</td>
                  <td className="num">{formatQty(row.plannedInventoryLevel)}</td>
                  <td className={`num ${shortage ? 'shortage-qty' : ''}`}>
                    {formatQty(row.stockShortageQuantity)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
