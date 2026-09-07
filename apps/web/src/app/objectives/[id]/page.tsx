'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ListTodo, Plus } from 'lucide-react';
import { useObjective, useMilestones } from '@/features/objectives/api';
import { useTasks } from '@/features/execution/api';
import { MilestoneSection } from '@/features/objectives/MilestoneSection';
import { TaskForm } from '@/features/execution/TaskForm';
import { TaskRow } from '@/features/execution/TaskRow';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';

export default function ObjectiveDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const objective = useObjective(id);
  const milestones = useMilestones(id);
  const allTasks = useTasks();
  const [adding, setAdding] = useState(false);

  const tasks = useMemo(
    () => (allTasks.data ?? []).filter((t) => t.objectiveId === id),
    [allTasks.data, id]
  );

  if (objective.isLoading) return <LoadingState />;
  if (objective.isError || !objective.data)
    return <ErrorState message="This objective could not be found." />;

  const o = objective.data;

  return (
    <div className="animate-fade-in">
      <Link
        href="/objectives"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Objectives
      </Link>

      <Card className="mb-6">
        <div className="flex items-start gap-5">
          <ProgressRing value={o.progress} size={104} />
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex items-center gap-2">
              <StatusBadge status={o.status} />
              <CardLabel>Priority P{o.priority}</CardLabel>
            </div>
            <h1 className="text-xl font-semibold text-[var(--text-primary)]">{o.title}</h1>
            {o.description && (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">{o.description}</p>
            )}
            {o.targetDate && (
              <p className="mt-3 font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--text-secondary)]">
                Target · {formatDate(o.targetDate)}
              </p>
            )}
          </div>
        </div>
      </Card>

      <div className="grid gap-8 lg:grid-cols-[1fr_1.2fr]">
        <MilestoneSection objectiveId={id} milestones={milestones.data ?? []} />

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-[var(--text-primary)]">Tasks</h2>
            <Button size="sm" onClick={() => setAdding(true)}>
              <Plus size={14} /> Add task
            </Button>
          </div>

          {allTasks.isLoading && <LoadingState />}
          {tasks.length === 0 && !allTasks.isLoading && (
            <EmptyState
              icon={ListTodo}
              title="No tasks yet"
              hint="Add the concrete steps — ORBIT scores and schedules them."
            />
          )}
          <div className="space-y-2">
            {tasks.map((t) => (
              <TaskRow key={t.id} task={t} objectiveId={id} />
            ))}
          </div>
        </section>
      </div>

      <Dialog open={adding} onClose={() => setAdding(false)} title="Add task">
        <TaskForm
          objectiveId={id}
          milestones={milestones.data ?? []}
          onDone={() => setAdding(false)}
        />
      </Dialog>
    </div>
  );
}
