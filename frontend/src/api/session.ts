export type Role = 'OWNER' | 'MEMBER' | 'VIEWER';

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  capacityPoints: number;
}

export const AUTH_TOKEN_KEY = 'jwt_token';
export const AUTH_USER_KEY = 'auth_user';
export const SESSION_EXPIRED_EVENT = 'levelup:session-expired';
const ROLES: Role[] = ['OWNER', 'MEMBER', 'VIEWER'];

// This is an expiry check for the UI; the server verifies the JWT signature.
export function tokenExpiry(token: string): number | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: unknown };
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp) ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function normalizeUser(value: unknown): User | null {
  if (!value || typeof value !== 'object') return null;
  const raw = value as Record<string, unknown>;
  if (!Number.isSafeInteger(raw.id) || Number(raw.id) <= 0 || typeof raw.email !== 'string') return null;
  return {
    id: Number(raw.id),
    email: raw.email,
    fullName: typeof raw.fullName === 'string' && raw.fullName.trim() ? raw.fullName : 'Unknown User',
    role: ROLES.includes(raw.role as Role) ? raw.role as Role : 'VIEWER',
    capacityPoints: typeof raw.capacityPoints === 'number' && Number.isFinite(raw.capacityPoints)
      ? Math.max(0, raw.capacityPoints) : 40,
  };
}

export interface Session { token: string; user: User }

export function readSession(): Session | null {
  try {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const expiry = token ? tokenExpiry(token) : null;
    const user = normalizeUser(JSON.parse(localStorage.getItem(AUTH_USER_KEY) ?? 'null'));
    return token && expiry !== null && expiry > Date.now() && user ? { token, user } : null;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
  localStorage.removeItem('levelup_current_project_id');
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}
