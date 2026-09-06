import axios from 'axios';

/**
 * Browser API client. Talks only to the same-origin BFF proxy (`/api/orbit/*`,
 * ADR-013 §3) — the Next.js server attaches the OIDC bearer token and refreshes
 * it. No tokens or auth headers are handled here.
 */
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? '/api/orbit';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-ID'] = crypto.randomUUID();
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // The BFF proxy already tried a token refresh. A 401 here means the session
    // is gone — send the user back through sign-in.
    if (typeof window !== 'undefined' && error?.response?.status === 401) {
      const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.assign(`/login?returnTo=${returnTo}`);
    }
    return Promise.reject(error);
  }
);
