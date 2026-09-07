'use client';

import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import type { PriorityReason } from '@/lib/api-types';
import { cn } from '@/lib/utils';

const SOURCE_LABEL: Record<string, string> = {
  computed: 'computed',
  proxy: 'proxy',
  'neutral-default': 'not yet available',
};

/**
 * Renders the Priority Engine's explanation (ADR-014 §4, PRD §6 "must be
 * explainable to the user"). Nothing here is generated text — it is the exact
 * factor breakdown the API returned.
 */
export function WhyThis({ reason }: { reason: PriorityReason | null }) {
  const [open, setOpen] = useState(false);
  if (!reason) return null;

  const factors = Object.entries(reason.factors).sort(
    ([, a], [, b]) => b.contribution - a.contribution
  );

  return (
    <div className="rounded-button border border-[var(--border)] text-xs">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between px-3 py-2 text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
        aria-expanded={open}
      >
        <span>
          Why this? · score{' '}
          <span className="font-mono text-[var(--text-primary)]">{reason.score.toFixed(2)}</span>
        </span>
        <ChevronDown size={14} className={cn('transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <ul className="space-y-1.5 border-t border-[var(--border)] px-3 py-2.5">
          {factors.map(([name, f]) => (
            <li key={name} className="flex items-baseline justify-between gap-3">
              <span className="text-[var(--text-primary)]">
                {name.replace(/([A-Z])/g, ' $1').toLowerCase()}
                <span className="ml-1.5 text-[10px] text-[var(--text-secondary)]">
                  {SOURCE_LABEL[f.source] ?? f.source}
                </span>
                <span className="block text-[10px] text-[var(--text-secondary)]">{f.detail}</span>
              </span>
              <span className="shrink-0 font-mono text-[var(--text-secondary)]">
                {f.value.toFixed(2)} × {f.weight.toFixed(2)} ={' '}
                <span className="text-[var(--accent-cyan)]">{f.contribution.toFixed(3)}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
