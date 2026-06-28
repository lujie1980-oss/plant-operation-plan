import type { SlittingRollNode } from '../../types/slitting';

const NODE_TYPE_LABEL: Record<string, string> = {
  MASTER: '母卷',
  INTERMEDIATE: '中间卷',
  CHILD: '子卷',
};

export function slittingNodeTypeLabel(nodeType: string): string {
  return NODE_TYPE_LABEL[nodeType] ?? nodeType;
}

/** 树与画板共用的可读标题 */
export function slittingNodeLabel(node: SlittingRollNode): string {
  if (node.nodeType === 'INTERMEDIATE' && node.sourceSpecCode) {
    return node.sourceSpecCode;
  }
  if (node.nodeType === 'CHILD') {
    const m = node.nodeId.match(/^CHILD-(.+)-(\d+)$/);
    if (m) return `${m[1]} ·#${m[2]}`;
  }
  if (node.nodeType === 'MASTER') {
    return node.nodeId.replace(/^MASTER-/, '') || node.nodeId;
  }
  return node.nodeId;
}

export function slittingNodeSubtitle(node: SlittingRollNode): string {
  return `${node.widthMm.toLocaleString()} × ${node.lengthMm.toLocaleString()} mm`;
}

export function slittingPlanStatusClass(status: string): string {
  const s = status.toUpperCase();
  if (s === 'SOLVED' || s === 'CONFIRMED') return 'slitting-status slitting-status--ok';
  if (s === 'SOLVING' || s === 'RUNNING') return 'slitting-status slitting-status--run';
  if (s === 'FAILED' || s === 'ERROR') return 'slitting-status slitting-status--err';
  return 'slitting-status slitting-status--draft';
}
