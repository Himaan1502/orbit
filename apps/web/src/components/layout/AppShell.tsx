'use client';

import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { MobileNav } from './MobileNav';
import { useSidebarStore } from '@/stores/sidebar';
import { cn } from '@/lib/utils';

export function AppShell({ children }: { children: React.ReactNode }) {
  const { isCollapsed } = useSidebarStore();

  return (
    <div className="min-h-screen bg-[var(--bg-base)]">
      {/* Skip Navigation */}
      <a href="#main-content" className="skip-nav">
        Skip to main content
      </a>

      {/* Desktop Sidebar */}
      <div className="hidden sm:block">
        <Sidebar />
      </div>

      {/* Top Bar */}
      <div className="hidden sm:block">
        <TopBar />
      </div>

      {/* Mobile Top Bar */}
      <header className="flex h-14 items-center justify-between border-b border-[var(--border)] bg-[var(--bg-surface)] px-4 sm:hidden">
        <span className="text-sm font-semibold tracking-[0.08em] text-[var(--text-primary)]">
          O R B I T
        </span>
      </header>

      {/* Main Content */}
      <main
        id="main-content"
        className={cn(
          'min-h-screen transition-all duration-200 ease-out',
          // Desktop: offset for sidebar and topbar
          'sm:pt-topbar',
          isCollapsed ? 'sm:pl-sidebar-collapsed' : 'sm:pl-sidebar-expanded',
          // Mobile: offset for mobile header and bottom nav
          'pb-14 sm:pb-0'
        )}
      >
        <div className="mx-auto max-w-[1440px] p-4 sm:p-6">{children}</div>
      </main>

      {/* Mobile Bottom Nav */}
      <MobileNav />
    </div>
  );
}
