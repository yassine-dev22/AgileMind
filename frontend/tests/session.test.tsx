import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../src/context/AuthContext';
import { normalizeUser, readSession, tokenExpiry } from '../src/api/session';

const user = { id: 1, email: 'user@test.dev', fullName: 'Test User', role: 'MEMBER', capacityPoints: 0 };
function token(exp: number) { return `header.${btoa(JSON.stringify({ exp }))}.signature`; }
function Status() { const auth = useAuth(); return <output>{auth.isAuthenticated ? 'signed in' : 'signed out'}</output>; }
afterEach(() => { cleanup(); vi.useRealTimers(); localStorage.clear(); });

describe('authentication lifecycle', () => {
  it('rejects expired, malformed, missing-user and corrupt stored sessions', () => {
    localStorage.setItem('auth_user', JSON.stringify(user));
    localStorage.setItem('jwt_token', token(Date.now() / 1000 - 1));
    expect(readSession()).toBeNull();
    expect(tokenExpiry('broken')).toBeNull();
    localStorage.setItem('jwt_token', token(Date.now() / 1000 + 3600));
    localStorage.setItem('auth_user', '{');
    expect(readSession()).toBeNull();
    localStorage.removeItem('auth_user');
    expect(readSession()).toBeNull();
    expect(normalizeUser(user)?.capacityPoints).toBe(0);
  });

  it('expires a session while the app remains open', () => {
    vi.useFakeTimers();
    localStorage.setItem('auth_user', JSON.stringify(user));
    localStorage.setItem('jwt_token', token((Date.now() + 1000) / 1000));
    render(<AuthProvider><Status /></AuthProvider>);
    expect(screen.getByText('signed in')).toBeTruthy();
    act(() => { vi.advanceTimersByTime(1001); });
    expect(screen.getByText('signed out')).toBeTruthy();
    expect(localStorage.getItem('jwt_token')).toBeNull();
  });

  it('reacts to logout in another tab and removes its listeners on unmount', () => {
    localStorage.setItem('auth_user', JSON.stringify(user));
    localStorage.setItem('jwt_token', token(Date.now() / 1000 + 3600));
    const { unmount } = render(<AuthProvider><Status /></AuthProvider>);
    act(() => { localStorage.removeItem('jwt_token'); window.dispatchEvent(new StorageEvent('storage', { key: 'jwt_token' })); });
    expect(screen.getByText('signed out')).toBeTruthy();
    unmount();
  });
});
