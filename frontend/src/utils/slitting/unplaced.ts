import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';

/** 当前父卷下尚未排入画板的直接子节点 */
export function unplacedNodesForParent(
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
  parentNodeId: string,
): SlittingRollNode[] {
  const children = nodes.filter((n) => n.parentNodeId === parentNodeId);
  const placed = new Set(
    assignments.filter((a) => a.parentNodeId === parentNodeId).map((a) => a.childNodeId),
  );
  return children.filter((n) => !placed.has(n.nodeId));
}
