import { useMemo } from 'react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import {
  buildFulfillmentChainTree,
  fulfillmentTreeNodeTypeLabel,
  type FulfillmentTreeNode,
} from '../utils/fulfillmentChainTree';
import './FulfillmentChainTree.css';

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

interface FulfillmentChainTreeProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  title?: string;
  meta?: string;
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string) => void;
  /** 若传入则直接使用，否则从 nodes/edges 构建销售订单根树 */
  roots?: FulfillmentTreeNode[];
}

export function FulfillmentChainTree({
  nodes,
  edges,
  title,
  meta,
  selectedNodeId,
  onSelectNode,
  roots: rootsOverride,
}: FulfillmentChainTreeProps) {
  const roots = useMemo(
    () => rootsOverride ?? buildFulfillmentChainTree(nodes, edges),
    [nodes, edges, rootsOverride],
  );

  if (roots.length === 0) {
    return (
      <div className="fulfillment-tree-panel">
        <p className="empty-hint">暂无满足链节点</p>
      </div>
    );
  }

  return (
    <div className="fulfillment-tree-panel">
      {(title || meta) && (
        <div className="fulfillment-tree-head">
          {title && <h4 className="fulfillment-tree-title">{title}</h4>}
          {meta && <span className="fulfillment-tree-meta">{meta}</span>}
        </div>
      )}
      <div className="fulfillment-tree-scroll">
        <ul className="demand-tree-forest">
          {roots.map((root) => (
            <FulfillmentTreeBranch
              key={root.nodeId}
              node={root}
              depth={0}
              selectedNodeId={selectedNodeId}
              onSelectNode={onSelectNode}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}

function FulfillmentTreeBranch({
  node,
  depth,
  selectedNodeId,
  onSelectNode,
}: {
  node: FulfillmentTreeNode;
  depth: number;
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string) => void;
}) {
  const hasChildren = node.children.length > 0;
  const selected = selectedNodeId === node.nodeId;
  const typeClass = node.nodeType.toLowerCase();

  return (
    <li className={`demand-tree-item depth-${depth} type-${typeClass}`}>
      <div
        className={`demand-tree-row ${selected ? 'is-selected' : ''}`}
        role={onSelectNode ? 'button' : undefined}
        tabIndex={onSelectNode ? 0 : undefined}
        onClick={onSelectNode ? () => onSelectNode(node.nodeId) : undefined}
        onKeyDown={
          onSelectNode
            ? (e) => {
                if (e.key === 'Enter') onSelectNode(node.nodeId);
              }
            : undefined
        }
      >
        <span className={`demand-tree-type ${typeClass}`}>
          {fulfillmentTreeNodeTypeLabel(node.nodeType)}
        </span>
        <span className="demand-tree-label">{node.label}</span>
        <span className="demand-tree-meta">
          <span className="demand-tree-date">{node.displayDate}</span>
          {node.quantity > 0 && <span className="demand-tree-qty">×{fmtQty(node.quantity)}</span>}
        </span>
      </div>
      {hasChildren && (
        <ul className="demand-tree-children">
          {node.children.map((child) => (
            <FulfillmentTreeBranch
              key={child.nodeId}
              node={child}
              depth={depth + 1}
              selectedNodeId={selectedNodeId}
              onSelectNode={onSelectNode}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
