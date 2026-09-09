'use client';

import { useState } from 'react';
import { Check, Play, X } from 'lucide-react';
import type { OscarProposedAction } from '@/lib/api-types';
import { Button } from '@/components/ui/Button';
import { useRunProposedAction } from './api';

type State = 'idle' | 'running' | 'done' | 'dismissed' | 'error';

/**
 * A UX §25.2 confirmation card for a state change OSCAR proposed. "Start" calls
 * the existing domain endpoint (ADR-032 §4) — OSCAR itself never writes. The
 * confirmation shown after is local to this view.
 */
export function ProposedActionCard({ action }: { action: OscarProposedAction }) {
  const [state, setState] = useState<State>('idle');
  const [showWhy, setShowWhy] = useState(false);
  const run = useRunProposedAction();

  if (state === 'dismissed') return null;

  return (
    <div className="mt-2 rounded-card border border-[color:rgba(139,124,246,0.4)] bg-[var(--bg-surface)] p-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[0.12em] text-[var(--accent-violet)]">
            Proposed · {action.tool.replace(/_/g, ' ')}
          </p>
          <p className="mt-0.5 text-sm text-[var(--text-primary)]">{action.description}</p>
          <p className="mt-0.5 font-mono text-[10px] text-[var(--text-secondary)]">
            {action.call.method} {action.call.path}
          </p>
        </div>
      </div>

      {showWhy && (
        <p className="mt-2 rounded-button bg-[var(--bg-base)] p-2 text-xs text-[var(--text-secondary)]">
          {action.reasoning}
        </p>
      )}

      {state === 'done' ? (
        <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-[var(--color-success)]">
          <Check size={13} /> Done — applied through the normal ORBIT endpoint.
        </p>
      ) : state === 'error' ? (
        <p className="mt-2 text-xs text-[var(--color-danger)]">
          That didn’t go through. You can do it manually from the relevant page.
        </p>
      ) : (
        <div className="mt-2.5 flex gap-2">
          <Button
            size="sm"
            disabled={state === 'running'}
            onClick={() => {
              setState('running');
              run.mutate(action, {
                onSuccess: () => setState('done'),
                onError: () => setState('error'),
              });
            }}
          >
            <Play size={13} /> {state === 'running' ? 'Starting…' : 'Start'}
          </Button>
          <Button size="sm" variant="secondary" onClick={() => setShowWhy((v) => !v)}>
            Explain
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setState('dismissed')}>
            <X size={13} /> Dismiss
          </Button>
        </div>
      )}
    </div>
  );
}
