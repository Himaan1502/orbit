'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import { ChevronRight, Sparkles } from 'lucide-react';
import { useSkills } from '@/features/skills/api';
import { Card } from '@/components/ui/Card';
import { CapabilityBar } from '@/components/ui/CapabilityBar';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import type { SkillSummary } from '@/lib/api-types';

export default function SkillsPage() {
  const { data: skills, isLoading, isError } = useSkills();

  const byCategory = useMemo(() => groupByCategory(skills ?? []), [skills]);
  const assessed = (skills ?? []).filter((s) => s.capability != null).length;

  return (
    <div className="animate-fade-in">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Skills</h1>
        <p className="text-sm text-[var(--text-secondary)]">
          A shared catalog of capabilities. Self-assess where you stand — scores are self-reported
          until evidence and interviews back them (M4+).
        </p>
        {skills && skills.length > 0 && (
          <p className="mt-2 font-mono text-[11px] text-[var(--text-secondary)]">
            {assessed} / {skills.length} self-assessed
          </p>
        )}
      </header>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load the skill catalog." />}

      {skills && skills.length === 0 && (
        <EmptyState
          icon={Sparkles}
          title="The catalog is empty"
          hint="Seed data may not have loaded."
        />
      )}

      <div className="space-y-8">
        {byCategory.map(([category, group]) => (
          <section key={category}>
            <h2 className="mb-2 font-mono text-[11px] uppercase tracking-[0.14em] text-[var(--text-secondary)]">
              {category}
            </h2>
            <ul className="grid gap-2 sm:grid-cols-2">
              {group.map((s) => (
                <li key={s.id}>
                  <Link href={`/skills/${s.id}`} className="block">
                    <Card className="p-4 transition-colors hover:border-[var(--card-hover-border)]">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-[var(--text-primary)]">
                            {s.canonicalName}
                          </p>
                          {s.description && (
                            <p className="mt-0.5 line-clamp-2 text-xs text-[var(--text-secondary)]">
                              {s.description}
                            </p>
                          )}
                        </div>
                        <ChevronRight
                          size={16}
                          className="mt-0.5 shrink-0 text-[var(--text-secondary)]"
                        />
                      </div>
                      <div className="mt-3">
                        <CapabilityBar value={s.capability} />
                      </div>
                    </Card>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
}

function groupByCategory(skills: SkillSummary[]): [string, SkillSummary[]][] {
  const groups: Record<string, SkillSummary[]> = {};
  for (const s of skills) {
    const key = s.category || 'Other';
    (groups[key] ??= []).push(s);
  }
  return Object.entries(groups).sort(([a], [b]) => a.localeCompare(b));
}
