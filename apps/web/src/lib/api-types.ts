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

// ===== Milestone 3 — Skills & Career (Blueprint §8/§12/§20/§23/§24) =====

export type SkillDimension = 'knowledge' | 'practical' | 'evidence' | 'interview' | 'production';

export type SkillDimensions = Record<SkillDimension, number>;

export interface SkillSummary {
  id: string;
  canonicalName: string;
  category: string;
  description: string | null;
  /** The caller's latest overall capability for this skill, 0..1, or null if never assessed. */
  capability: number | null;
}

export interface SkillDetail extends SkillSummary {
  /** Always "self-reported" in M3 (ADR-017). Null until the skill is assessed. */
  confidence: string | null;
  dimensions: SkillDimensions | null;
  lastAssessedAt: string | null;
}

export interface SkillNeighbour {
  skillId: string;
  name: string;
  /** Dependency strength 0..1 (ADR-019). */
  strength: number;
  capability: number | null;
}

export interface SkillDependencies {
  prerequisites: SkillNeighbour[];
  dependents: SkillNeighbour[];
}

export interface ScoreComponent {
  dimension: string;
  value: number;
  weight: number;
  contribution: number;
}

export interface SkillAssessmentResult {
  id: string;
  skillId: string;
  overall: number;
  confidence: string;
  dimensions: SkillDimensions;
  components: ScoreComponent[];
  assessmentSource: string;
  assessedAt: string;
}

export interface CareerTargetSkill {
  skillId: string;
  importance: number;
  interviewWeight: number;
  productionWeight: number;
}

export interface CareerTarget {
  id: string;
  name: string;
  roleFamily: string | null;
  specialization: string | null;
  priority: number;
  status: 'active' | 'archived';
  targetDate: string | null;
  targetReadiness: number | null;
  skillCount: number;
  skills: CareerTargetSkill[];
}

export interface ReadinessComponent {
  component: string;
  /** null when the component is not yet tracked (evidence / project coverage in M3). */
  value: number | null;
  weight: number;
  status: string;
}

export interface CriticalGap {
  skillId: string;
  skill: string;
  importance: number;
  capability: number;
  gap: number;
}

export interface HighLeverageSkill {
  skillId: string;
  skill: string;
  unlocks: number;
  leverage: number;
}

export interface CareerReadiness {
  careerTargetId: string;
  targetName: string;
  overall: number;
  confidence: string;
  componentBreakdown: ReadinessComponent[];
  criticalGaps: CriticalGap[];
  highLeverageSkills: HighLeverageSkill[];
  assessedSkillCount: number;
  targetSkillCount: number;
  explanation: string;
  trend: null;
}

export interface CareerGaps {
  careerTargetId: string;
  criticalGaps: CriticalGap[];
  highLeverageSkills: HighLeverageSkill[];
}
