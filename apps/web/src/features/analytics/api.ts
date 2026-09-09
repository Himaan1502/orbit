'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPatch, apiPost } from '@/lib/http';
import type {
  CareerAnalytics,
  ExecutionAnalytics,
  SkillAnalytics,
  WeeklyReview,
} from '@/lib/api-types';

const keys = {
  execution: (range: string) => ['analytics', 'execution', range] as const,
  career: (id: string, range: string) => ['analytics', 'career', id, range] as const,
  skills: ['analytics', 'skills'] as const,
  reviews: ['weekly-reviews'] as const,
  review: (week: string) => ['weekly-reviews', week] as const,
};

function rangeQuery(from?: string, to?: string) {
  const p = new URLSearchParams();
  if (from) p.set('from', from);
  if (to) p.set('to', to);
  return p.toString();
}

export function useExecutionAnalytics(from?: string, to?: string) {
  const q = rangeQuery(from, to);
  return useQuery({
    queryKey: keys.execution(q),
    queryFn: () => apiGet<ExecutionAnalytics>(`/analytics/execution${q ? `?${q}` : ''}`),
  });
}

export function useCareerAnalytics(careerTargetId?: string, from?: string, to?: string) {
  const p = new URLSearchParams(rangeQuery(from, to));
  if (careerTargetId) p.set('careerTargetId', careerTargetId);
  const q = p.toString();
  return useQuery({
    queryKey: keys.career(careerTargetId ?? 'primary', q),
    queryFn: async () => {
      try {
        return await apiGet<CareerAnalytics>(`/analytics/career${q ? `?${q}` : ''}`);
      } catch (e: unknown) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

export function useSkillAnalytics() {
  return useQuery({
    queryKey: keys.skills,
    queryFn: () => apiGet<SkillAnalytics>('/analytics/skills'),
  });
}

export function useWeeklyReviews() {
  return useQuery({
    queryKey: keys.reviews,
    queryFn: () => apiGet<WeeklyReview[]>('/weekly-reviews'),
  });
}

export function useWeeklyReview(weekStart: string, enabled = true) {
  return useQuery({
    queryKey: keys.review(weekStart),
    enabled: enabled && !!weekStart,
    retry: false,
    queryFn: async () => {
      try {
        return await apiGet<WeeklyReview>(`/weekly-reviews/${weekStart}`);
      } catch (e: unknown) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

export function useGenerateWeeklyReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (weekStart?: string) =>
      apiPost<WeeklyReview>('/weekly-reviews/generate', weekStart ? { weekStart } : {}),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: keys.reviews });
      qc.setQueryData(keys.review(r.weekStart), r);
    },
  });
}

export function useUpdateWeeklyReview(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { userNotes?: string; status?: string }) =>
      apiPatch<WeeklyReview>(`/weekly-reviews/${id}`, input),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: keys.reviews });
      qc.setQueryData(keys.review(r.weekStart), r);
    },
  });
}

function isNotFound(e: unknown): boolean {
  return (
    typeof e === 'object' &&
    e !== null &&
    'response' in e &&
    (e as { response?: { status?: number } }).response?.status === 404
  );
}
