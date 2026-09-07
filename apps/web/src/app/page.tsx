'use client';

import Link from 'next/link';
import { ArrowRight, Sparkles, Target } from 'lucide-react';
import { useDashboard } from '@/features/dashboard/api';
import { useGeneratePlan } from '@/features/execution/api';
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
        </>
      )}
    </div>
  );
}
