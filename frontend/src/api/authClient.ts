import type { AuthConfigDto, AuthTokenDto } from '../types/auth';
import { apiJson } from './http';

export async function fetchAuthConfig(): Promise<AuthConfigDto> {
  const res = await fetch('/api/v1/auth/config', { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function login(loginName: string, password: string): Promise<AuthTokenDto> {
  return apiJson<AuthTokenDto>('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginName, password }),
  });
}

export async function register(payload: {
  userId: string;
  loginName: string;
  displayName: string;
  password: string;
}): Promise<AuthTokenDto> {
  return apiJson<AuthTokenDto>('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function oidcRedirectUri(): string {
  return `${window.location.origin}${window.location.pathname}`;
}

export async function fetchOidcAuthorizationUrl(redirectUri: string): Promise<string> {
  const res = await fetch(
    `/api/v1/auth/oidc/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!res.ok) throw new Error(await res.text());
  const body = await res.json() as { authorizationUrl: string };
  return body.authorizationUrl;
}

export async function exchangeOidcCode(code: string, redirectUri: string): Promise<AuthTokenDto> {
  return apiJson<AuthTokenDto>('/api/v1/auth/oidc/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, redirectUri }),
  });
}
