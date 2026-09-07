'use client';

import { useMemo, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { useSkills } from '@/features/skills/api';
import type { SkillSummary } from '@/lib/api-types';
import { useCreateCareerTarget, type CareerTargetSkillInput } from './api';

/**
 * Client-side starter template (ADR-020 §4) — career targets are per-user and
 * created through the API, not seeded, but the platform offers this as a
 * one-click starting point for the primary persona.
 */
const PLATFORM_ENGINEER_TEMPLATE: { name: string; importance: number; interviewWeight: number }[] =
  [
    { name: 'Kubernetes', importance: 0.9, interviewWeight: 0.7 },
    { name: 'Kubernetes Production', importance: 0.9, interviewWeight: 0.5 },
    { name: 'Observability', importance: 0.8, interviewWeight: 0.6 },
    { name: 'Terraform', importance: 0.8, interviewWeight: 0.5 },
    { name: 'CI/CD', importance: 0.7, interviewWeight: 0.5 },
    { name: 'System Design', importance: 0.8, interviewWeight: 0.9 },
    { name: 'Distributed Systems', importance: 0.7, interviewWeight: 0.8 },
    { name: 'MLOps Platform', importance: 0.7, interviewWeight: 0.4 },
    { name: 'Model Serving', importance: 0.6, interviewWeight: 0.4 },
    { name: 'DSA & Problem Solving', importance: 0.6, interviewWeight: 0.9 },
  ];

interface Row extends CareerTargetSkillInput {
  importance: number;
  interviewWeight: number;
}

export function CareerTargetForm({ onDone }: { onDone: (id: string) => void }) {
  const skills = useSkills();
  const create = useCreateCareerTarget();

  const [name, setName] = useState('');
  const [roleFamily, setRoleFamily] = useState('');
  const [priority, setPriority] = useState(3);
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);

  const byName = useMemo(() => {
    const m = new Map<string, SkillSummary>();
    for (const s of skills.data ?? []) m.set(s.canonicalName, s);
    return m;
  }, [skills.data]);

  const nameOf = (skillId: string) =>
    (skills.data ?? []).find((s) => s.id === skillId)?.canonicalName ?? skillId;

  const available = (skills.data ?? []).filter((s) => !rows.some((r) => r.skillId === s.id));

  function addSkill(skillId: string) {
    if (!skillId) return;
    setRows((r) => [...r, { skillId, importance: 0.7, interviewWeight: 0.5 }]);
  }

  function applyTemplate() {
    if (!name) setName('Platform Engineer / AI Infrastructure');
    if (!roleFamily) setRoleFamily('Infrastructure');
    const templated: Row[] = [];
    for (const t of PLATFORM_ENGINEER_TEMPLATE) {
      const s = byName.get(t.name);
      if (s)
        templated.push({
          skillId: s.id,
          importance: t.importance,
          interviewWeight: t.interviewWeight,
        });
    }
    setRows(templated);
  }

  async function submit() {
    setError(null);
    if (!name.trim()) {
      setError('Give the target a name.');
      return;
    }
    try {
      const target = await create.mutateAsync({
        name: name.trim(),
        roleFamily: roleFamily.trim() || undefined,
        priority,
        skills: rows.map((r) => ({
          skillId: r.skillId,
          importance: r.importance,
          interviewWeight: r.interviewWeight,
        })),
      });
      onDone(target.id);
    } catch {
      setError('Could not create the career target.');
    }
  }

  return (
    <div className="space-y-4">
      <Input
        label="Name"
        placeholder="e.g. Platform Engineer / AI Infrastructure"
        value={name}
        onChange={(e) => setName(e.target.value)}
        autoFocus
      />
      <div className="grid grid-cols-2 gap-3">
        <Input
          label="Role family"
          placeholder="Infrastructure"
          value={roleFamily}
          onChange={(e) => setRoleFamily(e.target.value)}
        />
        <Input
          label="Priority (1–5)"
          type="number"
          min={1}
          max={5}
          value={priority}
          onChange={(e) => setPriority(Number(e.target.value))}
          hint="5 = highest"
        />
      </div>

      <div>
        <div className="mb-1.5 flex items-baseline justify-between">
          <span className="text-xs text-[var(--text-secondary)]">Skills for this target</span>
          <button
            type="button"
            onClick={applyTemplate}
            className="text-[11px] text-[var(--accent-cyan)] hover:underline"
          >
            Use Platform Engineer template
          </button>
        </div>

        <select
          value=""
          onChange={(e) => addSkill(e.target.value)}
          className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
        >
          <option value="">+ add a skill…</option>
          {available.map((s) => (
            <option key={s.id} value={s.id}>
              {s.category} — {s.canonicalName}
            </option>
          ))}
        </select>

        {rows.length > 0 && (
          <ul className="mt-3 space-y-3">
            {rows.map((r, i) => (
              <li key={r.skillId} className="rounded-button border border-[var(--border)] p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-medium text-[var(--text-primary)]">
                    {nameOf(r.skillId)}
                  </span>
                  <button
                    type="button"
                    onClick={() => setRows((rs) => rs.filter((_, j) => j !== i))}
                    aria-label={`Remove ${nameOf(r.skillId)}`}
                    className="text-[var(--text-secondary)] hover:text-[var(--color-danger)]"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
                <Slider
                  label="Importance"
                  value={r.importance}
                  onChange={(v) =>
                    setRows((rs) => rs.map((x, j) => (j === i ? { ...x, importance: v } : x)))
                  }
                />
                <Slider
                  label="Interview weight"
                  value={r.interviewWeight}
                  onChange={(v) =>
                    setRows((rs) => rs.map((x, j) => (j === i ? { ...x, interviewWeight: v } : x)))
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </div>

      {error && <p className="text-xs text-[var(--color-danger)]">{error}</p>}

      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={() => onDone('')}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create target'}
        </Button>
      </div>
    </div>
  );
}

function Slider({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="mt-1.5">
      <div className="flex items-baseline justify-between">
        <span className="text-[11px] text-[var(--text-secondary)]">{label}</span>
        <span className="font-mono text-[11px] text-[var(--accent-cyan)]">{value.toFixed(2)}</span>
      </div>
      <input
        type="range"
        min={0}
        max={1}
        step={0.05}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-[var(--accent-cyan)]"
      />
    </div>
  );
}
