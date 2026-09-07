'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost } from '@/lib/http';
import type {
  SkillAssessmentResult,
  SkillDependencies,
  SkillDetail,
  SkillDimensions,
  SkillSummary,
} from '@/lib/api-types';

const keys = {
  all: ['skills'] as const,
  detail: (id: string) => ['skills', id] as const,
  dependencies: (id: string) => ['skills', id, 'dependencies'] as const,
};

export function useSkills() {
  return useQuery({
    queryKey: keys.all,
    queryFn: () => apiGet<SkillSummary[]>('/skills'),
  });
}

export function useSkill(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => apiGet<SkillDetail>(`/skills/${id}`),
  });
}

export function useSkillDependencies(id: string) {
  return useQuery({
    queryKey: keys.dependencies(id),
    queryFn: () => apiGet<SkillDependencies>(`/skills/${id}/dependencies`),
  });
}

export function useAssessSkill(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dimensions: SkillDimensions) =>
      apiPost<SkillAssessmentResult>(`/skills/${id}/assessments`, dimensions),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all });
      qc.invalidateQueries({ queryKey: keys.detail(id) });
      qc.invalidateQueries({ queryKey: keys.dependencies(id) });
      // readiness and gaps depend on assessments
      qc.invalidateQueries({ queryKey: ['career'] });
    },
  });
}
