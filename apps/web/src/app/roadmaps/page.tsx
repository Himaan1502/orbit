'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ChevronRight, GitCompare, Map as MapIcon } from 'lucide-react';
import { useRoadmaps } from '@/features/roadmaps/api';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { CapabilityBar } from '@/components/ui/CapabilityBar';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import type { RoadmapSummary } from '@/lib/api-types';

const TIER_HEADINGS: Record<number, string> = {
  1: 'Priority 1 · Primary career focus',
  2: 'Priority 2 · Strategic secondary focus',
  3: 'Priority 3 · Supporting / expansion',
};

export default function RoadmapsPage() {
  const { data: roadmaps, isLoading, isError } = useRoadmaps();
  const [comparing, setComparing] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const router = useRouter();

  const byTier = useMemo(() => {
    const groups: Record<number, RoadmapSummary[]> = { 1: [], 2: [], 3: [] };
    for (const r of roadmaps ?? []) (groups[r.priorityTier] ??= []).push(r);
    return groups;
  }, [roadmaps]);

  function toggle(id: string) {
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }

  return (
    <div className="animate-fade-in pb-20">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Roadmaps</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            Normalized from roadmap.sh — topics mapped to your skills, so overlap and progress are
            explicit. Assess your skills to light up coverage.
          </p>
        </div>
        <Button
          variant={comparing ? 'primary' : 'secondary'}
          onClick={() => {
            setComparing((c) => !c);
            setSelected([]);
          }}
        >
          <GitCompare size={16} /> {comparing ? 'Cancel' : 'Compare'}
        </Button>
      </header>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load the roadmap library." />}
      {roadmaps && roadmaps.length === 0 && (
        <EmptyState icon={MapIcon} title="No roadmaps" hint="Seed data may not have loaded." />
      )}

      <div className="space-y-8">
        {[1, 2, 3].map((tier) =>
          byTier[tier]?.length ? (
            <section key={tier}>
              <h2 className="mb-3 font-mono text-[11px] uppercase tracking-[0.14em] text-[var(--text-secondary)]">
                {TIER_HEADINGS[tier]}
              </h2>
              <ul className="grid gap-3 sm:grid-cols-2">
                {byTier[tier].map((r) => (
                  <li key={r.id}>
                    <RoadmapCard
                      roadmap={r}
                      comparing={comparing}
                      checked={selected.includes(r.id)}
                      onToggle={() => toggle(r.id)}
                    />
                  </li>
                ))}
              </ul>
            </section>
          ) : null
        )}
      </div>

      {comparing && (
        <div className="fixed inset-x-0 bottom-14 z-40 border-t border-[var(--border)] bg-[var(--bg-surface-raised)] px-4 py-3 sm:bottom-0">
          <div className="mx-auto flex max-w-3xl items-center justify-between gap-3">
            <span className="text-xs text-[var(--text-secondary)]">
              {selected.length === 0
                ? 'Pick two or more roadmaps to see their shared skills.'
                : `${selected.length} selected`}
            </span>
            <Button
              disabled={selected.length < 2}
              onClick={() => router.push(`/roadmaps/intersection?ids=${selected.join(',')}`)}
            >
              Compare {selected.length >= 2 ? selected.length : ''} <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function RoadmapCard({
  roadmap: r,
  comparing,
  checked,
  onToggle,
}: {
  roadmap: RoadmapSummary;
  comparing: boolean;
  checked: boolean;
  onToggle: () => void;
}) {
  const inner = (
    <Card className="h-full p-4 transition-colors hover:border-[var(--card-hover-border)]">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="mb-1 flex items-center gap-2">
            <Badge
              tone={r.priorityTier === 1 ? 'cyan' : r.priorityTier === 2 ? 'violet' : 'neutral'}
            >
              {r.tier}
            </Badge>
            {r.adopted && <CardLabel>adopted</CardLabel>}
          </div>
          <p className="text-sm font-medium text-[var(--text-primary)]">{r.name}</p>
          {r.description && (
            <p className="mt-0.5 line-clamp-2 text-xs text-[var(--text-secondary)]">
              {r.description}
            </p>
          )}
        </div>
        {comparing ? (
          <input
            type="checkbox"
            checked={checked}
            readOnly
            tabIndex={-1}
            aria-hidden="true"
            className="pointer-events-none mt-1 h-4 w-4 shrink-0 accent-[var(--accent-cyan)]"
          />
        ) : (
          <ChevronRight size={16} className="mt-0.5 shrink-0 text-[var(--text-secondary)]" />
        )}
      </div>
      <div className="mt-3 flex items-center gap-3">
        <CapabilityBar value={r.coverage} className="flex-1" />
        <span className="shrink-0 font-mono text-[10px] text-[var(--text-secondary)]">
          {r.skillCount} skills
        </span>
      </div>
    </Card>
  );

  if (comparing) {
    return (
      <button
        type="button"
        onClick={onToggle}
        aria-pressed={checked}
        aria-label={`Compare ${r.name}`}
        className="block w-full text-left"
      >
        {inner}
      </button>
    );
  }
  return (
    <Link href={`/roadmaps/${r.id}`} className="block h-full">
      {inner}
    </Link>
  );
}
