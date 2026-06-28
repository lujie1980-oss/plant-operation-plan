import { getStoredWorkspaceId } from '../context/WorkspaceContext';
import type { BomMd } from '../types/masterData';
import type {
  ChildSlittingOrder,
  CreateSlittingPlanRequest,
  ImportChildOrdersFromDemandRequest,
  ImportChildOrdersFromDemandResult,
  IntermediateRollCatalog,
  MasterRoll,
  SlittingBomScope,
  SlittingMaterialDemand,
  SlittingAssignment,
  SlittingAssignmentPatch,
  SlittingPlanSummary,
  SlittingPlanTree,
  SlittingSession,
  SlittingSolverRun,
} from '../types/slitting';

const BASE = '/api/v1/slitting';

function headers(): HeadersInit {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Workspace-Id': getStoredWorkspaceId(),
  };
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text();
    if (text) {
      try {
        const body = JSON.parse(text) as { message?: string; error?: string; title?: string; detail?: string };
        const msg = body.message ?? body.error ?? body.detail ?? body.title;
        if (msg) {
          throw new Error(msg);
        }
      } catch (e) {
        if (e instanceof Error && e.message !== text && !e.message.startsWith('Unexpected')) {
          throw e;
        }
      }
      throw new Error(text);
    }
    throw new Error(res.statusText);
  }
  return res.json() as Promise<T>;
}

export const slittingClient = {
  listMasterRolls: () => fetch(`${BASE}/master-rolls`, { headers: headers() }).then((r) => json<MasterRoll[]>(r)),
  createMasterRoll: (body: MasterRoll) =>
    fetch(`${BASE}/master-rolls`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then((r) =>
      json<MasterRoll>(r),
    ),
  updateMasterRoll: (rollCode: string, body: MasterRoll) =>
    fetch(`${BASE}/master-rolls/${encodeURIComponent(rollCode)}`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify(body),
    }).then((r) => json<MasterRoll>(r)),
  deleteMasterRoll: (rollCode: string) =>
    fetch(`${BASE}/master-rolls/${encodeURIComponent(rollCode)}`, { method: 'DELETE', headers: headers() }).then(
      (r) => {
        if (!r.ok) {
          return r.text().then((text) => {
            throw new Error(text || r.statusText);
          });
        }
      },
    ),
  listChildOrders: () => fetch(`${BASE}/child-orders`, { headers: headers() }).then((r) => json<ChildSlittingOrder[]>(r)),
  listCatalog: () =>
    fetch(`${BASE}/intermediate-catalog`, { headers: headers() }).then((r) => json<IntermediateRollCatalog[]>(r)),
  listPlans: () => fetch(`${BASE}/plans`, { headers: headers() }).then((r) => json<SlittingPlanSummary[]>(r)),
  createPlan: (body: CreateSlittingPlanRequest) =>
    fetch(`${BASE}/plans`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then((r) =>
      json<SlittingPlanSummary>(r),
    ),
  solvePlan: (planVersionId: string) =>
    fetch(`${BASE}/plans/${planVersionId}/solve`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingPlanSummary>(r),
    ),
  getTree: (planVersionId: string) =>
    fetch(`${BASE}/plans/${planVersionId}/tree`, { headers: headers() }).then((r) => json<SlittingPlanTree>(r)),
  saveAssignments: (planVersionId: string, assignments: SlittingAssignment[]) =>
    fetch(`${BASE}/plans/${planVersionId}/assignments`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify({ assignments }),
    }).then((r) => json<SlittingPlanTree>(r)),
  saveTree: (planVersionId: string, nodes: SlittingPlanTree['nodes'], assignments: SlittingAssignment[]) =>
    fetch(`${BASE}/plans/${planVersionId}/tree`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify({ nodes, assignments }),
    }).then((r) => json<SlittingPlanTree>(r)),
  optimizeMaster: (planVersionId: string, masterNodeId: string, orderCodes?: string[]) =>
    fetch(`${BASE}/plans/${planVersionId}/masters/${encodeURIComponent(masterNodeId)}/optimize`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ orderCodes: orderCodes ?? [] }),
    }).then((r) => json<SlittingPlanTree>(r)),
  createSession: (planVersionId: string, activeParentNodeId: string | null) =>
    fetch(`${BASE}/sessions`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ planVersionId, activeParentNodeId }),
    }).then((r) => json<SlittingSession>(r)),
  sessionLocalOptimize: (sessionId: string) =>
    fetch(`${BASE}/sessions/${sessionId}/local-optimize`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingSession>(r),
    ),
  sessionAutoNest: (sessionId: string) =>
    fetch(`${BASE}/sessions/${sessionId}/auto-nest`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingSession>(r),
    ),
  sessionConfirm: (sessionId: string) =>
    fetch(`${BASE}/sessions/${sessionId}/confirm`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingPlanTree>(r),
    ),
  patchSession: (sessionId: string, assignmentPatches: SlittingAssignmentPatch[]) =>
    fetch(`${BASE}/sessions/${sessionId}`, {
      method: 'PATCH',
      headers: headers(),
      body: JSON.stringify({ assignmentPatches }),
    }).then((r) => json<SlittingSession>(r)),
  importChildOrdersFromDemand: (body?: ImportChildOrdersFromDemandRequest) =>
    fetch(`${BASE}/child-orders/from-demand`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify(body ?? { skipExisting: true }),
    }).then((r) => json<ImportChildOrdersFromDemandResult>(r)),
  listBomScopes: () =>
    fetch(`${BASE}/bom/scopes`, { headers: headers() }).then((r) => json<SlittingBomScope[]>(r)),
  listScopeBom: (scopeId: string) =>
    fetch(`${BASE}/bom/scopes/${encodeURIComponent(scopeId)}/components`, { headers: headers() }).then(
      (r) => json<BomMd[]>(r),
    ),
  demandsByMaterial: (productCode: string, finishedProductCode?: string) => {
    const q = new URLSearchParams({ productCode });
    if (finishedProductCode) q.set('finishedProductCode', finishedProductCode);
    return fetch(`${BASE}/bom/demands-by-material?${q}`, { headers: headers() }).then((r) =>
      json<SlittingMaterialDemand[]>(r),
    );
  },
  listSolverRuns: (limit = 30) =>
    fetch(`${BASE}/solver-runs?limit=${limit}`, { headers: headers() }).then((r) => json<SlittingSolverRun[]>(r)),
  getSolverRun: (runId: string) =>
    fetch(`${BASE}/solver-runs/${encodeURIComponent(runId)}`, { headers: headers() }).then((r) =>
      json<SlittingSolverRun>(r),
    ),
};
