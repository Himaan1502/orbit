'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/http';
import type { Evidence } from '@/lib/api-types';

const keys = {
  all: ['evidence'] as const,
  list: (filter: string) => ['evidence', 'list', filter] as const,
  detail: (id: string) => ['evidence', id] as const,
  forSkill: (skillId: string) => ['evidence', 'skill', skillId] as const,
};

export interface EvidenceFilter {
  type?: string;
  projectId?: string;
  skillId?: string;
}

export function useEvidenceList(filter: EvidenceFilter = {}) {
  const params = new URLSearchParams();
  if (filter.type) params.set('type', filter.type);
  if (filter.projectId) params.set('projectId', filter.projectId);
  if (filter.skillId) params.set('skillId', filter.skillId);
  const qs = params.toString();
  return useQuery({
    queryKey: keys.list(qs),
    queryFn: () => apiGet<Evidence[]>(`/evidence${qs ? `?${qs}` : ''}`),
  });
}

export function useEvidence(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => apiGet<Evidence>(`/evidence/${id}`),
    enabled: !!id,
  });
}

export function useSkillEvidence(skillId: string) {
  return useQuery({
    queryKey: keys.forSkill(skillId),
    queryFn: () => apiGet<Evidence[]>(`/skills/${skillId}/evidence`),
    enabled: !!skillId,
  });
}

export interface EvidenceSkillInput {
  skillId: string;
  contributionWeight?: number;
}

export interface EvidenceInput {
  type: string;
  title: string;
  description?: string;
  sourceUrl?: string | null;
  externalIdentifier?: string | null;
  occurredAt?: string | null;
  skills?: EvidenceSkillInput[];
  projects?: { projectId: string }[];
}

export function useCreateEvidence() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: EvidenceInput) => apiPost<Evidence>('/evidence', input),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateEvidence(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<EvidenceInput>) => apiPatch<Evidence>(`/evidence/${id}`, input),
    onSuccess: () => {
      invalidate(qc);
      qc.invalidateQueries({ queryKey: keys.detail(id) });
    },
  });
}

export function useDeleteEvidence(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiDelete(`/evidence/${id}`),
    onSuccess: () => invalidate(qc),
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: keys.all });
  // evidence raises assessment confidence and feeds project / career coverage
  qc.invalidateQueries({ queryKey: ['skills'] });
  qc.invalidateQueries({ queryKey: ['projects'] });
  qc.invalidateQueries({ queryKey: ['career'] });
  qc.invalidateQueries({ queryKey: ['dashboard'] });
}
