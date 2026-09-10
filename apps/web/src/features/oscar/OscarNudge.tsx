'use client';

import { useState } from 'react';
import { Brain, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useNudge } from './api';

/**
 * A contextual OSCAR card (ADR-034). Silent by default — renders nothing unless
 * the endpoint returns {@code status: "ready"}. Dismissal is remembered per
 * surface for the rest of the day.
 */
export function OscarNudge({
  surface,
  objectiveId,
}: {
  surface: 'today' | 'objective';
  objectiveId?: string;
}) {
  const { data } = useNudge(surface, objectiveId);
  const dismissKey = `oscar:nudge:dismissed:${surface}:${objectiveId ?? 'today'}:${new Date()
    .toISOString()
    .slice(0, 10)}`;
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(dismissKey) === '1';
    } catch {
      return false;
    }
  });
  const [showWhy, setShowWhy] = useState(false);

  if (dismissed || !data || data.status !== 'ready' || !data.card) return null;
  const c = data.card;

  function dismiss() {
    try {
      localStorage.setItem(dismissKey, '1');
    } catch {
      /* private mode — dismissal is best-effort */
    }
    setDismissed(true);
  }

  return (
    <div className="rounded-card border border-[color:rgba(139,124,246,0.4)] bg-[var(--bg-surface)] p-3.5">
      <div className="flex items-start gap-3">
        <Brain size={16} className="mt-0.5 shrink-0 text-[var(--accent-violet)]" />
        <div className="min-w-0 flex-1">
          <p className="font-mono text-[10px] uppercase tracking-[0.12em] text-[var(--accent-violet)]">
            OSCAR
          </p>
          <p className="mt-0.5 text-sm font-medium text-[var(--text-primary)]">{c.headline}</p>
          {c.body && <p className="mt-0.5 text-xs text-[var(--text-secondary)]">{c.body}</p>}
          {showWhy && c.why && (
            <p className="mt-1.5 rounded-button bg-[var(--bg-base)] p-2 text-[11px] italic text-[var(--text-secondary)]">
              {c.why}
            </p>
          )}
          <div className="mt-2 flex gap-1.5">
            {c.why && (
              <Button size="sm" variant="ghost" onClick={() => setShowWhy((v) => !v)}>
                Explain
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={dismiss}>
              <X size={12} /> Dismiss
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
