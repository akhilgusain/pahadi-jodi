import { tokenStore } from './api';

/** Reads the (unverified, display-only) roles claim from the stored access token. The server enforces real authorization. */
export function currentRoles(): string[] {
  try {
    const t = tokenStore.get();
    if (!t) return [];
    const payload = JSON.parse(atob(t.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return Array.isArray(payload.roles) ? payload.roles : [];
  } catch { return []; }
}
