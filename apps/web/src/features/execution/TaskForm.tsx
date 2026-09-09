'use client';

import { useEffect, useMemo } from 'react';
import { useForm, type UseFormRegisterReturn } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { useSkills } from '@/features/skills/api';
import { useProjects } from '@/features/projects/api';
import type { Milestone, Task } from '@/lib/api-types';
import { useCreateTask, useUpdateTask, type UpdateTaskInput } from './api';

const schema = z.object({
  title: z.string().trim().min(1, 'Title is required.').max(200),
  description: z.string().trim().max(5000).optional(),
  estimatedMinutes: z.union([z.coerce.number().int().positive(), z.literal('')]).optional(),
  milestoneId: z.string().optional(),
  skillId: z.string().optional(),
  projectId: z.string().optional(),
  dueDate: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

const selectClass =
  'w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm ' +
  'text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none';

const dueDateInput = (iso: string | null) => (iso ? iso.slice(0, 10) : '');

export function TaskForm({
  objectiveId,
  milestones,
  projectId,
  task,
  onDone,
}: {
  objectiveId: string;
  milestones: Milestone[];
  /** When set (create-from-project), the project is fixed and its picker is hidden. */
  projectId?: string;
  /** Present → edit mode. */
  task?: Task;
  onDone: () => void;
}) {
  const create = useCreateTask();
  const update = useUpdateTask(objectiveId);
  const editing = !!task;
  const mutation = editing ? update : create;

  const skills = useSkills();
  const projects = useProjects();

  const skillsByCategory = useMemo<[string, { id: string; canonicalName: string }[]][]>(() => {
    const groups = new Map<string, { id: string; canonicalName: string }[]>();
    for (const s of skills.data ?? []) {
      const g = groups.get(s.category) ?? [];
      g.push({ id: s.id, canonicalName: s.canonicalName });
      groups.set(s.category, g);
    }
    return Array.from(groups.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [skills.data]);

  const defaults = useMemo<FormValues>(
    () => ({
      title: task?.title ?? '',
      description: task?.description ?? '',
      estimatedMinutes: task?.estimatedMinutes ?? '',
      milestoneId: task?.milestoneId ?? '',
      skillId: task?.skillId ?? '',
      projectId: task?.projectId ?? '',
      dueDate: dueDateInput(task?.dueAt ?? null),
    }),
    [task]
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: defaults });

  // The skill / project <select> options load async — re-apply the task's
  // current values once the options they reference exist (edit mode).
  const skillsReady = skills.data != null;
  const projectsReady = projects.data != null;
  useEffect(() => {
    if (task) reset(defaults);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task, skillsReady, projectsReady]);

  const onSubmit = handleSubmit(async (values) => {
    const estimate = values.estimatedMinutes ? Number(values.estimatedMinutes) : null;
    const dueAt = values.dueDate ? new Date(`${values.dueDate}T17:00:00`).toISOString() : null;

    if (editing) {
      const input: UpdateTaskInput & { id: string } = {
        id: task.id,
        title: values.title,
        description: values.description ?? '',
        estimatedMinutes: estimate,
        dueAt,
      };
      applyLink(input, 'milestone', values.milestoneId, task.milestoneId);
      applyLink(input, 'skill', values.skillId, task.skillId);
      if (!projectId) applyLink(input, 'project', values.projectId, task.projectId);
      await update.mutateAsync(input);
    } else {
      await create.mutateAsync({
        objectiveId,
        projectId: projectId ?? (values.projectId || null),
        milestoneId: values.milestoneId || null,
        skillId: values.skillId || null,
        title: values.title,
        description: values.description || undefined,
        estimatedMinutes: estimate,
        dueAt,
      });
    }
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
        <Select label="Milestone (optional)" id="milestoneId" register={register('milestoneId')}>
          {milestones.map((m) => (
            <option key={m.id} value={m.id}>
              {m.title}
            </option>
          ))}
        </Select>
      )}

      <Select label="Skill (optional)" id="skillId" register={register('skillId')}>
        {skillsByCategory.map(([category, list]) => (
          <optgroup key={category} label={category}>
            {list.map((s) => (
              <option key={s.id} value={s.id}>
                {s.canonicalName}
              </option>
            ))}
          </optgroup>
        ))}
      </Select>
      <p className="-mt-1.5 text-[11px] text-[var(--text-secondary)]">
        Tagging a skill lets the Priority Engine compute its career &amp; skill factors for this
        task.
      </p>

      {!projectId && (
        <Select label="Project (optional)" id="projectId" register={register('projectId')}>
          {(projects.data ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </Select>
      )}

      {mutation.isError && (
        <p className="text-xs text-[var(--color-danger)]">
          Could not {editing ? 'save the task' : 'create the task'}.
        </p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending
            ? editing
              ? 'Saving…'
              : 'Adding…'
            : editing
              ? 'Save changes'
              : 'Add task'}
        </Button>
      </div>
    </form>
  );
}

/** Set the id, or ask the API to clear it when the field was cleared in edit mode. */
function applyLink(
  input: UpdateTaskInput,
  field: 'milestone' | 'skill' | 'project',
  next: string | undefined,
  current: string | null
) {
  const idKey = `${field}Id` as 'milestoneId' | 'skillId' | 'projectId';
  const clearKey = `clear${field[0].toUpperCase()}${field.slice(1)}` as
    'clearMilestone' | 'clearSkill' | 'clearProject';
  if (next) {
    input[idKey] = next;
  } else if (current) {
    input[clearKey] = true;
  }
}

function Select({
  label,
  id,
  register,
  children,
}: {
  label: string;
  id: string;
  register: UseFormRegisterReturn;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-xs text-[var(--text-secondary)]">
        {label}
      </label>
      <select id={id} {...register} className={selectClass}>
        <option value="">— none —</option>
        {children}
      </select>
    </div>
  );
}
