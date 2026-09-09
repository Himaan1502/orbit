'use client';

import Link from 'next/link';
import { CloudOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';

/**
 * UX §25.5 — OSCAR is off (no provider key) or the providers all failed this
 * turn. ORBIT itself is unaffected.
 */
export function OscarUnavailable({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return (
      <div className="rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3 text-xs text-[var(--text-secondary)]">
        OSCAR is temporarily unavailable. Your tasks, objectives, roadmaps and analytics remain
        available.
      </div>
    );
  }
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-card border border-[var(--border)] bg-[var(--bg-surface)]">
        <CloudOff size={24} className="text-[var(--text-secondary)]" />
      </div>
      <div>
        <p className="text-sm font-medium text-[var(--text-primary)]">
          OSCAR is temporarily unavailable
        </p>
        <p className="mt-1 text-xs text-[var(--text-secondary)]">
          Your tasks, objectives, roadmaps and analytics remain available.
        </p>
      </div>
      <Link href="/">
        <Button variant="secondary" size="sm">
          Continue without OSCAR
        </Button>
      </Link>
    </div>
  );
}
