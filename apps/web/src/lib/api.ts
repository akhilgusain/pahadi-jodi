// Minimal fetch client: bearer token + one transparent refresh on 401 (rotating refresh tokens).
export type Tokens = { accessToken: string; refreshToken: string; expiresIn: number };

const KEY = 'pj.tokens';
export const tokenStore = {
  get: (): Tokens | null => {
    try { return JSON.parse(localStorage.getItem(KEY) ?? 'null'); } catch { return null; }
  },
  set: (t: Tokens | null) => (t ? localStorage.setItem(KEY, JSON.stringify(t)) : localStorage.removeItem(KEY)),
};
// NOTE: localStorage is a skeleton shortcut; production should keep the refresh token in an
// httpOnly SameSite cookie set by the gateway (see docs/01 security architecture).

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

let refreshing: Promise<boolean> | null = null;
async function refresh(): Promise<boolean> {
  const t = tokenStore.get();
  if (!t) return false;
  refreshing ??= fetch('/api/v1/auth/refresh', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: t.refreshToken }),
  }).then(async (r) => { if (!r.ok) { tokenStore.set(null); return false; } tokenStore.set(await r.json()); return true; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const t = tokenStore.get();
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t.accessToken}` } : {}), ...init.headers },
  });
  if (res.status === 401 && retry && (await refresh())) return api<T>(path, init, false);
  if (!res.ok) {
    const p = await res.json().catch(() => ({}));
    throw new ApiError(res.status, p.code ?? 'ERROR', p.detail ?? res.statusText);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}
