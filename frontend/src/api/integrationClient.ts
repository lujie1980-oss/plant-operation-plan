import { apiFetch, apiHeaders } from './http';

export type IntegrationBatch = {
  importBatchId: string;
  adapterId: string;
  sourceSystem: string;
  rowCount: number;
  pendingCount: number;
  errorCount: number;
  qualityStatus: string;
  createdAt: string;
};

export type IntegrationAdapterStatus = {
  adapterId: string;
  name: string;
  enabled: boolean;
  configured: boolean;
  lastRunAt?: string;
  lastStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING';
  lastMessage?: string;
};

export type ExternalTableInfo = {
  domain: 'master' | 'transactional';
  tableName: string;
  label: string;
  rowCount?: number;
};

const BASE = '/api/v1/integration';

async function integrationRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(apiHeaders(init?.headers));
  if (init?.body != null && !headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await apiFetch(path, { ...init, headers });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export const integrationApi = {
  listBatches: (limit = 20) =>
    integrationRequest<IntegrationBatch[]>(`${BASE}/batches?limit=${limit}`),

  listAdapters: () => integrationRequest<IntegrationAdapterStatus[]>(`${BASE}/adapters`),

  listExternalTables: (domain: 'master' | 'transactional') =>
    integrationRequest<ExternalTableInfo[]>(`${BASE}/external/${domain}/tables`),

  runAdapter: (adapterId: string) =>
    integrationRequest<void>(`${BASE}/adapters/${encodeURIComponent(adapterId)}/run`, {
      method: 'POST',
    }),

  uploadExcel: (file: File, validateOnly = false) => {
    const form = new FormData();
    form.append('file', file);
    form.append('validateOnly', String(validateOnly));
    return integrationRequest<{ importBatchId: string; rowCount: number }>(
      `${BASE}/adapters/excel/upload`,
      { method: 'POST', body: form, headers: apiHeaders() },
    );
  },
};
