'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/http';
import type { Milestone, Objective } from '@/lib/api-types';

const keys = {
  all: ['objectives'] as const,
  detail: (id: string) => ['objectives', id] as const,
  milestones: (id: string) => ['objectives', id, 'milestones'] as const,
};

export function useObjectives(status?: string) {
  return useQuery({
    queryKey: [...keys.all, status ?? 'all'],
    queryFn: () => apiGet<Objective[]>(`/objectives${status ? `?status=${status}` : ''}`),
  });
}

export function useObjective(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => apiGet<Objective>(`/objectives/${id}`),
  });
}

export function useMilestones(objectiveId: string) {
  return useQuery({
    queryKey: keys.milestones(objectiveId),
    queryFn: () => apiGet<Milestone[]>(`/objectives/${objectiveId}/milestones`),
  });
}

export interface CreateObjectiveInput {
  title: string;
  description?: string;
  priority?: number;
  targetDate?: string | null;
}

export function useCreateObjective() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateObjectiveInput) => apiPost<Objective>('/objectives', input),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.all }),
  });
}

export function useUpdateObjective(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<CreateObjectiveInput> & { status?: string }) =>
      apiPatch<Objective>(`/objectives/${id}`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all });
      qc.invalidateQueries({ queryKey: keys.detail(id) });
    },
  });
}

export function useCreateMilestone(objectiveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { title: string; description?: string; targetDate?: string | null }) =>
      apiPost<Milestone>(`/objectives/${objectiveId}/milestones`, input),
    onSuccess: () => invalidateObjective(qc, objectiveId),
  });
}

export function useUpdateMilestone(objectiveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: { id: string; status?: string; title?: string }) =>
      apiPatch<Milestone>(`/objectives/${objectiveId}/milestones/${id}`, input),
    onSuccess: () => invalidateObjective(qc, objectiveId),
  });
}

export function useDeleteMilestone(objectiveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiDelete(`/objectives/${objectiveId}/milestones/${id}`),
    onSuccess: () => invalidateObjective(qc, objectiveId),
  });
}

function invalidateObjective(qc: ReturnType<typeof useQueryClient>, objectiveId: string) {
  qc.invalidateQueries({ queryKey: keys.all });
  qc.invalidateQueries({ queryKey: keys.detail(objectiveId) });
  qc.invalidateQueries({ queryKey: keys.milestones(objectiveId) });
}
