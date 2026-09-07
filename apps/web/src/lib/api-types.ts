/** Response shapes from `apps/api` (Blueprint §9/§10/§20). Kept in sync by hand. */

export type ObjectiveStatus = 'active' | 'completed' | 'archived';
export type MilestoneStatus = 'pending' | 'in_progress' | 'completed';
export type TaskStatus = 'todo' | 'in_progress' | 'blocked' | 'done' | 'skipped';

export interface Objective {
  id: string;
  title: string;
  description: string | null;
  priority: number;
  status: ObjectiveStatus;
  progress: number;
  targetDate: string | null;
  careerTargetId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Milestone {
  id: string;
  objectiveId: string;
  title: string;
  description: string | null;
  status: MilestoneStatus;
  progress: number;
  targetDate: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface PriorityFactor {
  value: number;
  weight: number;
  contribution: number;
  source: 'computed' | 'proxy' | 'neutral-default';
  detail: string;
}

export interface PriorityReason {
  score: number;
  engineVersion: string;
  weightsFingerprint: string;
  computedAt: string;
  factors: Record<string, PriorityFactor>;
}

export interface Task {
  id: string;
  objectiveId: string | null;
  milestoneId: string | null;
  title: string;
  description: string | null;
  estimatedMinutes: number | null;
  status: TaskStatus;
  priorityScore: number | null;
  priorityReason: PriorityReason | null;
  dueAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DailyPlanEntry {
  taskId: string;
  position: number;
  allocatedMinutes: number;
  title: string;
  status: TaskStatus;
  estimatedMinutes: number | null;
  priorityScore: number | null;
  dueAt: string | null;
  objectiveId: string | null;
  objectiveTitle: string | null;
}

export interface DailyPlan {
  id: string;
  planDate: string;
  availableMinutes: number;
  allocatedMinutes: number;
  status: 'draft' | 'active' | 'completed';
  generatedBy: 'system' | 'user';
  tasks: DailyPlanEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface Me {
  id: string;
  email: string;
  displayName: string | null;
  status: string;
  onboardingCompleted: boolean;
  timezone: string;
  availableDailyMinutes: number | null;
  createdAt: string;
}

export interface Dashboard {
  user: { displayName: string | null; email: string };
  objectiveHealth: { active: number; completed: number; averageProgress: number };
  primaryObjective: DashboardObjective | null;
  activeObjectives: DashboardObjective[];
  nextBestAction: NextBestAction | null;
  today: {
    date: string;
    planExists: boolean;
    taskCount: number;
    allocatedMinutes: number;
    availableMinutes: number;
  };
  executionHealth: { openTasks: number; dueWithin7Days: number; completedLast7Days: number };
}

export interface DashboardObjective {
  id: string;
  title: string;
  priority: number;
  status: ObjectiveStatus;
  progress: number;
  targetDate: string | null;
}

export interface NextBestAction {
  taskId: string;
  title: string;
  priorityScore: number | null;
  why: PriorityReason | null;
  objectiveId: string | null;
  objectiveTitle: string | null;
  estimatedMinutes: number | null;
  dueAt: string | null;
}
