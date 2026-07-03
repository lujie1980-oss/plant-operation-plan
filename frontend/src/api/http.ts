import { getStoredWorkspaceId } from '../context/WorkspaceContext';

export const AUTH_TOKEN_KEY = 'plantops.authToken';

export function getAuthToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function setAuthToken(token: string | null): void {
  if (token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
  } else {
    localStorage.removeItem(AUTH_TOKEN_KEY);
  }
}

function mergeHeaders(base: HeadersInit, extra?: HeadersInit): Headers {
  const headers = new Headers(base);
  if (extra) {
    new Headers(extra).forEach((value, key) => headers.set(key, value));
  }
  return headers;
}

export function apiHeaders(extra?: HeadersInit): HeadersInit {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Workspace-Id': getStoredWorkspaceId(),
  };
  const token = getAuthToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { ...headers, ...(extra as Record<string, string> | undefined) };
}

let redirectOn401 = true;

export function setRedirectOn401(enabled: boolean) {
  redirectOn401 = enabled;
}

export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const res = await fetch(path, {
    ...init,
    headers: mergeHeaders(apiHeaders(), init?.headers),
  });
  if (res.status === 401 && redirectOn401 && !path.includes('/api/v1/auth/')) {
    setAuthToken(null);
    window.location.hash = '#/login';
  }
  return res;
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await apiFetch(path, init);
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<T>;
}
