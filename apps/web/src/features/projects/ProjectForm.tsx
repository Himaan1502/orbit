'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { SkillWeightPicker, type SkillWeight } from '@/components/ui/SkillWeightPicker';
import { useObjectives } from '@/features/objectives/api';
import type { Project } from '@/lib/api-types';
import { useCreateProject, useUpdateProject, type ProjectInput } from './api';

const schema = z.object({
  name: z.string().trim().min(1, 'Name is required.').max(160),
  description: z.string().trim().max(5000).optional(),
  objectiveId: z.string().optional(),
  repositoryUrl: z.string().trim().url('Enter a valid URL.').max(2000).optional().or(z.literal('')),
  demoUrl: z.string().trim().url('Enter a valid URL.').max(2000).optional().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;

export function ProjectForm({ project, onDone }: { project?: Project; onDone: () => void }) {
  const create = useCreateProject();
  const update = useUpdateProject(project?.id ?? '');
  const mutation = project ? update : create;
  const objectives = useObjectives();

  const [skills, setSkills] = useState<SkillWeight[]>(
    (project?.skills ?? []).map((s) => ({ skillId: s.skillId, weight: s.expectedContribution }))
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: project?.name ?? '',
      description: project?.description ?? '',
      objectiveId: project?.objectiveId ?? '',
      repositoryUrl: project?.repositoryUrl ?? '',
      demoUrl: project?.demoUrl ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    const payload: ProjectInput = {
      name: values.name,
      description: values.description || undefined,
      objectiveId: values.objectiveId || null,
      repositoryUrl: values.repositoryUrl || null,
      demoUrl: values.demoUrl || null,
      skills: skills.map((s) => ({ skillId: s.skillId, expectedContribution: s.weight })),
    };
    await mutation.mutateAsync(payload);
    onDone();
  });

  return (
    <form onSubmit={onSubmit} className="space-y-3">
      <Input
        label="Name"
        placeholder="e.g. Kubernetes operator for internal platform"
        {...register('name')}
        error={errors.name?.message}
        autoFocus
      />
      <Textarea
        label="What are you building, and why?"
        {...register('description')}
        error={errors.description?.message}
        rows={2}
      />
      <div>
        <label htmlFor="objectiveId" className="mb-1.5 block text-xs text-[var(--text-secondary)]">
          Objective (optional)
        </label>
        <select
          id="objectiveId"
          {...register('objectiveId')}
          className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
        >
          <option value="">— none —</option>
          {(objectives.data ?? []).map((o) => (
            <option key={o.id} value={o.id}>
              {o.title}
            </option>
          ))}
        </select>
        <p className="mt-1 text-xs text-[var(--text-secondary)]">
          A linked objective lends the project its milestones.
        </p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input
          label="Repository URL"
          placeholder="https://github.com/…"
          {...register('repositoryUrl')}
          error={errors.repositoryUrl?.message}
        />
        <Input
          label="Demo URL"
          placeholder="https://…"
          {...register('demoUrl')}
          error={errors.demoUrl?.message}
        />
      </div>
      <SkillWeightPicker
        label="Skills this project builds"
        weightLabel="Expected contribution"
        value={skills}
        onChange={setSkills}
      />
      {mutation.isError && (
        <p className="text-xs text-[var(--color-danger)]">Could not save the project.</p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : project ? 'Save changes' : 'Create project'}
        </Button>
      </div>
    </form>
  );
}
