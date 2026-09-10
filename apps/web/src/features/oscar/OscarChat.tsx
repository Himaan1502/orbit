'use client';

import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2, SendHorizonal } from 'lucide-react';
import type { OscarMessageView, OscarProposedAction, OscarResponse } from '@/lib/api-types';
import { Button } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/States';
import { streamOscarTurn, useConversation, useCreateConversation, useSendTurn } from './api';
import { RecommendationView } from './RecommendationView';
import { ProposedActionCard } from './ProposedActionCard';
import { OscarUnavailable } from './OscarUnavailable';

interface Props {
  conversationId: string | null;
  onConversationCreated: (id: string) => void;
}

export function OscarChat({ conversationId, onConversationCreated }: Props) {
  const qc = useQueryClient();
  const detail = useConversation(conversationId);
  const create = useCreateConversation();
  const send = useSendTurn(conversationId ?? 'pending');

  const [input, setInput] = useState('');
  const [pendingUserText, setPendingUserText] = useState<string | null>(null);
  const [lastResponse, setLastResponse] = useState<OscarResponse | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [liveText, setLiveText] = useState('');
  const [liveStatus, setLiveStatus] = useState('');
  const [liveActions, setLiveActions] = useState<OscarProposedAction[]>([]);
  const [note, setNote] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const busy = streaming || create.isPending || send.isPending;
  const messages: OscarMessageView[] = conversationId ? (detail.data?.messages ?? []) : [];

  useEffect(() => {
    setLastResponse(null);
    setPendingUserText(null);
    setInput('');
    setLiveText('');
    setLiveStatus('');
    setLiveActions([]);
    setNote(null);
  }, [conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, pendingUserText, liveText, liveStatus, lastResponse]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || busy) return;
    setInput('');
    setPendingUserText(text);
    setLastResponse(null);
    setLiveText('');
    setLiveStatus('');
    setLiveActions([]);
    setNote(null);
    setStreaming(true);

    let convId = conversationId;
    try {
      if (!convId) {
        const created = await create.mutateAsync(undefined);
        convId = created.conversation.id;
        onConversationCreated(convId);
      }
    } catch {
      setStreaming(false);
      setPendingUserText(null);
      setNote('OSCAR is temporarily unavailable.');
      return;
    }

    const targetId = convId;
    const { streamedAnyToken } = await streamOscarTurn(targetId, text, {
      status: setLiveStatus,
      token: (d) => setLiveText((prev) => prev + d),
      proposedAction: (a) => setLiveActions((prev) => [...prev, a]),
      meta: setLastResponse,
      error: (_recoverable, n) => setNote(n),
      done: async (persisted) => {
        setLiveStatus('');
        if (persisted) {
          await qc.invalidateQueries({ queryKey: ['oscar', 'conversation', targetId] });
          await qc.invalidateQueries({ queryKey: ['oscar', 'conversations'] });
          setLiveText('');
          setLiveActions([]);
        }
        setStreaming(false);
        setPendingUserText(null);
      },
    });

    // stream failed before any token → fall back to the non-streaming turn
    if (!streamedAnyToken) {
      setLiveStatus('');
      send.mutate(text, {
        onSuccess: (r) => {
          setLastResponse(r.response);
          setStreaming(false);
          setPendingUserText(null);
          qc.invalidateQueries({ queryKey: ['oscar', 'conversation', targetId] });
        },
        onError: () => {
          setStreaming(false);
          setPendingUserText(null);
          setNote('OSCAR is temporarily unavailable.');
        },
      });
    }
  }

  if (conversationId && detail.isLoading) return <LoadingState label="Loading conversation…" />;

  const showRich = lastResponse && lastResponse.kind !== 'unavailable';

  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
        {messages.length === 0 && !pendingUserText && !liveText && !note && (
          <div className="py-10 text-center">
            <p className="text-sm text-[var(--text-primary)]">Ask OSCAR about your work.</p>
            <p className="mt-1 text-xs text-[var(--text-secondary)]">
              &ldquo;What should I focus on today?&rdquo; · &ldquo;Where am I weakest for my
              target?&rdquo;
            </p>
          </div>
        )}

        {messages.map((m, i) => {
          const isLastAssistant = m.role === 'assistant' && i === messages.length - 1;
          return (
            <Bubble key={m.id} role={m.role} text={m.content}>
              {isLastAssistant && showRich && !liveText && (
                <>
                  <RecommendationView response={lastResponse} />
                  {lastResponse.proposedActions?.map((a, idx) => (
                    <ProposedActionCard key={idx} action={a} />
                  ))}
                </>
              )}
            </Bubble>
          );
        })}

        {pendingUserText && <Bubble role="user" text={pendingUserText} />}

        {liveStatus && (
          <div className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">
            <Loader2 size={13} className="animate-spin" /> {liveStatus}
          </div>
        )}

        {liveText && (
          <Bubble role="assistant" text={liveText + (streaming ? '▍' : '')}>
            {liveActions.map((a, idx) => (
              <ProposedActionCard key={idx} action={a} />
            ))}
            {showRich && !streaming && <RecommendationView response={lastResponse} />}
          </Bubble>
        )}

        {streaming && !liveText && !liveStatus && (
          <div className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">
            <Loader2 size={13} className="animate-spin" /> OSCAR is thinking…
          </div>
        )}

        {note && <p className="text-xs text-[var(--color-warning)]">{note}</p>}
        {lastResponse?.kind === 'unavailable' && !busy && <OscarUnavailable compact />}

        <div ref={bottomRef} />
      </div>

      <form
        onSubmit={submit}
        className="mt-3 flex items-end gap-2 border-t border-[var(--border)] pt-3"
      >
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) submit(e);
          }}
          rows={2}
          placeholder="Ask OSCAR…"
          className="min-h-[2.5rem] w-full resize-none rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-secondary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
        />
        <Button type="submit" disabled={busy || !input.trim()}>
          <SendHorizonal size={15} />
        </Button>
      </form>
    </div>
  );
}

function Bubble({
  role,
  text,
  children,
}: {
  role: string;
  text: string;
  children?: React.ReactNode;
}) {
  const isUser = role === 'user';
  return (
    <div className={isUser ? 'flex justify-end' : ''}>
      <div className={isUser ? 'max-w-[85%]' : 'w-full'}>
        <div
          className={
            isUser
              ? 'rounded-card bg-[var(--bg-surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)]'
              : 'whitespace-pre-wrap text-sm text-[var(--text-primary)]'
          }
        >
          {text}
        </div>
        {children}
      </div>
    </div>
  );
}
