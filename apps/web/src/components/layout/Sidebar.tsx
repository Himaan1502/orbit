'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { navigationItems } from '@/config/navigation';
import { useSidebarStore } from '@/stores/sidebar';

export function Sidebar() {
  const pathname = usePathname();
  const { isCollapsed, toggleSidebar } = useSidebarStore();

  const primaryItems = navigationItems.filter((item) => item.section === 'primary');
  const secondaryItems = navigationItems.filter((item) => item.section === 'secondary');
  const bottomItems = navigationItems.filter((item) => item.section === 'bottom');

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 z-30 flex h-full flex-col border-r transition-all duration-200 ease-out',
        'border-[var(--border)] bg-[var(--bg-surface)]',
        isCollapsed ? 'w-sidebar-collapsed' : 'w-sidebar-expanded'
      )}
      aria-label="Primary navigation"
    >
      {/* Logo */}
      <div className="flex h-topbar items-center border-b border-[var(--border)] px-4">
        <div className="flex items-center gap-3">
          {/* ORBIT Ring Mark */}
          <svg
            width="28"
            height="28"
            viewBox="0 0 28 28"
            fill="none"
            className="shrink-0"
            aria-hidden="true"
          >
            <circle
              cx="14"
              cy="14"
              r="11"
              stroke="var(--accent-cyan)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeDasharray="55 14"
            />
          </svg>
          {!isCollapsed && (
            <span
              className="text-sm font-semibold tracking-[0.08em] text-[var(--text-primary)]"
              style={{ fontFamily: 'var(--font-geist-sans)' }}
            >
              O R B I T
            </span>
          )}
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex flex-1 flex-col overflow-y-auto py-2">
        {/* Primary items */}
        <div className="flex flex-col gap-0.5 px-2">
          {primaryItems.map((item) => (
            <NavLink
              key={item.href}
              item={item}
              isActive={pathname === item.href}
              isCollapsed={isCollapsed}
            />
          ))}
        </div>

        {/* Divider */}
        <div className="mx-3 my-2 border-t border-[var(--border)]" />

        {/* Secondary items */}
        <div className="flex flex-col gap-0.5 px-2">
          {secondaryItems.map((item) => (
            <NavLink
              key={item.href}
              item={item}
              isActive={pathname === item.href}
              isCollapsed={isCollapsed}
            />
          ))}
        </div>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Divider */}
        <div className="mx-3 my-2 border-t border-[var(--border)]" />

        {/* Bottom items */}
        <div className="flex flex-col gap-0.5 px-2">
          {bottomItems.map((item) => (
            <NavLink
              key={item.href}
              item={item}
              isActive={pathname === item.href}
              isCollapsed={isCollapsed}
            />
          ))}
        </div>
      </nav>

      {/* Collapse Toggle */}
      <button
        onClick={toggleSidebar}
        className={cn(
          'flex h-10 items-center justify-center border-t border-[var(--border)]',
          'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
          'transition-colors duration-150 ease-out'
        )}
        aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {isCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
      </button>
    </aside>
  );
}

function NavLink({
  item,
  isActive,
  isCollapsed,
}: {
  item: (typeof navigationItems)[0];
  isActive: boolean;
  isCollapsed: boolean;
}) {
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      className={cn(
        'group flex h-10 items-center gap-2 rounded-button px-3 text-sm',
        'transition-colors duration-150 ease-out',
        isActive
          ? 'bg-[var(--accent-violet)]/10 text-[var(--accent-violet)]'
          : 'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]'
      )}
      aria-current={isActive ? 'page' : undefined}
      title={isCollapsed ? item.label : undefined}
    >
      <Icon size={20} className="shrink-0" />
      {!isCollapsed && <span>{item.label}</span>}
    </Link>
  );
}
