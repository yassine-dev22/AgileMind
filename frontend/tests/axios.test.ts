import { AxiosError } from 'axios';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api, { getErrorMessage } from '../src/api/axios';

function token(exp: number) { return `header.${btoa(JSON.stringify({ exp }))}.signature`; }
const oldAdapter = api.defaults.adapter;
beforeEach(() => localStorage.clear());
afterEach(() => { localStorage.clear(); api.defaults.adapter = oldAdapter; });

describe('API authentication errors', () => {
  it('displays the backend error envelope and validation field messages', () => {
    const response = { status: 409, data: { error: 'Sprint scope is frozen' } } as AxiosResponse;
    expect(getErrorMessage(new AxiosError('Conflict', 'ERR_BAD_REQUEST', undefined, undefined, response), 'Fallback')).toBe('Sprint scope is frozen');
    response.data = { error: 'Validation failed', fields: { title: 'must not be blank' } };
    expect(getErrorMessage(new AxiosError('Bad request', 'ERR_BAD_REQUEST', undefined, undefined, response), 'Fallback')).toBe('title: must not be blank');
  });
  it('clears a rejected session on protected endpoint 401', async () => {
    const jwt = token(Date.now() / 1000 + 3600);
    localStorage.setItem('jwt_token', jwt);
    api.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
      const response = { status: 401, data: {}, headers: {}, statusText: 'Unauthorized', config } as AxiosResponse;
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
    };
    await expect(api.get('/home')).rejects.toThrow();
    expect(localStorage.getItem('jwt_token')).toBeNull();
  });

  it('does not log out the new session for a late 401 from an older token', async () => {
    const oldJwt = token(Date.now() / 1000 + 3600);
    const newJwt = token(Date.now() / 1000 + 7200);
    localStorage.setItem('jwt_token', oldJwt);
    api.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
      localStorage.setItem('jwt_token', newJwt);
      const response = { status: 401, data: {}, headers: {}, statusText: 'Unauthorized', config } as AxiosResponse;
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
    };
    await expect(api.get('/home')).rejects.toThrow();
    expect(localStorage.getItem('jwt_token')).toBe(newJwt);
  });

  it('lets users log in again with an expired token in storage', async () => {
    localStorage.setItem('jwt_token', token(Date.now() / 1000 - 1));
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => ({ status: 200, data: {}, headers: {}, statusText: 'OK', config }));
    api.defaults.adapter = adapter;
    await api.post('/auth/login', { email: 'user@test.dev', password: 'password' });
    expect(adapter).toHaveBeenCalledOnce();
    expect(adapter.mock.calls[0][0].headers.Authorization).toBeUndefined();
  });
});
