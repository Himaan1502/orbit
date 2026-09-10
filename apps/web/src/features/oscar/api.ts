'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/http';
import type {
  OscarConversation,
  OscarConversationDetail,
  OscarNudge,
  OscarProposedAction,
  OscarResponse,
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

export interface StreamHandlers {
  status?: (text: string) => void;
  token: (delta: string) => void;
  proposedAction?: (action: OscarProposedAction) => void;
  meta?: (response: OscarResponse) => void;
  done?: (persisted: boolean, messageId: string) => void;
  error?: (recoverable: boolean, note: string) => void;
}

/**
 * Stream a turn over SSE (ADR-033). Native `fetch` + a small line parser — no
 * `EventSource` (can't POST), no dependency. Resolves when the stream closes.
 * A failure before the first token → the caller falls back to {@link useSendTurn}.
 */
export async function streamOscarTurn(
  conversationId: string,
  message: string,
  on: StreamHandlers
): Promise<{ streamedAnyToken: boolean }> {
  let streamedAnyToken = false;
  let res: Response;
  try {
    res = await fetch(`/api/orbit/oscar/conversations/${conversationId}/turns/stream`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        accept: 'text/event-stream',
        'x-request-id': crypto.randomUUID(),
      },
      body: JSON.stringify({ message }),
    });
  } catch {
    on.error?.(true, 'OSCAR is temporarily unavailable.');
    return { streamedAnyToken };
  }
  if (!res.ok || !res.body) {
    on.error?.(true, 'OSCAR is temporarily unavailable.');
    return { streamedAnyToken };
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  let event = '';

  const dispatch = (name: string, payload: string) => {
    let data: Record<string, unknown>;
    try {
      data = JSON.parse(payload);
    } catch {
      return;
    }
    switch (name) {
      case 'status':
        on.status?.(String(data.text ?? ''));
        break;
      case 'token':
        streamedAnyToken = true;
        on.token(String(data.text ?? ''));
        break;
      case 'proposedAction':
        on.proposedAction?.(data as unknown as OscarProposedAction);
        break;
      case 'meta':
        on.meta?.(data as unknown as OscarResponse);
        break;
      case 'done':
        on.done?.(Boolean(data.persisted), String(data.messageId ?? ''));
        break;
      case 'error':
        on.error?.(Boolean(data.recoverable), String(data.note ?? ''));
        break;
    }
  };

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let nl: number;
    while ((nl = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, nl).replace(/\r$/, '');
      buf = buf.slice(nl + 1);
      if (line === '') {
        event = '';
      } else if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dispatch(event, line.slice(5).trim());
      }
    }
  }
  return { streamedAnyToken };
}

export function useNudge(surface: 'today' | 'objective', objectiveId?: string) {
  const params = new URLSearchParams({ surface });
  if (objectiveId) params.set('objectiveId', objectiveId);
  return useQuery({
    queryKey: ['oscar', 'nudge', surface, objectiveId ?? ''],
    queryFn: () => apiGet<OscarNudge>(`/oscar/nudge?${params.toString()}`),
    staleTime: 30 * 60 * 1000,
    retry: false,
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
