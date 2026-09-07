'use client';

import Link from 'next/link';
import { CalendarDays, RefreshCw, Sparkles } from 'lucide-react';
import {
  useDailyPlan,
  useGeneratePlan,
  useRegeneratePlan,
  useCompleteTask,
} from '@/features/execution/api';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import { formatMinutes, impactLabel, todayIso } from '@/lib/format';
import { cn } from '@/lib/utils';

export default function TodayPage() {
  const date = todayIso();
  const { data: plan, isLoading, isError } = useDailyPlan(date);
  const generate = useGeneratePlan(date);
  const regenerate = useRegeneratePlan(date);
  const complete = useCompleteTask();

  const heading = new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });

  return (
    <div className="mx-auto max-w-2xl animate-fade-in">
      <header className="mb-6">
        <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-[var(--text-secondary)]">
          Today
        </p>
        <h1 className="text-2xl font-semibold text-[var(--text-primary)]">{heading}</h1>
      </header>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load today’s plan." />}

      {!isLoading && !plan && (
        <EmptyState
          icon={CalendarDays}
          title="No plan for today yet"
          hint="ORBIT builds a focused plan from your open tasks and available time."
          action={
            <Button onClick={() => generate.mutate(undefined)} disabled={generate.isPending}>
              <Sparkles size={16} /> {generate.isPending ? 'Generating…' : 'Generate today’s plan'}
            </Button>
          }
        />
      )}

      {plan && (
        <>
          <Card className="mb-4">
            <div className="flex items-center justify-between">
              <div className="flex gap-6 font-mono text-xs text-[var(--text-secondary)]">
                <span>
                  <span className="block text-[10px] uppercase tracking-[0.1em]">Available</span>
                  <span className="text-sm text-[var(--text-primary)]">
                    {formatMinutes(plan.availableMinutes)}
                  </span>
                </span>
                <span>
                  <span className="block text-[10px] uppercase tracking-[0.1em]">Planned</span>
                  <span className="text-sm text-[var(--text-primary)]">
                    {formatMinutes(plan.allocatedMinutes)}
                  </span>
                </span>
                <span>
                  <span className="block text-[10px] uppercase tracking-[0.1em]">Tasks</span>
                  <span className="text-sm text-[var(--text-primary)]">{plan.tasks.length}</span>
                </span>
              </div>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => regenerate.mutate(plan.id)}
                disabled={regenerate.isPending}
              >
                <RefreshCw size={13} className={cn(regenerate.isPending && 'animate-spin')} />{' '}
                Regenerate
              </Button>
            </div>
          </Card>

          <ol className="space-y-2">
            {plan.tasks.map((entry, i) => {
              const done = entry.status === 'done';
              const impact = impactLabel(entry.priorityScore);
              return (
                <li
                  key={entry.taskId}
                  className={cn(
                    'flex items-start gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3',
                    done && 'opacity-60'
                  )}
                >
                  <button
                    onClick={() => !done && complete.mutate(entry.taskId)}
                    disabled={done || complete.isPending}
                    aria-label={done ? 'Completed' : 'Complete task'}
                    className={cn(
                      'mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border font-mono text-[10px]',
                      done
                        ? 'border-[var(--color-success)] bg-[var(--color-success)] text-[var(--bg-base)]'
                        : 'border-[var(--border)] text-[var(--text-secondary)] hover:border-[var(--accent-cyan)]'
                    )}
                  >
                    {done ? '✓' : String(i + 1).padStart(2, '0')}
                  </button>
                  <div className="min-w-0 flex-1">
                    <p className={cn('text-sm text-[var(--text-primary)]', done && 'line-through')}>
                      {entry.title}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                      <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                        {formatMinutes(entry.allocatedMinutes)}
                      </span>
                      <Badge
                        tone={impact === 'HIGH' ? 'cyan' : impact === 'LOW' ? 'neutral' : 'violet'}
                      >
                        {impact} impact
                      </Badge>
                      {entry.objectiveTitle && entry.objectiveId && (
                        <Link
                          href={`/objectives/${entry.objectiveId}`}
                          className="truncate text-[11px] text-[var(--text-secondary)] hover:text-[var(--accent-cyan)]"
                        >
                          {entry.objectiveTitle}
                        </Link>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
        </>
      )}
    </div>
  );
}
