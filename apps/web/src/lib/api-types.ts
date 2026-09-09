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
  projectId: string | null;
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
  /** null only for a target with no skills; every component is "computed" from M5. */
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

/** A target skill the user can do (capability ≥ bar) but has logged no evidence for (ADR-026 §2). */
export interface EvidenceGap {
  skillId: string;
  skill: string;
  importance: number;
  capability: number;
}

/** A target skill with ~no demonstrated project work (ADR-026 §2). */
export interface ProjectGap {
  skillId: string;
  skill: string;
  importance: number;
  capability: number;
}

export interface CareerReadiness {
  careerTargetId: string;
  targetName: string;
  overall: number;
  confidence: string;
  componentBreakdown: ReadinessComponent[];
  criticalGaps: CriticalGap[];
  highLeverageSkills: HighLeverageSkill[];
  evidenceGaps: EvidenceGap[];
  projectGaps: ProjectGap[];
  assessedSkillCount: number;
  targetSkillCount: number;
  explanation: string;
  trend: null;
}

export interface CareerGaps {
  careerTargetId: string;
  criticalGaps: CriticalGap[];
  highLeverageSkills: HighLeverageSkill[];
  evidenceGaps: EvidenceGap[];
  projectGaps: ProjectGap[];
}

// ===== Milestone 4 — Roadmaps (Blueprint §11/§20/§32, ADR-021/022/023) =====

export type RoadmapTopicStatus = 'covered' | 'in_progress' | 'not_started';

export interface RoadmapSummary {
  id: string;
  name: string;
  tier: string; // "P1" | "P2" | "P3"
  priorityTier: number; // 1 | 2 | 3
  sourceName: string;
  sourceUrl: string;
  description: string | null;
  status: string;
  /** Derived from the caller's assessments × topic relevance, 0..1 (ADR-023). */
  coverage: number;
  adopted: boolean;
  skillCount: number;
}

export interface RoadmapSkillLink {
  skillId: string;
  name: string;
  relevance: number;
  capability: number | null;
}

export interface RoadmapTopic {
  id: string;
  parentTopicId: string | null;
  name: string;
  sortOrder: number;
  sourceUrl: string | null;
  status: RoadmapTopicStatus;
  coverage: number;
  skills: RoadmapSkillLink[];
}

export interface RoadmapNextTopic {
  topicId: string;
  name: string;
  readiness: number;
  weightedGap: number;
  reason: string;
}

export interface RoadmapAdoption {
  roadmapId: string;
  status: 'active' | 'paused';
  startedAt: string;
  targetDate: string | null;
}

export interface RoadmapDetail {
  id: string;
  name: string;
  tier: string;
  priorityTier: number;
  sourceName: string;
  sourceUrl: string;
  description: string | null;
  lastVerifiedAt: string | null;
  status: string;
  coverage: number;
  topicCount: number;
  coveredTopicCount: number;
  adoption: RoadmapAdoption | null;
  topics: RoadmapTopic[];
  recommendedNext: RoadmapNextTopic[];
}

export interface RoadmapRef {
  id: string;
  name: string;
  tier: string;
}

export interface SharedSkill {
  skillId: string;
  name: string;
  roadmapCount: number;
  contributesTo: RoadmapRef[];
  capability: number | null;
  careerImportance: number | null;
  /** "HIGH" | "MEDIUM" | "LOW" | null */
  careerRelevance: string | null;
  leverage: number;
  recommendedAction: string;
}

export interface RoadmapIntersection {
  roadmaps: RoadmapRef[];
  sharedSkillCount: number;
  sharedSkills: SharedSkill[];
}

// ===== Milestone 5 — Projects & Evidence (Blueprint §13/§14/§20, ADR-024/025/026) =====

export type ProjectStatus = 'planned' | 'active' | 'completed' | 'archived';

export interface ProjectSkillLink {
  skillId: string;
  name: string;
  /** What the user expects this project to contribute to the skill, 0..1 (user-set). */
  expectedContribution: number;
  /** Derived (ADR-026 §3): 0 unless the project is completed with linked evidence. */
  demonstratedContribution: number;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  objectiveId: string | null;
  objectiveTitle: string | null;
  repositoryUrl: string | null;
  demoUrl: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  skillCount: number;
  evidenceCount: number;
  /** null until the project has tasks. */
  taskProgress: number | null;
  /** null until the project has skills. */
  skillsDemonstrated: number | null;
  progress: number;
  /** Empty on the list projection; populated on the detail response. */
  skills: ProjectSkillLink[];
}

