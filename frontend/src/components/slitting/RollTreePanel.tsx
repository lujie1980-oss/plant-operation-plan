import type { SlittingRollNode } from '../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle, slittingNodeTypeLabel } from '../../utils/slitting/display';

type Props = {
  nodes: SlittingRollNode[];
  activeParentNodeId: string | null;
  hoveredNodeId: string | null;
  onSelect: (nodeId: string | null) => void;
  onHover: (nodeId: string | null) => void;
};

function childrenOf(nodes: SlittingRollNode[], parentId: string | null): SlittingRollNode[] {
  return nodes.filter((n) => n.parentNodeId === parentId);
}

function TreeNode({
  node,
  nodes,
  activeParentNodeId,
  hoveredNodeId,
  onSelect,
  onHover,
  depth,
}: {
  node: SlittingRollNode;
  nodes: SlittingRollNode[];
  activeParentNodeId: string | null;
  hoveredNodeId: string | null;
  onSelect: (nodeId: string | null) => void;
  onHover: (nodeId: string | null) => void;
  depth: number;
}) {
  const kids = childrenOf(nodes, node.nodeId);
  const active = activeParentNodeId === node.nodeId;
  const hovered = hoveredNodeId === node.nodeId;
  const className = ['slitting-tree-btn', active && 'active', hovered && 'is-hovered'].filter(Boolean).join(' ');

  return (
    <div className="slitting-tree-node" style={{ paddingLeft: depth * 10 }}>
      <button
        type="button"
        className={className}
        onClick={() => onSelect(node.nodeType === 'INTERMEDIATE' ? node.nodeId : null)}
        onMouseEnter={() => onHover(node.nodeId)}
        onMouseLeave={() => onHover(null)}
      >
        <span className="slitting-tree-btn-label">
          {slittingNodeTypeLabel(node.nodeType)} · {slittingNodeLabel(node)}
        </span>
        <span className="slitting-tree-btn-meta">{slittingNodeSubtitle(node)}</span>
      </button>
      {kids.map((k) => (
        <TreeNode
          key={k.nodeId}
          node={k}
          nodes={nodes}
          activeParentNodeId={activeParentNodeId}
          hoveredNodeId={hoveredNodeId}
          onSelect={onSelect}
          onHover={onHover}
          depth={depth + 1}
        />
      ))}
    </div>
  );
}

export function RollTreePanel({ nodes, activeParentNodeId, hoveredNodeId, onSelect, onHover }: Props) {
  const roots = childrenOf(nodes, null);
  return (
    <div className="slitting-panel slitting-tree-panel">
      <h3 className="slitting-panel-title">分切树</h3>
      <button
        type="button"
        className={`slitting-tree-btn slitting-tree-master-link ${activeParentNodeId === null ? 'active' : ''}`}
        onClick={() => onSelect(null)}
      >
        <span className="slitting-tree-btn-label">MASTER 层</span>
        <span className="slitting-tree-btn-meta">查看母卷排样</span>
      </button>
      {roots.map((r) => (
        <TreeNode
          key={r.nodeId}
          node={r}
          nodes={nodes}
          activeParentNodeId={activeParentNodeId}
          hoveredNodeId={hoveredNodeId}
          onSelect={onSelect}
          onHover={onHover}
          depth={0}
        />
      ))}
    </div>
  );
}
