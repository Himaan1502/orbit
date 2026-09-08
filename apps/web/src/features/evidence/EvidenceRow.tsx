'use client';

import Link from 'next/link';
import { FileCheck } from 'lucide-react';
import type { Evidence } from '@/lib/api-types';
import { Badge } from '@/components/ui/Badge';
import { formatDate } from '@/lib/format';

/** Compact evidence row — used in the library, on a project, and on a skill. */
export function EvidenceRow({ evidence: e }: { evidence: Evidence }) {
  return (
    <li>
      <Link
        href={`/evidence/${e.id}`}
        className="flex items-start gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3 transition-colors hover:border-[var(--card-hover-border)]"
      >
        <FileCheck size={15} className="mt-0.5 shrink-0 text-[var(--text-secondary)]" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm text-[var(--text-primary)]">{e.title}</p>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <Badge tone="neutral">{e.type}</Badge>
            {e.verificationStatus !== 'self_attested' && (
              <Badge tone="cyan">{e.verificationStatus.replace('_', ' ')}</Badge>
            )}
            {e.skills.length > 0 && (
              <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                {e.skills.length} skill{e.skills.length === 1 ? '' : 's'}
              </span>
            )}
            {e.projects.length > 0 && (
              <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                {e.projects.length} project{e.projects.length === 1 ? '' : 's'}
              </span>
            )}
            {e.occurredAt && (
              <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                {formatDate(e.occurredAt)}
              </span>
            )}
          </div>
        </div>
      </Link>
    </li>
  );
}
