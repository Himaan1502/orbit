'use client';

import { useState } from 'react';
import { Check, Pencil, RotateCcw } from 'lucide-react';
import type { Milestone, Task } from '@/lib/api-types';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { Dialog } from '@/components/ui/Dialog';
import { WhyThis } from '@/components/ui/WhyThis';
import { cn } from '@/lib/utils';
import { formatMinutes, impactLabel, relativeDueDate } from '@/lib/format';
import { useCompleteTask, useUpdateTask } from './api';
import { TaskForm } from './TaskForm';

export function TaskRow({
  task,
  objectiveId,
  milestones = [],
  projectLocked = false,
}: {
  task: Task;
  objectiveId?: string;
  /** The parent objective's milestones — enables the milestone picker in the edit form. */
  milestones?: Milestone[];
  /** True on a project page: the project is fixed, its picker is hidden. */
  projectLocked?: boolean;
}) {
  const complete = useCompleteTask(objectiveId);
  const update = useUpdateTask(objectiveId);
  const done = task.status === 'done';
  const impact = impactLabel(task.priorityScore);
  const [editing, setEditing] = useState(false);

  return (
    <div
      className={cn(
        'rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3',
        done && 'opacity-60'
      )}
    >
      <div className="flex items-start gap-3">
        <button
          onClick={() =>
            done ? update.mutate({ id: task.id, status: 'todo' }) : complete.mutate(task.id)
          }
          disabled={complete.isPending || update.isPending}
          aria-label={done ? 'Reopen task' : 'Complete task'}
          className={cn(
            'mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border transition-colors',
            done
              ? 'border-[var(--color-success)] bg-[var(--color-success)] text-[var(--bg-base)]'
              : 'border-[var(--border)] hover:border-[var(--accent-cyan)]'
          )}
        >
          {done ? <Check size={12} /> : null}
        </button>

        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <p className={cn('text-sm text-[var(--text-primary)]', done && 'line-through')}>
              {task.title}
            </p>
            <div className="flex shrink-0 items-center gap-1.5">
              {done && (
                <button
                  onClick={() => update.mutate({ id: task.id, status: 'todo' })}
                  className="text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                  aria-label="Reopen"
                >
                  <RotateCcw size={14} />
                </button>
              )}
              <button
                onClick={() => setEditing(true)}
                className="text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                aria-label="Edit task"
              >
                <Pencil size={13} />
              </button>
            </div>
          </div>

          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <StatusBadge status={task.status} />
            <Badge tone={impact === 'HIGH' ? 'cyan' : impact === 'LOW' ? 'neutral' : 'violet'}>
              {impact} impact
            </Badge>
            {task.skillId && (
              <Badge tone="violet" title="Skill-tagged">
                skill
              </Badge>
            )}
            {task.estimatedMinutes && (
              <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                {formatMinutes(task.estimatedMinutes)}
              </span>
            )}
            {task.dueAt && (
              <span className="font-mono text-[11px] text-[var(--color-warning)]">
                {relativeDueDate(task.dueAt)}
              </span>
            )}
          </div>

          {task.priorityReason && (
            <div className="mt-2">
              <WhyThis reason={task.priorityReason} />
            </div>
          )}
        </div>
      </div>

      <Dialog open={editing} onClose={() => setEditing(false)} title="Edit task">
        <TaskForm
          objectiveId={objectiveId ?? task.objectiveId ?? ''}
          milestones={milestones}
          projectId={projectLocked ? (task.projectId ?? undefined) : undefined}
          task={task}
          onDone={() => setEditing(false)}
        />
      </Dialog>
    </div>
  );
}
