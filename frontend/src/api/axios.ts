import axios from 'axios';
import { AUTH_TOKEN_KEY, clearSession, tokenExpiry } from './session';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8082/api',
  timeout: 15_000,
});

api.interceptors.request.use(
  (config) => {
    // An expired session must not prevent a new login or registration.
    if (config.url?.startsWith('/auth/')) return config;
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token && (tokenExpiry(token) ?? 0) <= Date.now()) {
      clearSession();
      return Promise.reject(new Error('Your session expired. Please sign in again.'));
    }
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(response => response, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401) {
    const sentToken = error.config?.headers?.Authorization;
    // A late failure for a previous session must not log out a newly signed in user.
    if (sentToken && sentToken === `Bearer ${localStorage.getItem(AUTH_TOKEN_KEY)}`) clearSession();
  }
  return Promise.reject(error);
});

export function getErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const data: unknown = error.response?.data;
    if (typeof data === 'string' && data.trim()) return data;
    if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') return data.message;
    if (data && typeof data === 'object' && 'error' in data && typeof data.error === 'string') {
      if ('fields' in data && data.fields && typeof data.fields === 'object') {
        const fields = Object.entries(data.fields).filter((entry): entry is [string, string] => typeof entry[1] === 'string');
        if (fields.length) return fields.map(([field, message]) => `${field}: ${message}`).join('; ');
      }
      return data.error;
    }
    if (!error.response) return 'Unable to reach the server. Please try again.';
    return fallback;
  }
  return error instanceof Error ? error.message : fallback;
}

export default api;
