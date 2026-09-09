'use client';

import type { OscarResponse } from '@/lib/api-types';
import { Badge } from '@/components/ui/Badge';

const EVIDENCE_TONE: Record<string, 'cyan' | 'violet' | 'neutral'> = {
  fact: 'neutral',
  metric: 'cyan',
  inference: 'violet',
};

/**
 * UX §25.2 recommendation layout — Action / Why this? / Impact / Evidence, with
 * the §25.3 fact / metric / inference chips. Nothing here is generated at render
 * time; it is exactly what OSCAR returned.
 */
export function RecommendationView({ response }: { response: OscarResponse }) {
  const s = response.sections;
  if (!s) return null;

  return (
    <div className="mt-3 space-y-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-4">
      <p className="font-mono text-[10px] uppercase tracking-[0.14em] text-[var(--accent-violet)]">
        Recommendation
      </p>

      {s.action && (
        <div>
          <p className="text-[11px] text-[var(--text-secondary)]">Action</p>
          <p className="text-sm text-[var(--text-primary)]">{s.action}</p>
        </div>
      )}
      {s.why && (
        <div>
          <p className="text-[11px] text-[var(--text-secondary)]">Why this?</p>
          <p className="text-sm text-[var(--text-primary)]">{s.why}</p>
        </div>
      )}
      {s.impact && (
        <div>
          <p className="text-[11px] text-[var(--text-secondary)]">Impact</p>
          <p className="text-sm text-[var(--text-primary)]">{s.impact}</p>
        </div>
      )}
      {s.evidence && s.evidence.length > 0 && (
        <div>
          <p className="mb-1 text-[11px] text-[var(--text-secondary)]">Evidence</p>
          <ul className="space-y-1.5">
            {s.evidence.map((e, i) => (
              <li key={i} className="flex items-start gap-2 text-xs text-[var(--text-primary)]">
                <Badge tone={EVIDENCE_TONE[e.kind] ?? 'neutral'}>{e.kind}</Badge>
                <span>
                  {e.label ? (
                    <span className="text-[var(--text-secondary)]">{e.label}: </span>
                  ) : null}
                  {e.value}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {response.citations && response.citations.length > 0 && (
        <p className="pt-1 text-[10px] text-[var(--text-secondary)]">
          Based on: {response.citations.join(', ')}
        </p>
      )}
    </div>
  );
}
