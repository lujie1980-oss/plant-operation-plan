import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import {
  collectMaterialNodesForDemander,
  materialPegLabel,
  resolveDemanderNodeId,
} from '../utils/fulfillmentMaterial';
import { fulfillmentTreeNodeTypeLabel } from '../utils/fulfillmentChainTree';
import { FULFILLMENT_STATUS_LABEL } from '../utils/fulfillmentGantt';
import './FulfillmentMaterialPanel.css';

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function statusClass(status: string): string {
  if (status === 'SHORTAGE') return 'badge danger';
  if (status === 'OK') return 'badge ok';
  return 'badge info';
}

interface FulfillmentMaterialPanelProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  selectedTaskId: string | null;
}

export function FulfillmentMaterialPanel({
  nodes,
  edges,
  selectedTaskId,
}: FulfillmentMaterialPanelProps) {
  const demanderId = resolveDemanderNodeId(selectedTaskId ?? '', nodes);
  const demander = demanderId ? nodes.find((n) => n.nodeId === demanderId) : null;
  const materials = collectMaterialNodesForDemander(demanderId, nodes, edges);

  return (
    <div className="fulfillment-material-panel">
      <div className="fulfillment-material-head">
        <h4 className="panel-title">物料需求</h4>
        {demander ? (
          <p className="fulfillment-material-meta">
            {fulfillmentTreeNodeTypeLabel(demander.nodeType)} · {demander.label}
          </p>
        ) : (
          <p className="fulfillment-material-meta muted">请在左侧选择销售订单或工单</p>
        )}
      </div>
      <div className="fulfillment-material-scroll panel-scroll">
        {demander && materials.length === 0 && (
          <p className="empty-hint">
            {demander.nodeType === 'SALES_ORDER'
              ? '销售订单通过子工单满足，无直接原料需求'
              : '该工单无直接原料需求（原料在子工单层）'}
          </p>
        )}
        {materials.length > 0 && (
          <table className="fulfillment-material-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>物料</th>
                <th>数量</th>
                <th>状态</th>
                <th>满足方式</th>
              </tr>
            </thead>
            <tbody>
              {materials.map((row) => {
                const typeClass = row.nodeType.toLowerCase();
                return (
                  <tr key={row.nodeId} className={`material-row type-${typeClass}`}>
                    <td>
                      <span className={`demand-tree-type ${typeClass}`}>
                        {fulfillmentTreeNodeTypeLabel(row.nodeType)}
                      </span>
                    </td>
                    <td className="mono">{row.productCode || row.label}</td>
                    <td>{row.quantity > 0 ? fmtQty(row.quantity) : '—'}</td>
                    <td>
                      <span className={statusClass(row.status)}>
                        {FULFILLMENT_STATUS_LABEL[row.status] ?? row.status}
                      </span>
                    </td>
                    <td>{materialPegLabel(row, edges)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        <div className="fulfillment-material-legend">
          <span className="legend-item">
            <i className="dot ok" /> 库存满足
          </span>
          <span className="legend-item">
            <i className="dot risk" /> 缺料
          </span>
        </div>
      </div>
    </div>
  );
}
