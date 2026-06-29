import { apiFetch, apiHeaders } from './http';
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
  return apiHeaders({ 'Content-Type': 'application/json' });
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
  listMasterRolls: () => apiFetch(`${BASE}/master-rolls`, { headers: headers() }).then((r) => json<MasterRoll[]>(r)),
  createMasterRoll: (body: MasterRoll) =>
    apiFetch(`${BASE}/master-rolls`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then((r) =>
      json<MasterRoll>(r),
    ),
  updateMasterRoll: (rollCode: string, body: MasterRoll) =>
    apiFetch(`${BASE}/master-rolls/${encodeURIComponent(rollCode)}`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify(body),
    }).then((r) => json<MasterRoll>(r)),
  deleteMasterRoll: (rollCode: string) =>
    apiFetch(`${BASE}/master-rolls/${encodeURIComponent(rollCode)}`, { method: 'DELETE', headers: headers() }).then(
      (r) => {
        if (!r.ok) {
          return r.text().then((text) => {
            throw new Error(text || r.statusText);
          });
        }
      },
    ),
  listChildOrders: () => apiFetch(`${BASE}/child-orders`, { headers: headers() }).then((r) => json<ChildSlittingOrder[]>(r)),
  listCatalog: () =>
    apiFetch(`${BASE}/intermediate-catalog`, { headers: headers() }).then((r) => json<IntermediateRollCatalog[]>(r)),
  listPlans: () => apiFetch(`${BASE}/plans`, { headers: headers() }).then((r) => json<SlittingPlanSummary[]>(r)),
  createPlan: (body: CreateSlittingPlanRequest) =>
    apiFetch(`${BASE}/plans`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then((r) =>
      json<SlittingPlanSummary>(r),
    ),
  solvePlan: (planVersionId: string) =>
    apiFetch(`${BASE}/plans/${planVersionId}/solve`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingPlanSummary>(r),
    ),
  getTree: (planVersionId: string) =>
    apiFetch(`${BASE}/plans/${planVersionId}/tree`, { headers: headers() }).then((r) => json<SlittingPlanTree>(r)),
  saveAssignments: (planVersionId: string, assignments: SlittingAssignment[]) =>
    apiFetch(`${BASE}/plans/${planVersionId}/assignments`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify({ assignments }),
    }).then((r) => json<SlittingPlanTree>(r)),
  saveTree: (planVersionId: string, nodes: SlittingPlanTree['nodes'], assignments: SlittingAssignment[]) =>
    apiFetch(`${BASE}/plans/${planVersionId}/tree`, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify({ nodes, assignments }),
    }).then((r) => json<SlittingPlanTree>(r)),
  optimizeMaster: (planVersionId: string, masterNodeId: string, orderCodes?: string[]) =>
    apiFetch(`${BASE}/plans/${planVersionId}/masters/${encodeURIComponent(masterNodeId)}/optimize`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ orderCodes: orderCodes ?? [] }),
    }).then((r) => json<SlittingPlanTree>(r)),
  createSession: (planVersionId: string, activeParentNodeId: string | null) =>
    apiFetch(`${BASE}/sessions`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ planVersionId, activeParentNodeId }),
    }).then((r) => json<SlittingSession>(r)),
  sessionLocalOptimize: (sessionId: string) =>
    apiFetch(`${BASE}/sessions/${sessionId}/local-optimize`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingSession>(r),
    ),
  sessionAutoNest: (sessionId: string) =>
    apiFetch(`${BASE}/sessions/${sessionId}/auto-nest`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingSession>(r),
    ),
  sessionConfirm: (sessionId: string) =>
    apiFetch(`${BASE}/sessions/${sessionId}/confirm`, { method: 'POST', headers: headers() }).then((r) =>
      json<SlittingPlanTree>(r),
    ),
  patchSession: (sessionId: string, assignmentPatches: SlittingAssignmentPatch[]) =>
    apiFetch(`${BASE}/sessions/${sessionId}`, {
      method: 'PATCH',
      headers: headers(),
      body: JSON.stringify({ assignmentPatches }),
    }).then((r) => json<SlittingSession>(r)),
  importChildOrdersFromDemand: (body?: ImportChildOrdersFromDemandRequest) =>
    apiFetch(`${BASE}/child-orders/from-demand`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify(body ?? { skipExisting: true }),
    }).then((r) => json<ImportChildOrdersFromDemandResult>(r)),
  listBomScopes: () =>
    apiFetch(`${BASE}/bom/scopes`, { headers: headers() }).then((r) => json<SlittingBomScope[]>(r)),
  listScopeBom: (scopeId: string) =>
    apiFetch(`${BASE}/bom/scopes/${encodeURIComponent(scopeId)}/components`, { headers: headers() }).then(
      (r) => json<BomMd[]>(r),
    ),
  demandsByMaterial: (productCode: string, finishedProductCode?: string) => {
    const q = new URLSearchParams({ productCode });
    if (finishedProductCode) q.set('finishedProductCode', finishedProductCode);
    return apiFetch(`${BASE}/bom/demands-by-material?${q}`, { headers: headers() }).then((r) =>
      json<SlittingMaterialDemand[]>(r),
    );
  },
  listSolverRuns: (limit = 30) =>
    apiFetch(`${BASE}/solver-runs?limit=${limit}`, { headers: headers() }).then((r) => json<SlittingSolverRun[]>(r)),
  getSolverRun: (runId: string) =>
    apiFetch(`${BASE}/solver-runs/${encodeURIComponent(runId)}`, { headers: headers() }).then((r) =>
      json<SlittingSolverRun>(r),
    ),
};
