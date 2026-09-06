'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Menu } from 'lucide-react';
import { cn } from '@/lib/utils';
import { mobileNavItems, navigationItems } from '@/config/navigation';
import { useState } from 'react';

export function MobileNav() {
  const pathname = usePathname();
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  return (
    <>
      {/* Bottom Navigation Bar */}
      <nav
        className={cn(
          'fixed bottom-0 left-0 right-0 z-30 flex h-14 items-center justify-around',
          'border-t border-[var(--border)] bg-[var(--bg-surface)]',
          'sm:hidden'
        )}
        aria-label="Mobile navigation"
      >
        {mobileNavItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex flex-col items-center gap-0.5 px-3 py-1',
                isActive ? 'text-[var(--accent-cyan)]' : 'text-[var(--text-secondary)]'
              )}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon size={20} />
              <span className="text-[10px]">{item.label}</span>
            </Link>
          );
        })}

        {/* More button */}
        <button
          onClick={() => setIsDrawerOpen(true)}
          className="flex flex-col items-center gap-0.5 px-3 py-1 text-[var(--text-secondary)]"
          aria-label="More navigation options"
        >
          <Menu size={20} />
          <span className="text-[10px]">More</span>
        </button>
      </nav>

      {/* Drawer */}
      {isDrawerOpen && (
        <div
          className="fixed inset-0 z-40 sm:hidden"
          role="dialog"
          aria-modal="true"
          aria-label="Navigation drawer"
        >
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/60"
            onClick={() => setIsDrawerOpen(false)}
            aria-hidden="true"
          />
          {/* Drawer content */}
          <div className="absolute bottom-0 left-0 right-0 rounded-t-2xl bg-[var(--bg-surface)] p-4 pb-8">
            <div className="mb-4 flex items-center justify-between">
              <span className="text-sm font-semibold text-[var(--text-primary)]">Navigation</span>
              <button
                onClick={() => setIsDrawerOpen(false)}
                className="text-[var(--text-secondary)]"
                aria-label="Close navigation drawer"
              >
                ✕
              </button>
            </div>
            <div className="flex flex-col gap-1">
              {navigationItems.map((item) => {
                const Icon = item.icon;
                const isActive = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setIsDrawerOpen(false)}
                    className={cn(
                      'flex items-center gap-3 rounded-button px-3 py-2.5 text-sm',
                      isActive
                        ? 'bg-[var(--accent-violet)]/10 text-[var(--accent-violet)]'
                        : 'text-[var(--text-secondary)]'
                    )}
                  >
                    <Icon size={20} />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
