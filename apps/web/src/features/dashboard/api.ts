'use client';

import { useQuery } from '@tanstack/react-query';
import { apiGet } from '@/lib/http';
import type { Dashboard } from '@/lib/api-types';

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => apiGet<Dashboard>('/dashboard'),
  });
}
