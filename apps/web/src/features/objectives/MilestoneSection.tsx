'use client';

import { useState } from 'react';
import { Check, Flag, Plus } from 'lucide-react';
import type { Milestone } from '@/lib/api-types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { cn } from '@/lib/utils';
import { useCreateMilestone, useUpdateMilestone } from './api';

export function MilestoneSection({
  objectiveId,
  milestones,
}: {
  objectiveId: string;
  milestones: Milestone[];
}) {
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState('');
  const create = useCreateMilestone(objectiveId);
  const update = useUpdateMilestone(objectiveId);

  const submit = async () => {
    if (!title.trim()) return;
    await create.mutateAsync({ title: title.trim() });
    setTitle('');
    setAdding(false);
  };

  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-[var(--text-primary)]">Milestones</h2>
        <Button size="sm" variant="secondary" onClick={() => setAdding((v) => !v)}>
          <Plus size={14} /> Add
        </Button>
      </div>

      {adding && (
        <div className="mb-3 flex items-end gap-2">
          <div className="flex-1">
            <Input
              label="Milestone title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
              autoFocus
            />
          </div>
          <Button size="sm" onClick={submit} disabled={create.isPending}>
            Save
          </Button>
        </div>
      )}

      {milestones.length === 0 && !adding && (
        <p className="rounded-card border border-dashed border-[var(--border)] py-6 text-center text-xs text-[var(--text-secondary)]">
          <Flag size={16} className="mx-auto mb-1" />
          No milestones — progress will roll up from tasks instead.
        </p>
      )}

      <ul className="space-y-2">
        {milestones.map((m) => {
          const done = m.status === 'completed';
          return (
            <li
              key={m.id}
              className="flex items-center gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3"
            >
              <button
                onClick={() => update.mutate({ id: m.id, status: done ? 'pending' : 'completed' })}
                aria-label={done ? 'Mark milestone pending' : 'Mark milestone completed'}
                className={cn(
                  'flex h-5 w-5 shrink-0 items-center justify-center rounded-full border',
                  done
                    ? 'border-[var(--color-success)] bg-[var(--color-success)] text-[var(--bg-base)]'
                    : 'border-[var(--border)] hover:border-[var(--accent-cyan)]'
                )}
              >
                {done ? <Check size={12} /> : null}
              </button>
              <div className="min-w-0 flex-1">
                <p
                  className={cn(
                    'truncate text-sm text-[var(--text-primary)]',
                    done && 'line-through'
                  )}
                >
                  {m.title}
                </p>
                <div className="mt-1 flex items-center gap-2">
                  <StatusBadge status={m.status} />
                  <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                    {Math.round(m.progress * 100)}% of tasks
                  </span>
                </div>
              </div>
              <ProgressRing value={m.progress} size={40} strokeWidth={4} />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
