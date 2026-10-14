import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import {
  AUTH_TOKEN_KEY, AUTH_USER_KEY, SESSION_EXPIRED_EVENT, clearSession,
  normalizeUser, readSession, tokenExpiry,
} from '../api/session';
import type { Role, User } from '../api/session';
export type { Role, User } from '../api/session';

interface AuthContextType {
  user: User | null;
  role: Role;
  isAuthenticated: boolean;
  login: (token: string, user: unknown) => void;
  logout: () => void;
  updateUser: (user: unknown) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [session, setSession] = useState(readSession);
  const logout = useCallback(() => { clearSession(); setSession(null); }, []);

  useEffect(() => {
    const onExpired = () => setSession(null);
    const onStorage = (event: StorageEvent) => {
      if (event.key === AUTH_TOKEN_KEY || event.key === AUTH_USER_KEY || event.key === null) {
        setSession(readSession());
      }
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    window.addEventListener('storage', onStorage);
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  useEffect(() => {
    if (!session) return;
    let timer: ReturnType<typeof setTimeout>;
    const scheduleExpiry = () => {
      const remaining = (tokenExpiry(session.token) ?? 0) - Date.now();
      if (remaining <= 0) { logout(); return; }
      timer = setTimeout(scheduleExpiry, Math.min(remaining, 2_147_483_647));
    };
    scheduleExpiry();
    return () => clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback((token: string, value: unknown) => {
    const user = normalizeUser(value);
    if (!user || (tokenExpiry(token) ?? 0) <= Date.now()) throw new Error('Invalid authentication response. Please sign in again.');
    localStorage.removeItem('levelup_current_project_id');
    localStorage.setItem(AUTH_TOKEN_KEY, token);
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
    setSession({ token, user });
  }, []);

  const updateUser = useCallback((value: unknown) => {
    const user = normalizeUser(value);
    if (!user) throw new Error('Invalid user response.');
    setSession(previous => previous ? { ...previous, user } : null);
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  }, []);

  const value = useMemo(() => ({
    user: session?.user ?? null, role: session?.user.role ?? 'VIEWER' as Role,
    isAuthenticated: session !== null, login, logout, updateUser,
  }), [session, login, logout, updateUser]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components -- This hook shares the provider's private context.
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};
