'use client';

import Link from 'next/link';
import { ArrowRight, FolderKanban, LineChart, Sparkles, Target } from 'lucide-react';
import { useDashboard } from '@/features/dashboard/api';
import { useGeneratePlan } from '@/features/execution/api';
import { useProjects } from '@/features/projects/api';
import { useEvidenceList } from '@/features/evidence/api';
import { useCareerAnalytics, useExecutionAnalytics } from '@/features/analytics/api';
import { Sparkline } from '@/features/analytics/charts';
import { EvidenceRow } from '@/features/evidence/EvidenceRow';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { WhyThis } from '@/components/ui/WhyThis';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import { formatMinutes, todayIso } from '@/lib/format';

export default function CommandCenterPage() {
  const { data, isLoading, isError } = useDashboard();
  const generate = useGeneratePlan(todayIso());
  const projects = useProjects();
  const recentEvidence = useEvidenceList({});
  const execAnalytics = useExecutionAnalytics();
  const careerAnalytics = useCareerAnalytics();

  const activeProjects = (projects.data ?? []).filter((p) => p.status === 'active');
  const latestEvidence = (recentEvidence.data ?? []).slice(0, 4);
  const readinessTrend = careerAnalytics.data?.readinessTrend ?? [];
  const execWeekly = execAnalytics.data?.weekly ?? [];
  const showTrends = readinessTrend.length >= 2 || execWeekly.some((w) => w.completed > 0);

  if (isLoading) return <LoadingState />;
  if (isError || !data) return <ErrorState message="Could not load your Command Center." />;

  const greeting = data.user.displayName
    ? `Welcome back, ${data.user.displayName}`
    : 'Welcome back';
  const noObjectives = data.objectiveHealth.active === 0 && data.objectiveHealth.completed === 0;

  return (
    <div className="mx-auto max-w-4xl animate-fade-in space-y-6">
      <header>
        <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-[var(--text-secondary)]">
          Command Center
        </p>
        <h1 className="text-2xl font-semibold text-[var(--text-primary)]">{greeting}</h1>
        <p className="text-sm text-[var(--text-secondary)]">
          Where you are, what matters, what’s next.
        </p>
      </header>

      {noObjectives ? (
        <EmptyState
          icon={Target}
          title="Set your first objective"
          hint="ORBIT turns objectives into milestones, tasks, and a daily plan."
          action={
            <Link href="/objectives">
              <Button>
                Go to Objectives <ArrowRight size={16} />
              </Button>
            </Link>
          }
        />
      ) : (
        <>
          {/* Next best action */}
          <Card>
            <CardLabel>Next best action</CardLabel>
            {data.nextBestAction ? (
              <div className="mt-2">
                <p className="text-lg font-medium text-[var(--text-primary)]">
                  {data.nextBestAction.title}
                </p>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[var(--text-secondary)]">
                  {data.nextBestAction.objectiveTitle && data.nextBestAction.objectiveId && (
                    <Link
                      href={`/objectives/${data.nextBestAction.objectiveId}`}
                      className="hover:text-[var(--accent-cyan)]"
                    >
                      {data.nextBestAction.objectiveTitle}
                    </Link>
                  )}
                  {data.nextBestAction.estimatedMinutes && (
                    <span className="font-mono">
                      {formatMinutes(data.nextBestAction.estimatedMinutes)}
                    </span>
                  )}
                </div>
                <div className="mt-3 flex items-center gap-2">
                  <Link href="/today">
                    <Button size="sm">Go to Today</Button>
                  </Link>
                </div>
                <div className="mt-3">
                  <WhyThis reason={data.nextBestAction.why} />
                </div>
              </div>
            ) : (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">
                No open tasks. Add tasks to an objective to get a recommendation.
              </p>
            )}
          </Card>

          {/* Health metrics */}
          <div className="grid gap-3 sm:grid-cols-3">
            <Card>
              <CardLabel>Objective health</CardLabel>
              <p className="mt-2 text-2xl font-semibold text-[var(--text-primary)]">
                {data.objectiveHealth.active}
                <span className="ml-1 text-sm font-normal text-[var(--text-secondary)]">
                  active
                </span>
              </p>
              <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                {Math.round(data.objectiveHealth.averageProgress * 100)}% avg progress ·{' '}
                {data.objectiveHealth.completed} completed
              </p>
            </Card>
            <Card>
              <CardLabel>Execution</CardLabel>
              <p className="mt-2 text-2xl font-semibold text-[var(--text-primary)]">
                {data.executionHealth.openTasks}
                <span className="ml-1 text-sm font-normal text-[var(--text-secondary)]">open</span>
              </p>
              <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                {data.executionHealth.dueWithin7Days} due in 7d ·{' '}
                {data.executionHealth.completedLast7Days} done last 7d
              </p>
            </Card>
            <Card>
              <CardLabel>Today</CardLabel>
              {data.today.planExists ? (
                <>
                  <p className="mt-2 text-2xl font-semibold text-[var(--text-primary)]">
                    {data.today.taskCount}
                    <span className="ml-1 text-sm font-normal text-[var(--text-secondary)]">
                      tasks
                    </span>
                  </p>
                  <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                    {formatMinutes(data.today.allocatedMinutes)} /{' '}
                    {formatMinutes(data.today.availableMinutes)}
                  </p>
                </>
              ) : (
                <Button
                  size="sm"
                  variant="secondary"
                  className="mt-3"
                  onClick={() => generate.mutate(undefined)}
                  disabled={generate.isPending}
                >
                  <Sparkles size={13} /> {generate.isPending ? 'Generating…' : 'Generate plan'}
                </Button>
              )}
            </Card>
          </div>

          {/* Active objectives */}
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-[var(--text-primary)]">
                Active objectives
              </h2>
              <Link
                href="/objectives"
                className="text-xs text-[var(--text-secondary)] hover:text-[var(--accent-cyan)]"
              >
                All objectives
              </Link>
            </div>
            <ul className="space-y-2">
              {data.activeObjectives.map((o) => (
                <li key={o.id}>
                  <Link
                    href={`/objectives/${o.id}`}
                    className="flex items-center gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3 transition-colors hover:border-[var(--card-hover-border)]"
                  >
                    <ProgressRing value={o.progress} size={40} strokeWidth={4} />
                    <span className="min-w-0 flex-1 truncate text-sm text-[var(--text-primary)]">
                      {o.title}
                    </span>
                    <StatusBadge status={o.status} />
                    <CardLabel>P{o.priority}</CardLabel>
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          {/* Important trends (UX §16) */}
          {showTrends && (
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-[var(--text-primary)]">
                  Important trends
                </h2>
                <Link
                  href="/analytics"
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--accent-cyan)]"
                >
                  Analytics
                </Link>
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <Card className="p-4">
                  <CardLabel>Readiness</CardLabel>
                  {readinessTrend.length >= 2 ? (
                    <>
                      <Sparkline
                        data={readinessTrend.map((p) => ({ v: p.overall }))}
                        yKey="v"
                        height={36}
                      />
                      <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                        {Math.round((readinessTrend.at(-1)?.overall ?? 0) * 100)}% now
                      </p>
                    </>
                  ) : (
                    <p className="mt-2 text-xs text-[var(--text-secondary)]">
                      Assess target skills to track this.
                    </p>
                  )}
                </Card>
                <Card className="p-4">
                  <CardLabel>Completed / week</CardLabel>
                  <Sparkline
                    data={execWeekly.map((w) => ({ v: w.completed }))}
                    yKey="v"
                    height={36}
                  />
                  <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                    {(execAnalytics.data?.throughputPerWeek ?? 0).toFixed(1)} avg
                  </p>
                </Card>
                <Card className="p-4">
                  <CardLabel>Focus / week</CardLabel>
                  <Sparkline
                    data={execWeekly.map((w) => ({ v: w.focusedMinutes }))}
                    yKey="v"
                    height={36}
                  />
                  <p className="mt-1 inline-flex items-center gap-1 font-mono text-[11px] text-[var(--text-secondary)]">
                    <LineChart size={11} /> minutes planned
                  </p>
                </Card>
              </div>
            </section>
          )}

          {/* Active projects */}
          {activeProjects.length > 0 && (
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-[var(--text-primary)]">
                  Active projects
                </h2>
                <Link
                  href="/projects"
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--accent-cyan)]"
                >
                  All projects
                </Link>
              </div>
              <ul className="space-y-2">
                {activeProjects.map((p) => (
                  <li key={p.id}>
                    <Link
                      href={`/projects/${p.id}`}
                      className="flex items-center gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3 transition-colors hover:border-[var(--card-hover-border)]"
                    >
                      <ProgressRing value={p.progress} size={40} strokeWidth={4} />
                      <span className="min-w-0 flex-1 truncate text-sm text-[var(--text-primary)]">
                        {p.name}
                      </span>
                      <span className="shrink-0 font-mono text-[11px] text-[var(--text-secondary)]">
                        {p.evidenceCount} evidence
                      </span>
                      <FolderKanban size={14} className="shrink-0 text-[var(--text-secondary)]" />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* Recent evidence */}
          {latestEvidence.length > 0 && (
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-[var(--text-primary)]">
                  Recent evidence
                </h2>
                <Link
                  href="/evidence"
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--accent-cyan)]"
                >
                  All evidence
                </Link>
              </div>
              <ul className="space-y-2">
                {latestEvidence.map((e) => (
                  <EvidenceRow key={e.id} evidence={e} />
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}
