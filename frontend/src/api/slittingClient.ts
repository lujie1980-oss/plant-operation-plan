import { getStoredWorkspaceId } from '../context/WorkspaceContext';
import type {
  ChildSlittingOrder,
  CreateSlittingPlanRequest,
  IntermediateRollCatalog,
  MasterRoll,
  SlittingAssignment,
  SlittingPlanSummary,
  SlittingPlanTree,
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
    throw new Error(text || res.statusText);
  }
  return res.json() as Promise<T>;
}

export const slittingClient = {
  listMasterRolls: () => fetch(`${BASE}/master-rolls`, { headers: headers() }).then((r) => json<MasterRoll[]>(r)),
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
};
