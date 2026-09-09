'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPatch, apiPost } from '@/lib/http';
import type { DailyPlan, Task } from '@/lib/api-types';

const keys = {
  tasks: ['tasks'] as const,
  tasksForObjective: (objectiveId: string) => ['tasks', 'objective', objectiveId] as const,
  plan: (date: string) => ['daily-plans', date] as const,
};

export function useTasks() {
  return useQuery({ queryKey: keys.tasks, queryFn: () => apiGet<Task[]>('/tasks') });
}

export interface CreateTaskInput {
  objectiveId: string;
  milestoneId?: string | null;
  projectId?: string | null;
  skillId?: string | null;
  title: string;
  description?: string;
  estimatedMinutes?: number | null;
  dueAt?: string | null;
}

export function useCreateTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateTaskInput) => apiPost<Task>('/tasks', input),
    onSuccess: (_t, input) => {
      invalidateExecution(qc, input.objectiveId);
      if (input.projectId) qc.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useCompleteTask(objectiveId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => apiPost<Task>(`/tasks/${taskId}/complete`),
    onSuccess: () => invalidateExecution(qc, objectiveId),
  });
}

export interface UpdateTaskInput {
  title?: string;
  description?: string;
  estimatedMinutes?: number | null;
  dueAt?: string | null;
  milestoneId?: string | null;
  skillId?: string | null;
  projectId?: string | null;
  status?: string;
  /** Explicitly detach the field — the API can't clear an FK from a null in a PATCH. */
  clearMilestone?: boolean;
  clearSkill?: boolean;
  clearProject?: boolean;
}

export function useUpdateTask(objectiveId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: { id: string } & UpdateTaskInput) =>
      apiPatch<Task>(`/tasks/${id}`, input),
    onSuccess: () => {
      invalidateExecution(qc, objectiveId);
      qc.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

/** The plan for a date. `enabled: false` until you want it (avoids a 404 spam on first load). */
export function useDailyPlan(date: string) {
  return useQuery({
    queryKey: keys.plan(date),
    queryFn: async () => {
      try {
        return await apiGet<DailyPlan>(`/daily-plans/${date}`);
      } catch (e: unknown) {
        if (isNotFound(e)) return null;
        throw e;
      }
    },
  });
}

export function useGeneratePlan(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (availableMinutes?: number) =>
      apiPost<DailyPlan>('/daily-plans/generate', { date, availableMinutes, force: true }),
    onSuccess: (plan) => qc.setQueryData(keys.plan(date), plan),
  });
}

export function useRegeneratePlan(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (planId: string) => apiPost<DailyPlan>(`/daily-plans/${planId}/regenerate`),
    onSuccess: (plan) => qc.setQueryData(keys.plan(date), plan),
  });
}

function invalidateExecution(qc: ReturnType<typeof useQueryClient>, objectiveId?: string) {
  qc.invalidateQueries({ queryKey: keys.tasks });
  qc.invalidateQueries({ queryKey: ['daily-plans'] });
  qc.invalidateQueries({ queryKey: ['dashboard'] });
  qc.invalidateQueries({ queryKey: ['objectives'] });
  if (objectiveId) {
    qc.invalidateQueries({ queryKey: ['objectives', objectiveId] });
  }
}

function isNotFound(e: unknown): boolean {
  return (
    typeof e === 'object' &&
    e !== null &&
    'response' in e &&
    (e as { response?: { status?: number } }).response?.status === 404
  );
}
