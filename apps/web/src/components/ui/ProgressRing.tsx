import { cn } from '@/lib/utils';

interface ProgressRingProps {
  /** 0..1 */
  value: number;
  size?: number;
  strokeWidth?: number;
  label?: string;
  className?: string;
}

/**
 * Radial progress arc — the design system's signature "orbital instrument" shape
 * (Design System §3B, §4). Cyan arc on a hairline track, centre label in mono.
 */
export function ProgressRing({
  value,
  size = 96,
  strokeWidth = 6,
  label,
  className,
}: ProgressRingProps) {
  const clamped = Math.max(0, Math.min(1, value));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const dash = circumference * clamped;
  const pct = Math.round(clamped * 100);

  return (
    <div
      className={cn('relative inline-flex items-center justify-center', className)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={label ?? `${pct}% complete`}
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--border)"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--accent-cyan)"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference - dash}`}
          className="transition-[stroke-dasharray] duration-500 ease-out"
        />
      </svg>
      <span className="absolute font-mono text-sm font-medium text-[var(--text-primary)]">
        {pct}
        <span className="text-[var(--text-secondary)]">%</span>
      </span>
    </div>
  );
}
