'use client';

import { Search, Sun, Moon, Bell, LogOut } from 'lucide-react';
import { useThemeStore } from '@/stores/theme';
import { useSidebarStore } from '@/stores/sidebar';
import { useSession } from '@/hooks/useSession';
import { cn } from '@/lib/utils';

export function TopBar() {
  const { theme, toggleTheme } = useThemeStore();
  const { isCollapsed } = useSidebarStore();
  const { isAuthenticated, email } = useSession();

  return (
    <header
      className={cn(
        'fixed top-0 z-20 flex h-topbar items-center border-b px-6',
        'border-[var(--border)] bg-[var(--bg-surface)]',
        'transition-all duration-200 ease-out',
        isCollapsed ? 'left-sidebar-collapsed' : 'left-sidebar-expanded',
        'right-0'
      )}
      aria-label="Top bar"
    >
      {/* Search */}
      <button
        className={cn(
          'flex items-center gap-2 rounded-button px-3 py-1.5',
          'border border-[var(--border)] text-sm text-[var(--text-secondary)]',
          'hover:border-[var(--card-hover-border)] hover:text-[var(--text-primary)]',
          'transition-colors duration-150 ease-out'
        )}
        aria-label="Open search (Ctrl+K)"
      >
        <Search size={16} />
        <span className="hidden sm:inline">Search...</span>
        <kbd className="ml-4 hidden rounded border border-[var(--border)] px-1.5 py-0.5 text-xs text-[var(--text-secondary)] sm:inline">
          ⌘K
        </kbd>
      </button>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Actions */}
      <div className="flex items-center gap-1">
        {/* Notifications */}
        <button
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-button',
            'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]',
            'transition-colors duration-150 ease-out'
          )}
          aria-label="Notifications"
        >
          <Bell size={18} />
        </button>

        {/* Theme Toggle */}
        <button
          onClick={toggleTheme}
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-button',
            'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]',
            'transition-colors duration-150 ease-out'
          )}
          aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        {/* Account */}
        {isAuthenticated && (
          <div className="ml-2 flex items-center gap-2 border-l border-[var(--border)] pl-3">
            <span
              className="hidden max-w-[180px] truncate font-mono text-xs text-[var(--text-secondary)] sm:inline"
              title={email}
            >
              {email}
            </span>
            <form action="/logout" method="post">
              <button
                type="submit"
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-button',
                  'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]',
                  'transition-colors duration-150 ease-out'
                )}
                aria-label="Sign out"
              >
                <LogOut size={18} />
              </button>
            </form>
          </div>
        )}
      </div>
    </header>
  );
}
