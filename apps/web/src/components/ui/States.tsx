import type { LucideIcon } from 'lucide-react';
import { Loader2 } from 'lucide-react';

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      className="flex items-center justify-center gap-2 py-16 text-sm text-[var(--text-secondary)]"
      role="status"
    >
      <Loader2 size={16} className="animate-spin" />
      {label}
    </div>
  );
}

export function ErrorState({ message = 'Something went wrong.' }: { message?: string }) {
  return (
    <div className="py-16 text-center text-sm text-[var(--color-danger)]" role="alert">
      {message}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  hint,
  action,
}: {
  icon: LucideIcon;
  title: string;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-card border border-[var(--border)] bg-[var(--bg-surface)]">
        <Icon size={24} className="text-[var(--text-secondary)]" />
      </div>
      <div>
        <p className="text-sm font-medium text-[var(--text-primary)]">{title}</p>
        {hint && <p className="mt-1 text-xs text-[var(--text-secondary)]">{hint}</p>}
      </div>
      {action}
    </div>
  );
}
