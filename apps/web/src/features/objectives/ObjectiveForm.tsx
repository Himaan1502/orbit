'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Field';
import { useCreateObjective } from './api';

const schema = z.object({
  title: z.string().trim().min(1, 'Title is required.').max(200),
  description: z.string().trim().max(5000).optional(),
  priority: z.coerce.number().int().min(1).max(5),
  targetDate: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export function ObjectiveForm({ onDone }: { onDone: () => void }) {
  const create = useCreateObjective();
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
