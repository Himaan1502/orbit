'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { SkillWeightPicker, type SkillWeight } from '@/components/ui/SkillWeightPicker';
import { useProjects } from '@/features/projects/api';
import type { Evidence } from '@/lib/api-types';
import { useCreateEvidence, useUpdateEvidence, type EvidenceInput } from './api';

export const EVIDENCE_TYPES = [
  'project',
  'repository',
  'deployment',
  'benchmark',
  'document',
  'certification',
  'assessment',
  'talk',
  'other',
] as const;

const schema = z.object({
  type: z.enum(EVIDENCE_TYPES),
  title: z.string().trim().min(1, 'Title is required.').max(200),
  description: z.string().trim().max(5000).optional(),
  sourceUrl: z.string().trim().url('Enter a valid URL.').max(2000).optional().or(z.literal('')),
  externalIdentifier: z.string().trim().max(120).optional(),
  occurredAt: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export function EvidenceForm({
  evidence,
  presetProjectId,
  presetSkillId,
  onDone,
}: {
  evidence?: Evidence;
  presetProjectId?: string;
  presetSkillId?: string;
  onDone: () => void;
}) {
  const create = useCreateEvidence();
  const update = useUpdateEvidence(evidence?.id ?? '');
  const mutation = evidence ? update : create;
  const projects = useProjects();

  const [skills, setSkills] = useState<SkillWeight[]>(
    evidence
      ? evidence.skills.map((s) => ({ skillId: s.skillId, weight: s.contributionWeight }))
      : presetSkillId
        ? [{ skillId: presetSkillId, weight: 0.5 }]
        : []
  );
  const [projectIds, setProjectIds] = useState<string[]>(
    evidence ? evidence.projects.map((p) => p.projectId) : presetProjectId ? [presetProjectId] : []
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: evidence?.type ?? 'repository',
      title: evidence?.title ?? '',
      description: evidence?.description ?? '',
      sourceUrl: evidence?.sourceUrl ?? '',
      externalIdentifier: evidence?.externalIdentifier ?? '',
      occurredAt: evidence?.occurredAt ? evidence.occurredAt.slice(0, 10) : '',
    },
  });

  const projectList = projects.data ?? [];
  const nameOf = (id: string) => projectList.find((p) => p.id === id)?.name ?? id;
  const unpicked = projectList.filter((p) => !projectIds.includes(p.id));

  const onSubmit = handleSubmit(async (values) => {
    const payload: EvidenceInput = {
      type: values.type,
      title: values.title,
      description: values.description || undefined,
      sourceUrl: values.sourceUrl || null,
      externalIdentifier: values.externalIdentifier || null,
      occurredAt: values.occurredAt
        ? new Date(`${values.occurredAt}T12:00:00`).toISOString()
        : null,
      skills: skills.map((s) => ({ skillId: s.skillId, contributionWeight: s.weight })),
      projects: projectIds.map((projectId) => ({ projectId })),
    };
    await mutation.mutateAsync(payload);
    onDone();
  });

  return (
    <form onSubmit={onSubmit} className="space-y-3">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label htmlFor="type" className="mb-1.5 block text-xs text-[var(--text-secondary)]">
            Type
          </label>
          <select
            id="type"
            {...register('type')}
            className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
          >
            {EVIDENCE_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <Input label="When (optional)" type="date" {...register('occurredAt')} />
      </div>
      <Input
        label="Title"
        placeholder="e.g. Shipped the multi-tenant operator to prod"
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
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input
          label="Source URL"
          placeholder="https://…"
          {...register('sourceUrl')}
          error={errors.sourceUrl?.message}
        />
        <Input
          label="External identifier"
          placeholder="PR #, cert ID, DOI…"
          {...register('externalIdentifier')}
          error={errors.externalIdentifier?.message}
        />
      </div>

      <SkillWeightPicker
        label="Skills this demonstrates"
        weightLabel="Contribution weight"
        value={skills}
        onChange={setSkills}
      />

      <div>
        <label className="mb-1.5 block text-xs text-[var(--text-secondary)]">
          Projects (optional)
        </label>
        {projectIds.length > 0 && (
          <ul className="mb-2 flex flex-wrap gap-1.5">
            {projectIds.map((id) => (
              <li key={id}>
                <button
                  type="button"
                  onClick={() => setProjectIds(projectIds.filter((x) => x !== id))}
                  className="inline-flex items-center gap-1 rounded-pill border border-[var(--border)] px-2 py-0.5 text-xs text-[var(--text-primary)] hover:border-[var(--color-danger)]"
                >
                  {nameOf(id)} <X size={11} />
                </button>
              </li>
            ))}
          </ul>
        )}
        <select
          aria-label="Add a project"
          value=""
          onChange={(e) => e.target.value && setProjectIds([...projectIds, e.target.value])}
          disabled={unpicked.length === 0}
          className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none disabled:opacity-50"
        >
          <option value="">{projectList.length ? '+ link a project…' : 'no projects yet'}</option>
          {unpicked.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <p className="mt-1 text-xs text-[var(--text-secondary)]">
          Evidence on a <em>completed</em> project unlocks its demonstrated contribution.
        </p>
      </div>

      {mutation.isError && (
        <p className="text-xs text-[var(--color-danger)]">Could not save the evidence.</p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : evidence ? 'Save changes' : 'Log evidence'}
        </Button>
      </div>
    </form>
  );
}
