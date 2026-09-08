'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPut } from '@/lib/http';
import { apiClient } from '@/lib/api';
import type {
  RoadmapAdoption,
  RoadmapDetail,
  RoadmapIntersection,
  RoadmapSummary,
  RoadmapTopic,
} from '@/lib/api-types';

const keys = {
  all: ['roadmaps'] as const,
  detail: (id: string) => ['roadmaps', id] as const,
  topics: (id: string) => ['roadmaps', id, 'topics'] as const,
  intersection: (ids: string[]) => ['roadmaps', 'intersection', [...ids].sort().join(',')] as const,
};

export function useRoadmaps() {
  return useQuery({
    queryKey: keys.all,
    queryFn: () => apiGet<RoadmapSummary[]>('/roadmaps'),
  });
}

export function useRoadmap(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => apiGet<RoadmapDetail>(`/roadmaps/${id}`),
    enabled: !!id,
  });
}

export function useRoadmapTopics(id: string) {
  return useQuery({
    queryKey: keys.topics(id),
    queryFn: () => apiGet<RoadmapTopic[]>(`/roadmaps/${id}/topics`),
    enabled: !!id,
  });
}

/** Intersection of the given roadmap ids (>= 2), or the caller's adopted roadmaps when empty. */
export function useIntersection(roadmapIds: string[], enabled = true) {
  return useQuery({
    queryKey: keys.intersection(roadmapIds),
    enabled,
    queryFn: async () => {
      const params = roadmapIds.length
        ? { params: { roadmapIds: roadmapIds.join(',') } }
        : undefined;
      const res = await apiClient.get('/roadmaps/intersections', params);
      return res.data.data as RoadmapIntersection;
    },
    retry: false,
  });
}

export function useAdoptRoadmap(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { status?: string; targetDate?: string | null }) =>
      apiPut<RoadmapAdoption>(`/roadmaps/${id}/adoption`, input),
    onSuccess: () => invalidate(qc, id),
  });
}

export function useAbandonRoadmap(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiDelete(`/roadmaps/${id}/adoption`),
    onSuccess: () => invalidate(qc, id),
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>, id: string) {
  qc.invalidateQueries({ queryKey: keys.all });
  qc.invalidateQueries({ queryKey: keys.detail(id) });
  qc.invalidateQueries({ queryKey: ['roadmaps', 'intersection'] });
}
