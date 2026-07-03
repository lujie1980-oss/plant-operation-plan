import { useCallback, useMemo, useState } from 'react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import {
  buildSupplyOrderChainTree,
  flattenSupplyOrderTreeRows,
  formatSupplyOrderTreeDate,
  fulfillmentTreeNodeTypeLabel,
} from '../utils/fulfillmentChainTree';
import './FulfillmentChainTreePanel.css';

export const FULFILLMENT_TREE_ROW_HEIGHT = 32;

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

interface FulfillmentChainTreePanelProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string) => void;
  loading?: boolean;
}

export function FulfillmentChainTreePanel({
  nodes,
  edges,
  selectedNodeId,
  onSelectNode,
  loading = false,
}: FulfillmentChainTreePanelProps) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const tree = useMemo(() => buildSupplyOrderChainTree(nodes, edges), [nodes, edges]);

  const treeRows = useMemo(
    () => flattenSupplyOrderTreeRows(tree, nodes, collapsed),
    [tree, nodes, collapsed],
  );

  const toggleCollapse = useCallback((nodeId: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  }, []);

  if (treeRows.length === 0) {
    if (loading) {
      return <p className="fulfillment-tree-empty">加载满足链…</p>;
    }
    return <p className="fulfillment-tree-empty">暂无满足链，请先创建上游满足链</p>;
  }

  return (
    <div className="fulfillment-chain-tree-panel">
      <div className="fulfillment-tree-toolbar">
        <span className="fulfillment-tree-toolbar-label">SupplyOrder 满足层级</span>
      </div>
      <div className="fulfillment-tree-scroll panel-scroll">
        <table className="fulfillment-tree-table">
          <thead>
            <tr>
              <th className="col-toggle" />
              <th className="col-type">类型</th>
              <th className="col-label">供应订单</th>
              <th className="col-product">产品</th>
              <th className="col-qty">数量</th>
              <th className="col-start">开始</th>
              <th className="col-end">结束</th>
            </tr>
          </thead>
          <tbody>
            {treeRows.map((row) => {
              const typeClass = row.nodeType.toLowerCase();
              const selected = selectedNodeId === row.nodeId;
              const isWo = row.nodeType === 'SUPPLY_ORDER';
              return (
                <tr
                  key={row.nodeId}
                  className={`fulfillment-tree-row type-${typeClass} ${selected ? 'is-selected' : ''} ${isWo ? 'is-work-order' : ''}`}
                  style={{ height: FULFILLMENT_TREE_ROW_HEIGHT }}
                  onClick={onSelectNode ? () => onSelectNode(row.nodeId) : undefined}
                >
                  <td className="col-toggle">
                    {row.hasChildren ? (
                      <button
                        type="button"
                        className="fulfillment-tree-toggle"
                        aria-expanded={!collapsed.has(row.nodeId)}
                        aria-label={collapsed.has(row.nodeId) ? '展开' : '收拢'}
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleCollapse(row.nodeId);
                        }}
                      >
                        {collapsed.has(row.nodeId) ? '▸' : '▾'}
                      </button>
                    ) : (
                      <span className="fulfillment-tree-toggle-spacer" />
                    )}
                  </td>
                  <td className="col-type">
                    <span className={`demand-tree-type ${typeClass}`}>
                      {fulfillmentTreeNodeTypeLabel(row.nodeType)}
                    </span>
                  </td>
                  <td className="col-label">
                    <span
                      className="fulfillment-tree-indent"
                      style={{ paddingLeft: `${row.depth}rem` }}
                    />
                    <span className="fulfillment-tree-label" title={row.label}>
                      {row.label}
                    </span>
                  </td>
                  <td className="col-product mono">{row.productCode || '—'}</td>
                  <td className="col-qty">{row.quantity > 0 ? fmtQty(row.quantity) : '—'}</td>
                  <td className="col-start mono">
                    {row.startTs ? formatSupplyOrderTreeDate(row.startTs) : '—'}
                  </td>
                  <td className="col-end mono">
                    {row.endTs ? formatSupplyOrderTreeDate(row.endTs) : '—'}
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
