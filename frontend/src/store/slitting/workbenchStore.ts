import { create } from 'zustand';
import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import { computeUtilizationPct } from '../../utils/slitting/kpi';

interface WorkbenchState {
  planVersionId: string | null;
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  activeParentNodeId: string | null;
  utilizationPct: number;
  setTree: (planVersionId: string, nodes: SlittingRollNode[], assignments: SlittingAssignment[]) => void;
  setActiveParent: (nodeId: string | null) => void;
  updateAssignmentPosition: (assignmentId: string, x: number, y: number) => void;
  toggleRotation: (assignmentId: string) => void;
  recalcKpi: () => void;
}

export const useSlittingWorkbenchStore = create<WorkbenchState>((set, get) => ({
  planVersionId: null,
  nodes: [],
  assignments: [],
  activeParentNodeId: null,
  utilizationPct: 0,
  setTree: (planVersionId, nodes, assignments) => {
    set({ planVersionId, nodes, assignments, activeParentNodeId: null, utilizationPct: 0 });
    get().recalcKpi();
  },
  setActiveParent: (nodeId) => set({ activeParentNodeId: nodeId }),
  updateAssignmentPosition: (assignmentId, x, y) => {
    const next = get().assignments.map((a) =>
      a.assignmentId === assignmentId ? { ...a, posXMm: x, posYMm: y } : a,
    );
    set({ assignments: next });
    get().recalcKpi();
  },
  toggleRotation: (assignmentId) => {
    const next = get().assignments.map((a) =>
      a.assignmentId === assignmentId ? { ...a, rotated: !a.rotated } : a,
    );
    set({ assignments: next });
    get().recalcKpi();
  },
  recalcKpi: () => {
    const { nodes, assignments } = get();
    const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));
    const masters = nodes.filter((n) => n.nodeType === 'MASTER');
    set({ utilizationPct: computeUtilizationPct(masters, assignments, nodeById) });
  },
}));