export type EvidenceType =
  | 'project'
  | 'repository'
  | 'deployment'
  | 'benchmark'
  | 'document'
  | 'certification'
  | 'assessment'
  | 'talk'
  | 'other';

export type VerificationStatus = 'self_attested' | 'pending' | 'verified' | 'disputed';

export interface EvidenceSkillLink {
  skillId: string;
  name: string | null;
  contributionWeight: number;
}

export interface EvidenceProjectLink {
  projectId: string;
  name: string | null;
}

export interface Evidence {
  id: string;
  type: EvidenceType;
  title: string;
  description: string | null;
  sourceUrl: string | null;
  externalIdentifier: string | null;
  verificationStatus: VerificationStatus;
  metadata: Record<string, unknown>;
  occurredAt: string | null;
  createdAt: string;
  updatedAt: string;
  skills: EvidenceSkillLink[];
  projects: EvidenceProjectLink[];
}

// ===== Milestone 6 — Analytics & Weekly Review (Blueprint §15/§16/§20, UX §24, ADR-027/028/029) =====

export interface AnalyticsRange {
  from: string;
  to: string;
  weeks: number;
}

export interface ExecutionWeek {
  weekStart: string;
  planned: number;
  completed: number;
  focusedMinutes: number;
}

export interface StatusCounts {
  todo: number;
  inProgress: number;
  blocked: number;
  done: number;
  skipped: number;
}

export interface BlockerRow {
  taskId: string;
  title: string;
  blockedSince: string;
  daysBlocked: number;
}

export interface ObjectiveProgressPoint {
  at: string;
  progress: number;
  status: string;
}

export interface ObjectiveProgressSeries {
  objectiveId: string;
  title: string;
  points: ObjectiveProgressPoint[];
}

export interface ExecutionAnalytics {
  range: AnalyticsRange;
  /** "ok" | "no-data-yet" */
  status: string;
  weekly: ExecutionWeek[];
  completionRate: number | null;
  throughputPerWeek: number;
  statusCounts: StatusCounts;
  blockers: BlockerRow[];
  objectiveProgress: ObjectiveProgressSeries[];
}

export interface ReadinessTrendPoint {
  at: string;
  overall: number;
  skillCapability: number;
  assessedSkillCount: number;
}

export interface CountWeek {
  weekStart: string;
  count: number;
}

export interface TargetProgressionPoint {
  at: string;
  skillsAtThreshold: number;
  targetSkillCount: number;
}

export interface CareerAnalytics {
  careerTargetId: string;
  targetName: string;
  status: string;
  readinessTrend: ReadinessTrendPoint[];
  evidenceGrowth: CountWeek[];
  targetProgression: TargetProgressionPoint[];
}

export interface SkillSeriesPoint {
  at: string;
  overall: number;
}

export interface SkillProgression {
  skillId: string;
  name: string;
  points: SkillSeriesPoint[];
}

export interface NeglectedSkill {
  skillId: string;
  name: string;
  lastAssessedAt: string | null;
  daysSince: number | null;
}

export interface LeverageSkill {
  skillId: string;
  name: string;
  leverage: number;
  unlocks: number;
}

export interface Bottleneck {
  skillId: string;
  name: string;
  blockedDependents: string[];
}

export interface SkillAnalytics {
  status: string;
  progression: SkillProgression[];
  neglected: NeglectedSkill[];
  highLeverage: LeverageSkill[];
  bottlenecks: Bottleneck[];
}

// ----- Weekly Review -----

export interface WeeklyReviewContent {
  happened: {
    tasksCompleted: number;
    tasksPlanned: number;
    evidenceLogged: number;
    projectsAdvanced: number;
    milestonesHit: number;
  };
  improved: { kind: string; label: string; delta: number; skillId: string | null }[];
  missed: { taskId: string; title: string; objectiveTitle: string | null; dueAt: string | null }[];
  why: { claim: string; basis: string }[];
  changed: { kind: string; label: string; from: string; to: string }[];
  next: { taskId: string; title: string; priorityScore: number | null; reason: string }[];
}

export interface WeeklyReview {
  id: string;
  weekStart: string;
  weekEnd: string;
  summary: string | null;
  content: WeeklyReviewContent;
  userNotes: string | null;
  /** "system" | "user" | "oscar" — always "system" in M6. */
  generatedBy: string;
  /** "generated" | "reviewed" */
  status: string;
  createdAt: string;
  updatedAt: string;
}
