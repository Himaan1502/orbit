import { apiClient } from './api';

/** Unwraps the `{ data }` envelope (ADR-003) from the BFF proxy response. */
export async function apiGet<T>(path: string): Promise<T> {
  const res = await apiClient.get(path);
  return res.data.data as T;
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await apiClient.post(path, body);
  return res.data?.data as T;
}

export async function apiPatch<T>(path: string, body: unknown): Promise<T> {
  const res = await apiClient.patch(path, body);
  return res.data.data as T;
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const res = await apiClient.put(path, body);
  return res.data?.data as T;
}

export async function apiDelete(path: string): Promise<void> {
  await apiClient.delete(path);
}
