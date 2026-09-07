'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import type { SkillDimensions } from '@/lib/api-types';
import { useAssessSkill } from './api';

const DIMENSIONS: { key: keyof SkillDimensions; label: string; help: string }[] = [
  { key: 'knowledge', label: 'Knowledge', help: 'Concepts, theory, vocabulary' },
  { key: 'practical', label: 'Practical', help: 'Hands-on, can build with it' },
  { key: 'evidence', label: 'Evidence', help: 'Artefacts that prove it (self-reported in M3)' },
  { key: 'interview', label: 'Interview', help: 'Could explain it under questioning' },
  { key: 'production', label: 'Production', help: 'Run it in production, own the failure modes' },
];

const score = z.coerce.number().min(0).max(1);
const schema = z.object({
  knowledge: score,
  practical: score,
  evidence: score,
  interview: score,
  production: score,
});
type FormValues = z.infer<typeof schema>;

export function AssessSkillForm({
  skillId,
  current,
  onDone,
}: {
  skillId: string;
  current: SkillDimensions | null;
  onDone: () => void;
}) {
  const assess = useAssessSkill(skillId);
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: current ?? {
      knowledge: 0.3,
      practical: 0.3,
      evidence: 0.2,
      interview: 0.3,
      production: 0.2,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    await assess.mutateAsync(values);
    onDone();
  });

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <p className="text-xs text-[var(--text-secondary)]">
        Rate each dimension from 0 to 1. The overall score is a fixed weighted blend (Blueprint §23)
        and is recorded as <span className="font-mono">self-reported</span>.
      </p>
      {DIMENSIONS.map((d) => (
        <div key={d.key}>
          <div className="mb-1 flex items-baseline justify-between">
            <label htmlFor={d.key} className="text-xs text-[var(--text-primary)]">
              {d.label}
              <span className="ml-1.5 text-[10px] text-[var(--text-secondary)]">{d.help}</span>
            </label>
            <span className="font-mono text-[11px] text-[var(--accent-cyan)]">
              {Number(watch(d.key) ?? 0).toFixed(2)}
            </span>
          </div>
          <input
            id={d.key}
            type="range"
            min={0}
            max={1}
            step={0.05}
            {...register(d.key)}
            className="w-full accent-[var(--accent-cyan)]"
          />
          {errors[d.key] && (
            <p className="mt-1 text-xs text-[var(--color-danger)]">{errors[d.key]?.message}</p>
          )}
        </div>
      ))}
      {assess.isError && (
        <p className="text-xs text-[var(--color-danger)]">Could not record the assessment.</p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={assess.isPending}>
          {assess.isPending ? 'Saving…' : 'Save assessment'}
        </Button>
      </div>
    </form>
  );
}
