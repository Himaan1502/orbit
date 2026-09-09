'use client';

import { useEffect, useRef, useState } from 'react';
import { Loader2, SendHorizonal } from 'lucide-react';
import type { OscarMessageView, OscarResponse } from '@/lib/api-types';
import { Button } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/States';
import { useConversation, useCreateConversation, useSendTurn } from './api';
import { RecommendationView } from './RecommendationView';
import { ProposedActionCard } from './ProposedActionCard';
import { OscarUnavailable } from './OscarUnavailable';

interface Props {
  conversationId: string | null;
  onConversationCreated: (id: string) => void;
}

export function OscarChat({ conversationId, onConversationCreated }: Props) {
  const detail = useConversation(conversationId);
  const create = useCreateConversation();
  const send = useSendTurn(conversationId ?? 'pending');

  const [input, setInput] = useState('');
  const [pendingUserText, setPendingUserText] = useState<string | null>(null);
  const [lastResponse, setLastResponse] = useState<OscarResponse | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const busy = create.isPending || send.isPending;
  const messages: OscarMessageView[] = conversationId ? (detail.data?.messages ?? []) : [];

  useEffect(() => {
    setLastResponse(null);
    setPendingUserText(null);
    setInput('');
  }, [conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, pendingUserText, lastResponse]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || busy) return;
    setInput('');
    setPendingUserText(text);
    setLastResponse(null);

    const onDone = (r: { conversation: { id: string }; response: OscarResponse | null }) => {
      setPendingUserText(null);
      setLastResponse(r.response);
    };

    if (!conversationId) {
      create.mutate(text, {
        onSuccess: (r) => {
          onConversationCreated(r.conversation.id);
          onDone(r);
        },
        onError: () => setPendingUserText(null),
      });
    } else {
      send.mutate(text, { onSuccess: onDone, onError: () => setPendingUserText(null) });
    }
  }

  if (conversationId && detail.isLoading) return <LoadingState label="Loading conversation…" />;

  const showRichForLast = lastResponse && lastResponse.kind !== 'unavailable';

  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
        {messages.length === 0 && !pendingUserText && !lastResponse && (
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
              {isLastAssistant && showRichForLast && (
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

        {busy && (
          <div className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">
            <Loader2 size={14} className="animate-spin" /> OSCAR is thinking…
          </div>
        )}

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
