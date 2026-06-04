import { useCallback, useEffect, useMemo, useState } from 'react';
import { slittingClient } from '../../api/slittingClient';
import { PageHeader } from '../../components/PageHeader';
import { SlittingCanvas } from '../../components/slitting/SlittingCanvas';
import { RollTreePanel } from '../../components/slitting/RollTreePanel';
import { StatusBanner } from '../../components/StatusBanner';
import { useSlittingWorkbenchStore } from '../../store/slitting/workbenchStore';
import type { SlittingPlanSummary, SlittingRollNode } from '../../types/slitting';
import '../../components/slitting/slitting.css';

export function SlittingWorkbenchPage() {
  const [plans, setPlans] = useState<SlittingPlanSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const {
    planVersionId,
    nodes,
    assignments,
    activeParentNodeId,
    utilizationPct,
    setTree,
    setActiveParent,
    updateAssignmentPosition,
    toggleRotation,
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

  const loadTree = async (id: string) => {
    setLoading(true);
    setErr(null);
    try {
      const tree = await slittingClient.getTree(id);
      setTree(tree.planVersionId, tree.nodes, tree.assignments);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'r' && assignments[0]) {
        toggleRotation(assignments[0].assignmentId);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [assignments, toggleRotation]);

  return (
    <div className="page slitting-workbench-page">
      <PageHeader title="分切排样工作台" description="画板拖拽、树形钻取、求解与保存" />
      <StatusBanner error={err} success={success} />
      <div className="slitting-toolbar">
        <label>
          方案
          <select
            value={planVersionId ?? ''}
            onChange={(e) => {
              const id = e.target.value;
              if (id) void loadTree(id);
            }}
          >
            <option value="">— 选择 —</option>
            {plans.map((p) => (
              <option key={p.planVersionId} value={p.planVersionId}>
                {p.name} ({p.status})
              </option>
            ))}
          </select>
        </label>
        <button type="button" disabled={!planVersionId || loading} onClick={() => void handleSolve()}>
          求解
        </button>
        <button type="button" disabled={!planVersionId || loading} onClick={() => void handleSave()}>
          保存
        </button>
        <span className="slitting-kpi">利用率 {utilizationPct.toFixed(1)}%</span>
      </div>
      <div className="slitting-workbench-grid">
        <RollTreePanel nodes={nodes} activeParentNodeId={activeParentNodeId} onSelect={setActiveParent} />
        <SlittingCanvas
          parentNode={canvasParent}
          childNodes={nodeById}
          assignments={assignments}
          onMove={updateAssignmentPosition}
        />
      </div>
    </div>
  );
}
