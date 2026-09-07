import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type Tone = 'neutral' | 'cyan' | 'violet' | 'success' | 'warning' | 'danger';

const tones: Record<Tone, string> = {
  neutral: 'border-[var(--border)] text-[var(--text-secondary)]',
  cyan: 'border-[color:rgba(79,209,229,0.4)] text-[var(--accent-cyan)]',
  violet: 'border-[color:rgba(139,124,246,0.4)] text-[var(--accent-violet)]',
  success: 'border-[color:rgba(61,214,140,0.4)] text-[var(--color-success)]',
  warning: 'border-[color:rgba(245,166,35,0.4)] text-[var(--color-warning)]',
  danger: 'border-[color:rgba(242,84,91,0.4)] text-[var(--color-danger)]',
};

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-pill border px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.1em]',
        tones[tone],
        className
      )}
      {...props}
    />
  );
}

const STATUS_TONES: Record<string, Tone> = {
  active: 'cyan',
  in_progress: 'cyan',
  completed: 'success',
  done: 'success',
  archived: 'neutral',
  pending: 'neutral',
  todo: 'neutral',
  blocked: 'warning',
  skipped: 'neutral',
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={STATUS_TONES[status] ?? 'neutral'}>{status.replace('_', ' ')}</Badge>;
}
