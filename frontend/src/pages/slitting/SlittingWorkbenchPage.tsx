import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useSearchParams } from 'react-router-dom';

import { slittingClient } from '../../api/slittingClient';

import { SlittingCanvas } from '../../components/slitting/SlittingCanvas';

import { SlittingPropertyPanel } from '../../components/slitting/SlittingPropertyPanel';

import { SlittingUnplacedPool } from '../../components/slitting/SlittingUnplacedPool';

import { SlittingWorkbenchToolbar } from '../../components/slitting/SlittingWorkbenchToolbar';

import { RollTreePanel } from '../../components/slitting/RollTreePanel';

import { StatusBanner } from '../../components/StatusBanner';

import { useSlittingWorkbenchStore } from '../../store/slitting/workbenchStore';

import { unplacedNodesForParent } from '../../utils/slitting/unplaced';

import type { SlittingPlanSummary, SlittingRollNode } from '../../types/slitting';

import '../../components/slitting/slitting.css';



export function SlittingWorkbenchPage() {

  const [searchParams] = useSearchParams();

  const [plans, setPlans] = useState<SlittingPlanSummary[]>([]);

  const [loading, setLoading] = useState(false);

  const [err, setErr] = useState<string | null>(null);

  const [success, setSuccess] = useState<string | null>(null);

  const [sessionId, setSessionId] = useState<string | null>(null);



  const {

    planVersionId,

    nodes,

    assignments,

    activeParentNodeId,

    utilizationPct,

    selectedAssignmentId,

    hoveredNodeId,

    setTree,

    setActiveParent,

    setSelectedAssignment,

    setHoveredNode,

    applyLayerAssignments,

    updateAssignmentPosition,

    toggleRotation,

    togglePinned,

  } = useSlittingWorkbenchStore();



  const loadPlans = useCallback(async () => {

    try {

      setPlans(await slittingClient.listPlans());

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    }

  }, []);



  useEffect(() => {

    void loadPlans();

  }, [loadPlans]);



  const nodeById = useMemo(() => new Map(nodes.map((n) => [n.nodeId, n])), [nodes]);



  const canvasParent: SlittingRollNode | null = useMemo(() => {

    if (activeParentNodeId) {

      return nodeById.get(activeParentNodeId) ?? null;

    }

    const masters = nodes.filter((n) => n.nodeType === 'MASTER');

    return masters[0] ?? null;

  }, [activeParentNodeId, nodeById, nodes]);



  const loadTree = useCallback(

    async (id: string) => {

      setLoading(true);

      setErr(null);

      try {

        const tree = await slittingClient.getTree(id);

        setTree(tree.planVersionId, tree.nodes, tree.assignments);

        setSessionId(null);

      } catch (e: unknown) {

        setErr(e instanceof Error ? e.message : String(e));

      } finally {

        setLoading(false);

      }

    },

    [setTree],

  );



  useEffect(() => {

    const planFromUrl = searchParams.get('plan');

    if (planFromUrl && planFromUrl !== planVersionId) {

      void loadTree(planFromUrl);

    }

  }, [searchParams, planVersionId, loadTree]);



  const selectedAssignment = useMemo(

    () => assignments.find((a) => a.assignmentId === selectedAssignmentId) ?? null,

    [assignments, selectedAssignmentId],

  );



  const selectedChildNode = useMemo(

    () => (selectedAssignment ? nodeById.get(selectedAssignment.childNodeId) ?? null : null),

    [selectedAssignment, nodeById],

  );



  const unplaced = useMemo(() => {

    if (!canvasParent) return [];

    return unplacedNodesForParent(nodes, assignments, canvasParent.nodeId);

  }, [canvasParent, nodes, assignments]);



  const handleSolve = async () => {

    if (!planVersionId) return;

    setLoading(true);

    setErr(null);

    try {

      await slittingClient.solvePlan(planVersionId);

      await loadTree(planVersionId);

      setSuccess('求解完成');

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };

  const solveTriggeredRef = useRef<string | null>(null);

  useEffect(() => {
    const planFromUrl = searchParams.get('plan');
    const wantSolve = searchParams.get('solve') === '1';
    if (!wantSolve || !planFromUrl || planVersionId !== planFromUrl || loading) return;
    if (solveTriggeredRef.current === planFromUrl) return;
    solveTriggeredRef.current = planFromUrl;
    void handleSolve();
  }, [searchParams, planVersionId, loading]);

  const handleCreateSession = async () => {

    if (!planVersionId) return;

    setLoading(true);

    setErr(null);

    try {

      const session = await slittingClient.createSession(planVersionId, activeParentNodeId);

      setSessionId(session.sessionId);

      applyLayerAssignments(session.assignments);

      setSuccess('会话已创建，可锁定块后局部重算');

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };



  const handleLocalOptimize = async () => {

    if (!sessionId) return;

    setLoading(true);

    setErr(null);

    try {

      const session = await slittingClient.sessionLocalOptimize(sessionId);

      applyLayerAssignments(session.assignments);

      setSuccess(`局部重算完成 ${session.score ?? ''}`);

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };



  const handleAutoNest = async () => {

    if (!sessionId) return;

    setLoading(true);

    setErr(null);

    try {

      const session = await slittingClient.sessionAutoNest(sessionId);

      applyLayerAssignments(session.assignments);

      setSuccess('Auto-Nest 完成');

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };



  const handleConfirmSession = async () => {

    if (!sessionId || !planVersionId) return;

    setLoading(true);

    setErr(null);

    try {

      const tree = await slittingClient.sessionConfirm(sessionId);

      setTree(tree.planVersionId, tree.nodes, tree.assignments);

      setSessionId(null);

      setSuccess('会话已写回方案');

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };



  const handleSave = async () => {

    if (!planVersionId) return;

    setLoading(true);

    setErr(null);

    try {

      const tree = await slittingClient.saveAssignments(planVersionId, assignments);

      setTree(tree.planVersionId, tree.nodes, tree.assignments);

      setSuccess('已保存');

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    } finally {

      setLoading(false);

    }

  };



  const handleTogglePin = async () => {

    if (!selectedAssignmentId) return;

    togglePinned(selectedAssignmentId);

    if (!sessionId) return;

    const next = useSlittingWorkbenchStore.getState().assignments.find((a) => a.assignmentId === selectedAssignmentId);

    if (!next) return;

    try {

      await slittingClient.patchSession(sessionId, [

        { assignmentId: selectedAssignmentId, pinned: Boolean(next.pinned) },

      ]);

    } catch (e: unknown) {

      setErr(e instanceof Error ? e.message : String(e));

    }

  };



  const handleRotate = () => {

    if (!selectedAssignmentId) return;

    toggleRotation(selectedAssignmentId);

  };



  useEffect(() => {

    const onKey = (e: KeyboardEvent) => {

      if (e.key.toLowerCase() !== 'r' || e.target instanceof HTMLInputElement) return;

      const id = useSlittingWorkbenchStore.getState().selectedAssignmentId;

      if (id) toggleRotation(id);

    };

    window.addEventListener('keydown', onKey);

    return () => window.removeEventListener('keydown', onKey);

  }, [toggleRotation]);



  return (

    <div className="page slitting-module slitting-workbench-page">

      <StatusBanner error={err} success={success} />

      <SlittingWorkbenchToolbar
        title="分切求解工作台"

        plans={plans}

        planVersionId={planVersionId}

        sessionId={sessionId}

        utilizationPct={utilizationPct}

        loading={loading}

        onPlanChange={(id) => void loadTree(id)}

        onSolve={() => void handleSolve()}

        onSave={() => void handleSave()}

        onCreateSession={() => void handleCreateSession()}

        onLocalOptimize={() => void handleLocalOptimize()}

        onAutoNest={() => void handleAutoNest()}

        onConfirmSession={() => void handleConfirmSession()}

      />

      <div className="slitting-workbench-grid">

        <RollTreePanel

          nodes={nodes}

          activeParentNodeId={activeParentNodeId}

          hoveredNodeId={hoveredNodeId}

          onSelect={setActiveParent}

          onHover={setHoveredNode}

        />

        <SlittingCanvas

          parentNode={canvasParent}

          childNodes={nodeById}

          assignments={assignments}

          selectedAssignmentId={selectedAssignmentId}

          hoveredNodeId={hoveredNodeId}

          sessionActive={Boolean(sessionId)}

          onSelect={setSelectedAssignment}

          onHoverNode={setHoveredNode}

          onMove={updateAssignmentPosition}

        />

        <div className="slitting-workbench-aside">

          <SlittingPropertyPanel

            assignment={selectedAssignment}

            childNode={selectedChildNode}

            sessionActive={Boolean(sessionId)}

            onRotate={handleRotate}

            onTogglePin={() => void handleTogglePin()}

          />

          <SlittingUnplacedPool nodes={unplaced} hoveredNodeId={hoveredNodeId} onHover={setHoveredNode} />

        </div>

      </div>

    </div>

  );

}

