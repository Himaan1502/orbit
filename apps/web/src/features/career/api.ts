'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPatch, apiPost } from '@/lib/http';
import type { CareerGaps, CareerReadiness, CareerTarget } from '@/lib/api-types';

const keys = {
  targets: ['career', 'targets'] as const,
  target: (id: string) => ['career', 'targets', id] as const,
  readiness: (id?: string) => ['career', 'readiness', id ?? 'primary'] as const,
  gaps: (id?: string) => ['career', 'gaps', id ?? 'primary'] as const,
};

export interface CareerTargetSkillInput {
  skillId: string;
  importance?: number;
  interviewWeight?: number;
  productionWeight?: number;
}

export interface CareerTargetInput {
  name: string;
  roleFamily?: string;
  specialization?: string;
  priority?: number;
  targetDate?: string | null;
  targetReadiness?: number | null;
  skills?: CareerTargetSkillInput[];
}

export function useCareerTargets() {
  return useQuery({
    queryKey: keys.targets,
    queryFn: () => apiGet<CareerTarget[]>('/career/targets'),
  });
}

export function useCareerTarget(id: string) {
  return useQuery({
    queryKey: keys.target(id),
    queryFn: () => apiGet<CareerTarget>(`/career/targets/${id}`),
    enabled: !!id,
  });
}

/** Readiness for the primary target, or an explicit one. Returns null when no target exists yet. */
export function useReadiness(careerTargetId?: string) {
  return useQuery({
    queryKey: keys.readiness(careerTargetId),
    queryFn: async () => {
      try {
        const q = careerTargetId ? `?careerTargetId=${careerTargetId}` : '';
        return await apiGet<CareerReadiness>(`/career/readiness${q}`);
      } catch (e: unknown) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

export function useGaps(careerTargetId?: string) {
  return useQuery({
    queryKey: keys.gaps(careerTargetId),
    queryFn: async () => {
      try {
        const q = careerTargetId ? `?careerTargetId=${careerTargetId}` : '';
        return await apiGet<CareerGaps>(`/career/gaps${q}`);
      } catch (e: unknown) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

export function useCreateCareerTarget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CareerTargetInput) => apiPost<CareerTarget>('/career/targets', input),
    onSuccess: () => invalidateCareer(qc),
  });
}

export function useUpdateCareerTarget(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<CareerTargetInput> & { status?: string }) =>
      apiPatch<CareerTarget>(`/career/targets/${id}`, input),
    onSuccess: () => {
      invalidateCareer(qc);
      qc.invalidateQueries({ queryKey: keys.target(id) });
    },
  });
}

function invalidateCareer(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['career'] });
  qc.invalidateQueries({ queryKey: ['objectives'] });
  qc.invalidateQueries({ queryKey: ['dashboard'] });
}

function isNotFound(e: unknown): boolean {
  return (
    typeof e === 'object' &&
    e !== null &&
    'response' in e &&
    (e as { response?: { status?: number } }).response?.status === 404
  );
}
