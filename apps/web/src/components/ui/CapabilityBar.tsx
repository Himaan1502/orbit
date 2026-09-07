import { cn } from '@/lib/utils';

interface CapabilityBarProps {
  /** 0..1, or null when the skill has never been self-assessed. */
  value: number | null;
  className?: string;
}

/**
 * Horizontal capability meter for a skill (Design System §4 — hairline track,
 * cyan fill). An un-assessed skill reads as an explicit "not assessed", never a
 * silent zero (ADR-018 §3).
 */
export function CapabilityBar({ value, className }: CapabilityBarProps) {
  if (value == null) {
    return (
      <span className={cn('font-mono text-[11px] text-[var(--text-secondary)]', className)}>
        not assessed
      </span>
    );
  }
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <div
        className="h-1.5 w-full overflow-hidden rounded-pill bg-[var(--border)]"
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className="h-full rounded-pill bg-[var(--accent-cyan)] transition-[width] duration-500 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="shrink-0 font-mono text-[11px] text-[var(--text-secondary)]">{pct}</span>
    </div>
  );
}
