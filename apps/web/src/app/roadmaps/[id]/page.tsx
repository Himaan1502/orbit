'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import { ArrowLeft, ArrowUpRight, Compass, ExternalLink } from 'lucide-react';
import { useRoadmap, useAdoptRoadmap, useAbandonRoadmap } from '@/features/roadmaps/api';
import { buildTopicTree, type TopicNode } from '@/features/roadmaps/tree';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { CapabilityBar } from '@/components/ui/CapabilityBar';
import { ErrorState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';

const STATUS_TONE = {
  covered: 'success',
  in_progress: 'cyan',
  not_started: 'neutral',
} as const;

export default function RoadmapDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const roadmap = useRoadmap(id);
  const adopt = useAdoptRoadmap(id);
  const abandon = useAbandonRoadmap(id);

  const tree = useMemo(() => buildTopicTree(roadmap.data?.topics ?? []), [roadmap.data]);

  if (roadmap.isLoading) return <LoadingState />;
  if (roadmap.isError || !roadmap.data)
    return <ErrorState message="This roadmap could not be found." />;

  const r = roadmap.data;

  return (
    <div className="animate-fade-in">
      <Link
        href="/roadmaps"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Roadmaps
      </Link>

      <Card className="mb-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
          <ProgressRing
            value={r.coverage}
            size={104}
            label={`${Math.round(r.coverage * 100)}% coverage`}
          />
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <Badge
                tone={r.priorityTier === 1 ? 'cyan' : r.priorityTier === 2 ? 'violet' : 'neutral'}
              >
                {r.tier}
              </Badge>
              <CardLabel>
                {r.coveredTopicCount}/{r.topicCount} topics covered
              </CardLabel>
            </div>
            <h1 className="text-xl font-semibold text-[var(--text-primary)]">{r.name}</h1>
            {r.description && (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">{r.description}</p>
            )}
            <a
              href={r.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              title={`Open ${r.name} on ${r.sourceName} (opens in a new tab)`}
              className="mt-3 inline-flex items-center gap-1.5 text-xs text-[var(--text-secondary)] transition-colors hover:text-[var(--accent-cyan)]"
            >
              View on {r.sourceName}
              <ExternalLink size={12} aria-hidden="true" />
            </a>
          </div>
          <div className="shrink-0">
            {r.adoption ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => abandon.mutate()}
                disabled={abandon.isPending}
              >
                Adopted{r.adoption.targetDate ? ` · by ${formatDate(r.adoption.targetDate)}` : ''} ·
                Remove
              </Button>
            ) : (
              <Button size="sm" onClick={() => adopt.mutate({})} disabled={adopt.isPending}>
                Adopt roadmap
              </Button>
            )}
          </div>
        </div>
      </Card>

      {r.recommendedNext.length > 0 && (
        <section className="mb-6">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
            <Compass size={15} /> Recommended next
          </h2>
          <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {r.recommendedNext.map((n) => (
              <li key={n.topicId}>
                <Card className="h-full p-3">
                  <p className="text-sm font-medium text-[var(--text-primary)]">{n.name}</p>
                  <p className="mt-1 text-xs text-[var(--text-secondary)]">{n.reason}</p>
                  <p className="mt-2 font-mono text-[10px] text-[var(--text-secondary)]">
                    ready {Math.round(n.readiness * 100)}% · gap {n.weightedGap.toFixed(2)}
                  </p>
                </Card>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="mb-3 text-sm font-semibold text-[var(--text-primary)]">Topics</h2>
        <div className="space-y-2">
          {tree.map((t) => (
            <TopicRow key={t.id} node={t} />
          ))}
        </div>
      </section>
    </div>
  );
}

function TopicRow({ node: t }: { node: TopicNode }) {
  return (
    <Card className="p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge tone={STATUS_TONE[t.status]}>{t.status.replace('_', ' ')}</Badge>
            <p className="text-sm font-medium text-[var(--text-primary)]">{t.name}</p>
          </div>
          <ul className="mt-2 space-y-1">
            {t.skills.map((s) => (
              <li key={s.skillId}>
                <Link
                  href={`/skills/${s.skillId}`}
                  className="flex items-center justify-between gap-3 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                >
                  <span className="inline-flex items-center gap-1">
                    {s.name}
                    <ArrowUpRight size={11} className="text-[var(--text-secondary)]" />
                  </span>
                  <span className="w-28 shrink-0">
                    <CapabilityBar value={s.capability} />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          {t.children.map((c) => (
            <div key={c.id} className="mt-2 border-l border-[var(--border)] pl-3">
              <TopicRow node={c} />
            </div>
          ))}
        </div>
        <span className="shrink-0 font-mono text-[10px] text-[var(--text-secondary)]">
          {Math.round(t.coverage * 100)}%
        </span>
      </div>
    </Card>
  );
}
