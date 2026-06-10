import type { SrpSnapshotDto } from '../types/ontology';
import './SrpCapacityTable.css';

interface SrpCapacityTableProps {
  rows: SrpSnapshotDto[];
}

function formatQty(value: number): string {
  if (!Number.isFinite(value)) return '—';
  if (Number.isInteger(value)) return String(value);
  return value.toFixed(2);
}

export function SrpCapacityTable({ rows }: SrpCapacityTableProps) {
  if (rows.length === 0) {
    return <p className="empty">暂无资源产能数据</p>;
  }

  return (
    <div className="srp-capacity-table">
      <div className="srp-capacity-table-head">
        <h4 className="srp-capacity-table-title">资源产能明细</h4>
        <span className="srp-capacity-table-count">{rows.length} 行</span>
      </div>
      <div className="table-wrap srp-capacity-table-wrap">
        <table className="data-table srp-capacity-table-grid">
          <thead>
            <tr>
              <th>资源</th>
              <th>周期</th>
              <th className="num">总产能</th>
              <th className="num">日历停机</th>
              <th className="num">已占用</th>
              <th className="num">可用</th>
              <th className="num">空闲</th>
              <th className="num">超载</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const overload = row.overloadCapacity > 0;
              return (
                <tr key={row.id} className={overload ? 'is-overload' : undefined}>
                  <td className="resource-cell">{row.resourceId}</td>
                  <td className="period-cell">{row.periodId}</td>
                  <td className="num">{formatQty(row.totalCapacity)}</td>
                  <td className="num">{formatQty(row.calendarDowntime)}</td>
                  <td className="num">{formatQty(row.reservedCapacity)}</td>
                  <td className="num">{formatQty(row.availableCapacity)}</td>
                  <td className="num">{formatQty(row.freeCapacity)}</td>
                  <td className={`num ${overload ? 'overload-qty' : ''}`}>
                    {formatQty(row.overloadCapacity)}
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
