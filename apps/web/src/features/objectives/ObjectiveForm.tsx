'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { useCareerTargets } from '@/features/career/api';
import { useCreateObjective } from './api';

const schema = z.object({
  title: z.string().trim().min(1, 'Title is required.').max(200),
  description: z.string().trim().max(5000).optional(),
  priority: z.coerce.number().int().min(1).max(5),
  targetDate: z.string().optional(),
  careerTargetId: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export function ObjectiveForm({ onDone }: { onDone: () => void }) {
  const create = useCreateObjective();
  const careerTargets = useCareerTargets();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { priority: 3 },
  });

  const onSubmit = handleSubmit(async (values) => {
    await create.mutateAsync({
      title: values.title,
      description: values.description || undefined,
      priority: values.priority,
      targetDate: values.targetDate || null,
      careerTargetId: values.careerTargetId || null,
    });
    onDone();
  });

  return (
    <form onSubmit={onSubmit} className="space-y-3">
      <Input
        label="Title"
        placeholder="e.g. Reach production readiness on Kubernetes"
        {...register('title')}
        error={errors.title?.message}
        autoFocus
      />
      <Textarea
        label="Description"
        placeholder="What outcome does this represent?"
        {...register('description')}
        error={errors.description?.message}
      />
      <div className="grid grid-cols-2 gap-3">
        <Input
          label="Priority (1–5)"
          type="number"
          min={1}
          max={5}
          {...register('priority')}
          error={errors.priority?.message}
          hint="5 = highest"
        />
        <Input label="Target date" type="date" {...register('targetDate')} />
      </div>
      {(careerTargets.data?.length ?? 0) > 0 && (
        <div>
          <label
            htmlFor="careerTargetId"
            className="mb-1.5 block text-xs text-[var(--text-secondary)]"
          >
            Career target (optional)
          </label>
          <select
            id="careerTargetId"
            {...register('careerTargetId')}
            className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
          >
            <option value="">— none —</option>
            {careerTargets.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-[var(--text-secondary)]">
            Linking an objective feeds its tasks the career &amp; skill priority factors.
          </p>
        </div>
      )}
      {create.isError && (
        <p className="text-xs text-[var(--color-danger)]">Could not create the objective.</p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create objective'}
        </Button>
      </div>
    </form>
  );
}
