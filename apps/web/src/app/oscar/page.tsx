'use client';

import { useState } from 'react';
import { MessageSquarePlus, Trash2 } from 'lucide-react';
import { useConversations, useDeleteConversation, useOscarStatus } from '@/features/oscar/api';
import { OscarChat } from '@/features/oscar/OscarChat';
import { OscarUnavailable } from '@/features/oscar/OscarUnavailable';
import { Button } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/States';
import { cn } from '@/lib/utils';

export default function OscarPage() {
  const status = useOscarStatus();
  const conversations = useConversations();
  const del = useDeleteConversation();
  const [activeId, setActiveId] = useState<string | null>(null);

  if (status.isLoading) return <LoadingState />;
  if (status.data && !status.data.enabled) {
    return (
      <div className="animate-fade-in">
        <Header />
        <OscarUnavailable />
      </div>
    );
  }

  const list = conversations.data ?? [];

  return (
    <div className="animate-fade-in">
      <Header />
      <div className="grid gap-4 lg:grid-cols-[220px_1fr]">
        <aside className="hidden lg:block">
          <Button
            variant="secondary"
            size="sm"
            className="mb-3 w-full"
            onClick={() => setActiveId(null)}
          >
            <MessageSquarePlus size={14} /> New chat
          </Button>
          <ul className="space-y-1">
            {list.map((c) => (
              <li key={c.id} className="group flex items-center gap-1">
                <button
                  onClick={() => setActiveId(c.id)}
                  className={cn(
                    'min-w-0 flex-1 truncate rounded-button px-2.5 py-1.5 text-left text-xs',
                    activeId === c.id
                      ? 'bg-[var(--bg-hover)] text-[var(--text-primary)]'
                      : 'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'
                  )}
                >
                  {c.title ?? 'New conversation'}
                </button>
                <button
                  aria-label="Delete conversation"
                  onClick={() => {
                    del.mutate(c.id);
                    if (activeId === c.id) setActiveId(null);
                  }}
                  className="opacity-0 transition-opacity group-hover:opacity-100"
                >
                  <Trash2
                    size={13}
                    className="text-[var(--text-secondary)] hover:text-[var(--color-danger)]"
                  />
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <div className="min-h-[60vh] rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-4 lg:h-[calc(100vh-13rem)]">
          <OscarChat
            key={activeId ?? 'new'}
            conversationId={activeId}
            onConversationCreated={setActiveId}
          />
        </div>
      </div>
    </div>
  );
}

function Header() {
  return (
    <header className="mb-5">
      <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-[var(--text-secondary)]">
        OSCAR
      </p>
      <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Ask OSCAR</h1>
      <p className="text-sm text-[var(--text-secondary)]">
        Operational guidance grounded in your ORBIT data. OSCAR proposes; you confirm.
      </p>
    </header>
  );
}
