'use client';

import { type ReactNode } from 'react';
import * as RadixTooltip from '@radix-ui/react-tooltip';

/**
 * Hover / focus tooltip on {@link https://www.radix-ui.com/primitives/docs/components/tooltip Radix Tooltip}
 * (ADR-030). Needs the `TooltipProvider` in `app/providers.tsx` (already wired).
 * The trigger must be a single focusable element.
 */
export function Tooltip({
  content,
  children,
  side = 'top',
}: {
  content: ReactNode;
  children: ReactNode;
  side?: 'top' | 'right' | 'bottom' | 'left';
}) {
  return (
    <RadixTooltip.Root>
      <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
      <RadixTooltip.Portal>
        <RadixTooltip.Content
          side={side}
          sideOffset={6}
          className="z-50 max-w-xs animate-fade-in rounded-button border border-[var(--border)] bg-[var(--bg-surface-raised)] px-2.5 py-1.5 text-xs text-[var(--text-primary)] shadow-[var(--shadow-overlay)]"
        >
          {content}
          <RadixTooltip.Arrow className="fill-[var(--bg-surface-raised)]" />
        </RadixTooltip.Content>
      </RadixTooltip.Portal>
    </RadixTooltip.Root>
  );
}
