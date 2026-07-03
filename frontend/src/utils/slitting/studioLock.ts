import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';

export function assignmentForNode(
  nodeId: string,
  assignments: SlittingAssignment[],
): SlittingAssignment | undefined {
  return assignments.find((a) => a.childNodeId === nodeId);
}

export function collectDescendantNodeIds(nodes: SlittingRollNode[], rootId: string): Set<string> {
  const ids = new Set<string>([rootId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const n of nodes) {
      if (n.parentNodeId && ids.has(n.parentNodeId) && !ids.has(n.nodeId)) {
        ids.add(n.nodeId);
        changed = true;
      }
    }
  }
  return ids;
}

export function isNodeLocked(
  nodeId: string,
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
): boolean {
  const node = nodes.find((n) => n.nodeId === nodeId);
  if (!node) return false;
  if (node.nodeType === 'MASTER') {
    const subtree = collectDescendantNodeIds(nodes, nodeId);
    const scoped = assignments.filter((a) => subtree.has(a.childNodeId));
    return scoped.length > 0 && scoped.every((a) => a.pinned);
  }
  return Boolean(assignmentForNode(nodeId, assignments)?.pinned);
}

export function orderCodeFromChildNodeId(nodeId: string): string | null {
  const m = nodeId.match(/^CHILD-(.+)-(\d+)$/);
  return m ? m[1] : null;
}
