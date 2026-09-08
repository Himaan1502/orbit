'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/http';
import type { Project } from '@/lib/api-types';

const keys = {
  all: ['projects'] as const,
  detail: (id: string) => ['projects', id] as const,
};

export function useProjects() {
  return useQuery({
    queryKey: keys.all,
    queryFn: () => apiGet<Project[]>('/projects'),
  });
}

export function useProject(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => apiGet<Project>(`/projects/${id}`),
    enabled: !!id,
  });
}

export interface ProjectSkillInput {
  skillId: string;
  expectedContribution?: number;
}

export interface ProjectInput {
  name: string;
  description?: string;
  objectiveId?: string | null;
  repositoryUrl?: string | null;
  demoUrl?: string | null;
  skills?: ProjectSkillInput[];
}

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectInput) => apiPost<Project>('/projects', input),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateProject(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<ProjectInput> & { status?: string }) =>
      apiPatch<Project>(`/projects/${id}`, input),
    onSuccess: () => {
      invalidate(qc);
      qc.invalidateQueries({ queryKey: keys.detail(id) });
    },
  });
}

export function useDeleteProject(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiDelete(`/projects/${id}`),
    onSuccess: () => invalidate(qc),
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: keys.all });
  // demonstrated_contribution feeds skills, career readiness and the dashboard
  qc.invalidateQueries({ queryKey: ['evidence'] });
  qc.invalidateQueries({ queryKey: ['skills'] });
  qc.invalidateQueries({ queryKey: ['career'] });
  qc.invalidateQueries({ queryKey: ['dashboard'] });
  qc.invalidateQueries({ queryKey: ['tasks'] });
}
