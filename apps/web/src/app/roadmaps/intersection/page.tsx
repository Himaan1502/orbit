'use client';

import { Suspense, useMemo } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ArrowLeft, ArrowUpRight, Layers } from 'lucide-react';
import { useIntersection } from '@/features/roadmaps/api';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { CapabilityBar } from '@/components/ui/CapabilityBar';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import type { SharedSkill } from '@/lib/api-types';

export default function RoadmapIntersectionPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <IntersectionContent />
    </Suspense>
  );
}

const RELEVANCE_TONE = { HIGH: 'cyan', MEDIUM: 'violet', LOW: 'neutral' } as const;

function IntersectionContent() {
  const params = useSearchParams();
  const ids = useMemo(
    () =>
      (params.get('ids') ?? '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    [params]
  );
  const { data, isLoading, isError, error } = useIntersection(ids);

  const notEnough =
    isError && (error as { response?: { status?: number } })?.response?.status === 400;

  return (
    <div className="animate-fade-in">
      <Link
        href="/roadmaps"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Roadmaps
      </Link>

      <header className="mb-6">
        <h1 className="flex items-center gap-2 text-2xl font-semibold text-[var(--text-primary)]">
          <Layers size={22} /> Roadmap Intersection
        </h1>
        <p className="text-sm text-[var(--text-secondary)]">
          The skills that show up across the roadmaps you selected — ranked so the highest-leverage
          shared skill is first.
        </p>
      </header>

      {isLoading && <LoadingState />}
      {notEnough && (
        <EmptyState
          icon={Layers}
          title="Pick at least two roadmaps"
          hint="Go back to the library, hit Compare, and select two or more — or adopt two roadmaps."
        />
      )}
      {isError && !notEnough && <ErrorState message="Could not compute the intersection." />}

      {data && (
        <>
          <div className="mb-4 flex flex-wrap gap-2">
            {data.roadmaps.map((r) => (
              <Link key={r.id} href={`/roadmaps/${r.id}`}>
                <Badge tone="neutral">
                  {r.tier} · {r.name}
                </Badge>
              </Link>
            ))}
          </div>

          {data.sharedSkills.length === 0 ? (
            <EmptyState
              icon={Layers}
              title="No shared skills"
              hint="These roadmaps don't overlap in the catalog."
            />
          ) : (
            <ul className="space-y-2">
              {data.sharedSkills.map((s) => (
                <SharedSkillRow key={s.skillId} skill={s} roadmaps={data.roadmaps} />
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

function SharedSkillRow({
  skill: s,
  roadmaps,
}: {
  skill: SharedSkill;
  roadmaps: { id: string; name: string; tier: string }[];
}) {
  const inSet = new Set(s.contributesTo.map((r) => r.id));
  return (
    <Card className="p-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="min-w-0 flex-1">
          <Link
            href={`/skills/${s.skillId}`}
            className="inline-flex items-center gap-1 text-sm font-medium text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
          >
            {s.name}
            <ArrowUpRight size={12} className="text-[var(--text-secondary)]" />
          </Link>
          <p className="mt-0.5 text-xs text-[var(--text-secondary)]">{s.recommendedAction}</p>
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {roadmaps.map((r) => (
              <span
                key={r.id}
                className={
                  'rounded-pill border px-1.5 py-0.5 text-[10px] ' +
                  (inSet.has(r.id)
                    ? 'border-[color:rgba(79,209,229,0.4)] text-[var(--accent-cyan)]'
                    : 'border-[var(--border)] text-[var(--text-secondary)] line-through opacity-40')
                }
              >
                {r.tier} {r.name}
              </span>
            ))}
            <span className="ml-1 font-mono text-[10px] text-[var(--text-secondary)]">
              {s.roadmapCount}/{roadmaps.length}
            </span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-3 sm:w-64">
          {s.careerRelevance && (
            <Badge
              tone={RELEVANCE_TONE[s.careerRelevance as keyof typeof RELEVANCE_TONE] ?? 'neutral'}
            >
              {s.careerRelevance}
            </Badge>
          )}
          <div className="flex-1">
            <CapabilityBar value={s.capability} />
          </div>
          <span
            title="graph leverage — how much other shared work this unblocks"
            className="font-mono text-[10px] text-[var(--accent-cyan)]"
          >
            lev {s.leverage.toFixed(2)}
          </span>
        </div>
      </div>
    </Card>
  );
}
