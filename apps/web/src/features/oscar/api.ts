'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/http';
import type {
  OscarConversation,
  OscarConversationDetail,
  OscarProposedAction,
  OscarStatus,
  OscarTurnResponse,
} from '@/lib/api-types';

const keys = {
  status: ['oscar', 'status'] as const,
  conversations: ['oscar', 'conversations'] as const,
  conversation: (id: string) => ['oscar', 'conversation', id] as const,
};

export function useOscarStatus() {
  return useQuery({
    queryKey: keys.status,
    queryFn: () => apiGet<OscarStatus>('/oscar/status'),
    staleTime: 10 * 60 * 1000,
  });
}

export function useConversations() {
  return useQuery({
    queryKey: keys.conversations,
    queryFn: () => apiGet<OscarConversation[]>('/oscar/conversations'),
  });
}

export function useConversation(id: string | null) {
  return useQuery({
    queryKey: keys.conversation(id ?? 'none'),
    enabled: !!id,
    queryFn: () => apiGet<OscarConversationDetail>(`/oscar/conversations/${id}`),
  });
}

export function useCreateConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (message?: string) =>
      apiPost<OscarTurnResponse>('/oscar/conversations', message ? { message } : {}),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: keys.conversations });
      qc.invalidateQueries({ queryKey: keys.conversation(r.conversation.id) });
    },
  });
}

export function useSendTurn(conversationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (message: string) =>
      apiPost<OscarTurnResponse>(`/oscar/conversations/${conversationId}/turns`, { message }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.conversation(conversationId) });
      qc.invalidateQueries({ queryKey: keys.conversations });
    },
  });
}

export function useRenameConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      apiPatch<OscarConversation>(`/oscar/conversations/${id}`, { title }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.conversations }),
  });
}

export function useDeleteConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiDelete(`/oscar/conversations/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.conversations }),
  });
}

/**
 * Execute a proposed action by calling the EXISTING domain endpoint directly
 * (ADR-032 §4 — "Start" routes to the same API a manual action would). OSCAR
 * never performs this; the user's click does.
 */
export function useRunProposedAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (action: OscarProposedAction) => {
      const url = action.call.path.replace(/^\/api\/v1/, '');
      const res = await apiClient.request({
        method: action.call.method,
        url,
        data: action.call.method === 'GET' ? undefined : action.call.body,
      });
      return res.data?.data;
    },
    onSuccess: () => {
      // the action may have created/changed a task, objective, project or evidence
      qc.invalidateQueries();
    },
  });
}
