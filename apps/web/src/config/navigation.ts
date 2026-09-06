import {
  LayoutDashboard,
  Target,
  Briefcase,
  Map,
  Sparkles,
  FolderKanban,
  FileCheck,
  CalendarDays,
  BarChart3,
  Brain,
  Settings,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  section: 'primary' | 'secondary' | 'bottom';
}

// Navigation structure per UX Spec (ADR-008)
export const navigationItems: NavItem[] = [
  // Primary section
  { label: 'Command Center', href: '/command-center', icon: LayoutDashboard, section: 'primary' },
  { label: 'Objectives', href: '/objectives', icon: Target, section: 'primary' },
  { label: 'Career', href: '/career', icon: Briefcase, section: 'primary' },
  { label: 'Roadmaps', href: '/roadmaps', icon: Map, section: 'primary' },
  { label: 'Skills', href: '/skills', icon: Sparkles, section: 'primary' },
  { label: 'Projects', href: '/projects', icon: FolderKanban, section: 'primary' },
  { label: 'Evidence', href: '/evidence', icon: FileCheck, section: 'primary' },

  // Secondary section
  { label: 'Today', href: '/today', icon: CalendarDays, section: 'secondary' },
  { label: 'Analytics', href: '/analytics', icon: BarChart3, section: 'secondary' },
  { label: 'OSCAR', href: '/oscar', icon: Brain, section: 'secondary' },

  // Bottom section
  { label: 'Settings', href: '/settings', icon: Settings, section: 'bottom' },
];

// Mobile bottom nav items per UX Spec
export const mobileNavItems: NavItem[] = [
  { label: 'Today', href: '/today', icon: CalendarDays, section: 'primary' },
  { label: 'Career', href: '/career', icon: Briefcase, section: 'primary' },
  { label: 'Projects', href: '/projects', icon: FolderKanban, section: 'primary' },
];
