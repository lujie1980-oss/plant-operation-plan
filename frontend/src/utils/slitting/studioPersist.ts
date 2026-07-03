import type { SlittingAssignment, SlittingPlanTree, SlittingRollNode } from '../../types/slitting';
import { apiToStudioPosition, studioToApiPosition } from './studioGeometry';

export function treeToStudio(tree: SlittingPlanTree): {
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
} {
  const assignments = tree.assignments.map((a) => {
    const { posXMm, posYMm } = apiToStudioPosition(a.posXMm, a.posYMm);
    return { ...a, posXMm, posYMm };
  });
  return { nodes: tree.nodes, assignments };
}

export function studioToTreePayload(
  nodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
): { nodes: SlittingRollNode[]; assignments: SlittingAssignment[] } {
  const apiAssignments = assignments.map((a) => {
    const { posXMm, posYMm } = studioToApiPosition(a.posXMm, a.posYMm);
    return { ...a, posXMm, posYMm };
  });
  return { nodes, assignments: apiAssignments };
}
