'use client';

import { type ReactNode } from 'react';
import * as RadixDialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Optional short description, announced to screen readers under the title. */
  description?: string;
  children: ReactNode;
}

/**
 * Modal dialog. Built on {@link https://www.radix-ui.com/primitives/docs/components/dialog Radix Dialog}
 * (ADR-030) — focus is trapped within the panel, the trigger regains focus on
 * close, background scroll is locked, and the panel is labelled by its heading.
 * Deep-Space styling is ours; the same small API as before.
 */
export function Dialog({ open, onClose, title, description, children }: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-50 animate-fade-in bg-black/60" />
        <RadixDialog.Content
          onOpenAutoFocus={(e) => {
            // focus the first real field, not Radix's default (the close button)
            const panel = e.currentTarget as HTMLElement;
            const first = panel.querySelector<HTMLElement>(
              'input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), ' +
                'select:not([disabled]), [href], button:not([data-dialog-close]):not([disabled])'
            );
            if (first) {
              e.preventDefault();
              first.focus();
            }
            // else: let Radix focus-trap the panel itself
          }}
          className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 animate-slide-up overflow-y-auto rounded-card border border-[var(--border)] bg-[var(--bg-surface-raised)] p-5 shadow-[var(--shadow-overlay)] focus:outline-none"
        >
          <div className="mb-4 flex items-start justify-between gap-3">
            <div>
              <RadixDialog.Title className="text-sm font-semibold text-[var(--text-primary)]">
                {title}
              </RadixDialog.Title>
              {description && (
                <RadixDialog.Description className="mt-0.5 text-xs text-[var(--text-secondary)]">
                  {description}
                </RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close
              data-dialog-close
              aria-label="Close dialog"
              className="mt-0.5 shrink-0 text-[var(--text-secondary)] transition-colors hover:text-[var(--text-primary)]"
            >
              <X size={18} />
            </RadixDialog.Close>
          </div>
          {!description && (
            <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
          )}
          {children}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
