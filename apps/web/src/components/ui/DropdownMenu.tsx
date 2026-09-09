'use client';

import { type ReactNode } from 'react';
import * as Radix from '@radix-ui/react-dropdown-menu';
import { cn } from '@/lib/utils';

/**
 * Dropdown menu on {@link https://www.radix-ui.com/primitives/docs/components/dropdown-menu Radix DropdownMenu}
 * (ADR-030) — arrow-key navigation, typeahead, focus management and
 * `role="menu"` for free. Deep-Space styling is ours.
 */
export const DropdownMenu = Radix.Root;

export function DropdownMenuTrigger({ children }: { children: ReactNode }) {
  return <Radix.Trigger asChild>{children}</Radix.Trigger>;
}

export function DropdownMenuContent({
  children,
  align = 'end',
}: {
  children: ReactNode;
  align?: 'start' | 'center' | 'end';
}) {
  return (
    <Radix.Portal>
      <Radix.Content
        align={align}
        sideOffset={6}
        className="z-50 min-w-[10rem] animate-slide-up rounded-card border border-[var(--border)] bg-[var(--bg-surface-raised)] p-1 shadow-[var(--shadow-overlay)]"
      >
        {children}
      </Radix.Content>
    </Radix.Portal>
  );
}

export function DropdownMenuItem({
  children,
  onSelect,
  disabled,
}: {
  children: ReactNode;
  onSelect?: () => void;
  disabled?: boolean;
}) {
  return (
    <Radix.Item
      disabled={disabled}
      onSelect={onSelect}
      className={cn(
        'flex cursor-pointer select-none items-center gap-2 rounded-button px-2.5 py-1.5 text-sm text-[var(--text-primary)] outline-none',
        'data-[highlighted]:bg-[var(--bg-hover)] data-[disabled]:opacity-50'
      )}
    >
      {children}
    </Radix.Item>
  );
}

export function DropdownMenuSeparator() {
  return <Radix.Separator className="my-1 h-px bg-[var(--border)]" />;
}
