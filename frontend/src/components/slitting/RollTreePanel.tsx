import type { SlittingRollNode } from '../../types/slitting';

type Props = {
  nodes: SlittingRollNode[];
  activeParentNodeId: string | null;
  onSelect: (nodeId: string | null) => void;
};

function childrenOf(nodes: SlittingRollNode[], parentId: string | null): SlittingRollNode[] {
  return nodes.filter((n) => n.parentNodeId === parentId);
}

function TreeNode({
  node,
  nodes,
  activeParentNodeId,
  onSelect,
  depth,
}: {
  node: SlittingRollNode;
  nodes: SlittingRollNode[];
  activeParentNodeId: string | null;
  onSelect: (nodeId: string | null) => void;
  depth: number;
}) {
  const kids = childrenOf(nodes, node.nodeId);
  const active = activeParentNodeId === node.nodeId;
  return (
    <div className="slitting-tree-node" style={{ paddingLeft: depth * 12 }}>
      <button
        type="button"
        className={active ? 'slitting-tree-btn active' : 'slitting-tree-btn'}
        onClick={() => onSelect(node.nodeType === 'INTERMEDIATE' ? node.nodeId : null)}
      >
        {node.nodeType} · {node.nodeId}
      </button>
      {kids.map((k) => (
        <TreeNode
          key={k.nodeId}
          node={k}
          nodes={nodes}
          activeParentNodeId={activeParentNodeId}
          onSelect={onSelect}
          depth={depth + 1}
        />
      ))}
    </div>
  );
}

export function RollTreePanel({ nodes, activeParentNodeId, onSelect }: Props) {
  const roots = childrenOf(nodes, null);
  return (
    <div className="slitting-tree-panel">
      <h3>分切树</h3>
      <button type="button" className="slitting-tree-btn" onClick={() => onSelect(null)}>
        MASTER 层
      </button>
      {roots.map((r) => (
        <TreeNode
          key={r.nodeId}
          node={r}
          nodes={nodes}
          activeParentNodeId={activeParentNodeId}
          onSelect={onSelect}
          depth={1}
        />
      ))}
    </div>
  );
}
