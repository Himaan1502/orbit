'use client';

import { type ReactNode } from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

/**
 * Underline tabs on {@link https://www.radix-ui.com/primitives/docs/components/tabs Radix Tabs}
 * (ADR-030) — arrow-key navigation, `role="tablist"` / `tabpanel`, and
 * `aria-controls` wiring for free. Deep-Space styling is ours.
 */
export function Tabs({
  value,
  onValueChange,
  children,
}: {
  value: string;
  onValueChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <RadixTabs.Root value={value} onValueChange={onValueChange}>
      {children}
    </RadixTabs.Root>
  );
}

export function TabsList({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <RadixTabs.List className={cn('mb-6 flex gap-1 border-b border-[var(--border)]', className)}>
      {children}
    </RadixTabs.List>
  );
}

export function TabsTrigger({ value, children }: { value: string; children: ReactNode }) {
  return (
    <RadixTabs.Trigger
      value={value}
      className={cn(
        '-mb-px border-b-2 border-transparent px-3 py-2 text-sm text-[var(--text-secondary)] transition-colors',
        'hover:text-[var(--text-primary)]',
        'data-[state=active]:border-[var(--accent-violet)] data-[state=active]:text-[var(--text-primary)]',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent-cyan)]'
      )}
    >
      {children}
    </RadixTabs.Trigger>
  );
}

export function TabsContent({ value, children }: { value: string; children: ReactNode }) {
  return (
    <RadixTabs.Content value={value} className="focus-visible:outline-none">
      {children}
    </RadixTabs.Content>
  );
}
