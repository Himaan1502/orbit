'use client';

import { X } from 'lucide-react';
import { useSkills } from '@/features/skills/api';

export interface SkillWeight {
  skillId: string;
  weight: number;
}

/**
 * Pick skills and give each a 0–1 weight — the shared control behind a project's
 * `expected_contribution` and an evidence item's `contribution_weight`. Controlled.
 */
export function SkillWeightPicker({
  label,
  weightLabel,
  value,
  onChange,
}: {
  label: string;
  weightLabel: string;
  value: SkillWeight[];
  onChange: (next: SkillWeight[]) => void;
}) {
  const skills = useSkills();
  const catalog = skills.data ?? [];
  const nameOf = (id: string) => catalog.find((s) => s.id === id)?.canonicalName ?? id;
  const chosen = new Set(value.map((v) => v.skillId));
  const available = catalog.filter((s) => !chosen.has(s.id));

  return (
    <div>
      <label className="mb-1.5 block text-xs text-[var(--text-secondary)]">{label}</label>

      {value.length > 0 && (
        <ul className="mb-2 space-y-1.5">
          {value.map((v) => (
            <li key={v.skillId} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate text-xs text-[var(--text-primary)]">
                {nameOf(v.skillId)}
              </span>
              <input
                type="number"
                min={0}
                max={1}
                step={0.05}
                aria-label={`${weightLabel} for ${nameOf(v.skillId)}`}
                value={v.weight}
                onChange={(e) =>
                  onChange(
                    value.map((x) =>
                      x.skillId === v.skillId ? { ...x, weight: clamp(Number(e.target.value)) } : x
                    )
                  )
                }
                className="h-8 w-20 rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-2 text-xs text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
              />
              <button
                type="button"
                aria-label={`Remove ${nameOf(v.skillId)}`}
                onClick={() => onChange(value.filter((x) => x.skillId !== v.skillId))}
                className="text-[var(--text-secondary)] hover:text-[var(--color-danger)]"
              >
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <select
        aria-label={`Add a skill (${weightLabel})`}
        value=""
        onChange={(e) => {
          if (e.target.value) onChange([...value, { skillId: e.target.value, weight: 0.5 }]);
        }}
        disabled={available.length === 0}
        className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none disabled:opacity-50"
      >
        <option value="">{available.length ? '+ add a skill…' : 'all skills added'}</option>
        {available.map((s) => (
          <option key={s.id} value={s.id}>
            {s.canonicalName}
          </option>
        ))}
      </select>
      <p className="mt-1 text-xs text-[var(--text-secondary)]">{weightLabel} — 0 to 1.</p>
    </div>
  );
}

function clamp(n: number): number {
  if (Number.isNaN(n)) return 0;
  return Math.max(0, Math.min(1, n));
}
