import type { LucideIcon } from 'lucide-react';

interface PlaceholderPageProps {
  title: string;
  icon: LucideIcon;
  milestone: number;
}

export function PlaceholderPage({ title, icon: Icon, milestone }: PlaceholderPageProps) {
  return (
    <div className="flex animate-fade-in flex-col items-center justify-center py-20">
      <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-card border border-[var(--border)] bg-[var(--bg-surface)]">
        <Icon size={28} className="text-[var(--text-secondary)]" />
      </div>
      <h1 className="mb-2 text-2xl font-semibold text-[var(--text-primary)]">{title}</h1>
      <p className="text-sm text-[var(--text-secondary)]">Coming in Milestone {milestone}</p>
    </div>
  );
}
