'use client';

import { useQuery } from '@tanstack/react-query';

interface SessionInfo {
  authenticated: boolean;
  email?: string;
  sub?: string;
}

async function fetchSession(): Promise<SessionInfo> {
  const res = await fetch('/auth/me', { credentials: 'include' });
  if (!res.ok) {
    return { authenticated: false };
  }
  return (await res.json()) as SessionInfo;
}

/** Current sign-in state for client components (ADR-013 §2). */
export function useSession() {
  const { data, isLoading } = useQuery({
    queryKey: ['session'],
    queryFn: fetchSession,
    staleTime: 60_000,
  });
  return {
    isLoading,
    isAuthenticated: data?.authenticated ?? false,
    email: data?.email,
  };
}
