import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import { effectiveSize } from './satCollision';

export function computeUtilizationPct(
  masterNodes: SlittingRollNode[],
  assignments: SlittingAssignment[],
  nodeById: Map<string, SlittingRollNode>,
): number {
  const masterArea = masterNodes.reduce((s, n) => s + n.widthMm * n.lengthMm, 0);
  if (masterArea <= 0) return 0;
  const placedArea = assignments.reduce((s, a) => {
    const n = nodeById.get(a.childNodeId);
    if (!n || n.nodeType !== 'CHILD') return s;
    const { w, h } = effectiveSize(n.widthMm, n.lengthMm, a.rotated);
    return s + w * h;
  }, 0);
  return (placedArea / masterArea) * 100;
}
