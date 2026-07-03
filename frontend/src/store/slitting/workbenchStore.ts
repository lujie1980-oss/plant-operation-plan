import { create } from 'zustand';

import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';

import { computeUtilizationPct } from '../../utils/slitting/kpi';



interface WorkbenchState {

  planVersionId: string | null;

  nodes: SlittingRollNode[];

  assignments: SlittingAssignment[];

  activeParentNodeId: string | null;

  utilizationPct: number;

  selectedAssignmentId: string | null;

  hoveredNodeId: string | null;

  setTree: (planVersionId: string, nodes: SlittingRollNode[], assignments: SlittingAssignment[]) => void;

  setActiveParent: (nodeId: string | null) => void;

  setSelectedAssignment: (assignmentId: string | null) => void;

  setHoveredNode: (nodeId: string | null) => void;

  applyLayerAssignments: (layerAssignments: SlittingAssignment[]) => void;

  updateAssignmentPosition: (assignmentId: string, x: number, y: number) => void;

  toggleRotation: (assignmentId: string) => void;

  togglePinned: (assignmentId: string) => void;

  recalcKpi: () => void;

}



function belongsToLayer(

  assignment: SlittingAssignment,

  nodes: SlittingRollNode[],

  activeParentNodeId: string | null,

): boolean {

  const parent = nodes.find((n) => n.nodeId === assignment.parentNodeId);

  if (!activeParentNodeId) {

    return parent?.nodeType === 'MASTER';

  }

  return assignment.parentNodeId === activeParentNodeId;

}



export const useSlittingWorkbenchStore = create<WorkbenchState>((set, get) => ({

  planVersionId: null,

  nodes: [],

  assignments: [],

  activeParentNodeId: null,

  utilizationPct: 0,

  selectedAssignmentId: null,

  hoveredNodeId: null,

  setTree: (planVersionId, nodes, assignments) => {

    set({

      planVersionId,

      nodes,

      assignments,

      activeParentNodeId: null,

      utilizationPct: 0,

      selectedAssignmentId: null,

      hoveredNodeId: null,

    });

    get().recalcKpi();

  },

  setActiveParent: (nodeId) =>

    set({ activeParentNodeId: nodeId, selectedAssignmentId: null, hoveredNodeId: null }),

  setSelectedAssignment: (assignmentId) => set({ selectedAssignmentId: assignmentId }),

  setHoveredNode: (nodeId) => set({ hoveredNodeId: nodeId }),

  applyLayerAssignments: (layerAssignments) => {

    const { nodes, assignments, activeParentNodeId, selectedAssignmentId } = get();

    const kept = assignments.filter((a) => !belongsToLayer(a, nodes, activeParentNodeId));

    const next = [...kept, ...layerAssignments];

    const stillSelected = selectedAssignmentId && next.some((a) => a.assignmentId === selectedAssignmentId);

    set({

      assignments: next,

      selectedAssignmentId: stillSelected ? selectedAssignmentId : null,

    });

    get().recalcKpi();

  },

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

  togglePinned: (assignmentId) => {

    const next = get().assignments.map((a) =>

      a.assignmentId === assignmentId ? { ...a, pinned: !a.pinned } : a,

    );

    set({ assignments: next });

  },

  recalcKpi: () => {

    const { nodes, assignments } = get();

    const nodeById = new Map(nodes.map((n) => [n.nodeId, n]));

    const masters = nodes.filter((n) => n.nodeType === 'MASTER');

    set({ utilizationPct: computeUtilizationPct(masters, assignments, nodeById) });

  },

}));


