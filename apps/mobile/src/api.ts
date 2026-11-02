import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

// 10.0.2.2 = host machine from the Android emulator. Use your LAN IP for a physical device.
const BASE = (Constants.expoConfig?.extra?.apiUrl as string) ?? 'http://localhost:8080';
const KEY = 'pj.refreshToken';

export type Tokens = { accessToken: string; refreshToken: string };
let access: string | null = null;

export async function authenticate(mode: 'login' | 'register', email: string, password: string) {
  const r = await fetch(`${BASE}/api/v1/auth/${mode}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).detail ?? 'Request failed');
  const t = (await r.json()) as Tokens;
  access = t.accessToken;
  await SecureStore.setItemAsync(KEY, t.refreshToken);   // Keychain / Keystore
}

/** Restores a session from the stored refresh token (call after a successful biometric check). */
export async function restoreSession(): Promise<boolean> {
  const rt = await SecureStore.getItemAsync(KEY);
  if (!rt) return false;
  const r = await fetch(`${BASE}/api/v1/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }),
  });
  if (!r.ok) { await SecureStore.deleteItemAsync(KEY); return false; }
  const t = (await r.json()) as Tokens;
  access = t.accessToken;
  await SecureStore.setItemAsync(KEY, t.refreshToken);   // rotation: always store the new one
  return true;
}

export async function myProfile() {
  const r = await fetch(`${BASE}/api/v1/profiles/me`, { headers: { Authorization: `Bearer ${access}` } });
  return r.status === 404 ? null : r.json();
}
