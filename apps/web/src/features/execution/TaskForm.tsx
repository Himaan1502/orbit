'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { useCreateTask } from './api';
import type { Milestone } from '@/lib/api-types';

const schema = z.object({
  title: z.string().trim().min(1, 'Title is required.').max(200),
  description: z.string().trim().max(5000).optional(),
  estimatedMinutes: z.union([z.coerce.number().int().positive(), z.literal('')]).optional(),
  milestoneId: z.string().optional(),
  dueDate: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export function TaskForm({
  objectiveId,
  milestones,
  projectId,
  onDone,
}: {
  objectiveId: string;
  milestones: Milestone[];
  projectId?: string;
  onDone: () => void;
}) {
  const create = useCreateTask();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    await create.mutateAsync({
      objectiveId,
      projectId: projectId ?? null,
      title: values.title,
      description: values.description || undefined,
      estimatedMinutes: values.estimatedMinutes ? Number(values.estimatedMinutes) : null,
      milestoneId: values.milestoneId || null,
      dueAt: values.dueDate ? new Date(`${values.dueDate}T17:00:00`).toISOString() : null,
    });
    onDone();
  });

  return (
    <form onSubmit={onSubmit} className="space-y-3">
      <Input
        label="Title"
        placeholder="e.g. Implement NetworkPolicy for the demo app"
        {...register('title')}
        error={errors.title?.message}
        autoFocus
      />
      <Textarea
        label="Description"
        {...register('description')}
        error={errors.description?.message}
        rows={2}
      />
      <div className="grid grid-cols-2 gap-3">
        <Input
          label="Estimate (min)"
          type="number"
          min={1}
          placeholder="45"
          {...register('estimatedMinutes')}
        />
        <Input label="Due date" type="date" {...register('dueDate')} />
      </div>
      {milestones.length > 0 && (
        <div>
          <label
            htmlFor="milestoneId"
            className="mb-1.5 block text-xs text-[var(--text-secondary)]"
          >
            Milestone (optional)
          </label>
          <select
            id="milestoneId"
            {...register('milestoneId')}
            className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
          >
            <option value="">— none —</option>
            {milestones.map((m) => (
              <option key={m.id} value={m.id}>
                {m.title}
              </option>
            ))}
          </select>
        </div>
      )}
      {create.isError && (
        <p className="text-xs text-[var(--color-danger)]">Could not create the task.</p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Adding…' : 'Add task'}
        </Button>
      </div>
    </form>
  );
}
