'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  CalendarRange,
  CheckCircle2,
  HelpCircle,
  ListChecks,
  RefreshCw,
  TrendingUp,
} from 'lucide-react';
import {
  useGenerateWeeklyReview,
  useUpdateWeeklyReview,
  useWeeklyReviews,
} from '@/features/analytics/api';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { EmptyState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';
import type { WeeklyReview } from '@/lib/api-types';

export default function WeeklyReviewPage() {
  const list = useWeeklyReviews();
  const generate = useGenerateWeeklyReview();
  const [selected, setSelected] = useState<string | null>(null);

  const reviews = list.data ?? [];
  const current = reviews.find((r) => r.weekStart === selected) ?? reviews[0];

  return (
    <div className="animate-fade-in">
      <Link
        href="/analytics"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Analytics
      </Link>

      <header className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Weekly Review</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            What happened, what moved, what to do next — generated from your data, not an LLM.
          </p>
        </div>
        <Button size="sm" onClick={() => generate.mutate(undefined)} disabled={generate.isPending}>
          <RefreshCw size={14} /> {generate.isPending ? 'Generating…' : 'Generate last week'}
        </Button>
      </header>

      {list.isLoading && <LoadingState />}

      {!list.isLoading && reviews.length === 0 && (
        <EmptyState
          icon={CalendarRange}
          title="No reviews yet"
          hint="A review is generated automatically each Monday — or generate last week's now."
          action={
            <Button onClick={() => generate.mutate(undefined)} disabled={generate.isPending}>
              <RefreshCw size={14} /> Generate last week
            </Button>
          }
        />
      )}

      {reviews.length > 1 && (
        <div className="mb-4 flex flex-wrap gap-1.5">
          {reviews.map((r) => (
            <button
              key={r.id}
              onClick={() => setSelected(r.weekStart)}
              aria-pressed={current?.id === r.id}
              className={`rounded-pill border px-2.5 py-1 font-mono text-[11px] transition-colors ${
                current?.id === r.id
                  ? 'border-[color:rgba(79,209,229,0.4)] text-[var(--accent-cyan)]'
                  : 'border-[var(--border)] text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              {formatDate(r.weekStart)}
            </button>
          ))}
        </div>
      )}

      {current && (
        <ReviewBody
          review={current}
          regenerate={() => generate.mutate(current.weekStart)}
          regenerating={generate.isPending}
        />
      )}
    </div>
  );
}

function ReviewBody({
  review,
  regenerate,
  regenerating,
}: {
  review: WeeklyReview;
  regenerate: () => void;
  regenerating: boolean;
}) {
  const c = review.content;
  const h = c.happened;
  const update = useUpdateWeeklyReview(review.id);
  const [notes, setNotes] = useState(review.userNotes ?? '');
  useEffect(() => setNotes(review.userNotes ?? ''), [review.id, review.userNotes]);

  return (
    <div className="space-y-5">
      <Card>
        <div className="mb-2 flex items-center gap-2">
          <CardLabel>
            {formatDate(review.weekStart)} – {formatDate(review.weekEnd)}
          </CardLabel>
          <Badge tone={review.status === 'reviewed' ? 'success' : 'neutral'}>{review.status}</Badge>
          <span className="font-mono text-[10px] text-[var(--text-secondary)]">
            by {review.generatedBy}
          </span>
        </div>
        {review.summary && <p className="text-sm text-[var(--text-primary)]">{review.summary}</p>}
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
          <Metric
            label="Completed"
            value={`${h.tasksCompleted}/${h.tasksPlanned || h.tasksCompleted}`}
          />
          <Metric label="Evidence" value={String(h.evidenceLogged)} />
          <Metric label="Projects" value={String(h.projectsAdvanced)} />
          <Metric label="Milestones" value={String(h.milestonesHit)} />
          <Metric label="Missed" value={String(c.missed.length)} />
        </div>
      </Card>

      <Section icon={TrendingUp} title="What improved">
        {c.improved.length === 0 ? (
          <Muted>Nothing moved measurably this week.</Muted>
        ) : (
          <ul className="space-y-1.5">
            {c.improved.map((i, idx) => (
              <li key={idx} className="flex items-center justify-between gap-2 text-xs">
                <span className="text-[var(--text-primary)]">
                  <Badge tone="neutral" className="mr-1.5">
                    {i.kind}
                  </Badge>
                  {i.label}
                </span>
                <span className="font-mono text-[11px] text-[var(--color-success)]">
                  +{Math.round(i.delta * 100)}%
                </span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section icon={HelpCircle} title="Why">
        {c.why.length === 0 ? (
          <Muted>No attributable changes.</Muted>
        ) : (
          <ul className="space-y-1.5 text-xs">
            {c.why.map((w, idx) => (
              <li key={idx}>
                <span className="text-[var(--text-primary)]">{w.claim}</span>
                <span className="block text-[11px] text-[var(--text-secondary)]">{w.basis}</span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section icon={ListChecks} title="What was missed">
        {c.missed.length === 0 ? (
          <Muted>Nothing planned slipped — nice.</Muted>
        ) : (
          <ul className="space-y-1.5 text-xs">
            {c.missed.map((m) => (
              <li key={m.taskId} className="flex items-center justify-between gap-2">
                <span className="truncate text-[var(--text-primary)]">{m.title}</span>
                {m.objectiveTitle && (
                  <span className="shrink-0 text-[10px] text-[var(--text-secondary)]">
                    {m.objectiveTitle}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </Section>

      {c.changed.length > 0 && (
        <Section icon={RefreshCw} title="What changed">
          <ul className="space-y-1.5 text-xs">
            {c.changed.map((ch, idx) => (
              <li key={idx} className="text-[var(--text-primary)]">
                {ch.label}: <span className="text-[var(--text-secondary)]">{ch.from}</span> →{' '}
                <span className="text-[var(--accent-cyan)]">{ch.to}</span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      <Section icon={CheckCircle2} title="What to prioritize next">
        {c.next.length === 0 ? (
          <Muted>No open tasks — add some to your objectives.</Muted>
        ) : (
          <ol className="space-y-1.5 text-xs">
            {c.next.map((n) => (
              <li key={n.taskId} className="flex items-center justify-between gap-2">
                <span className="truncate text-[var(--text-primary)]">{n.title}</span>
                <span className="shrink-0 font-mono text-[10px] text-[var(--text-secondary)]">
                  {n.reason}
                </span>
              </li>
            ))}
          </ol>
        )}
        <Link
          href="/today"
          className="mt-3 inline-flex items-center gap-1 text-xs text-[var(--accent-cyan)] hover:underline"
        >
          Plan next week in Today →
        </Link>
      </Section>

      <Card className="p-4">
        <CardLabel>Your notes</CardLabel>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          placeholder="What does the data miss? What are you changing?"
          className="mt-2 w-full resize-y rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
        />
        <div className="mt-2 flex items-center gap-2">
          <Button
            size="sm"
            onClick={() => update.mutate({ userNotes: notes, status: 'reviewed' })}
            disabled={update.isPending}
          >
            {update.isPending ? 'Saving…' : 'Save & mark reviewed'}
          </Button>
          <Button size="sm" variant="ghost" onClick={regenerate} disabled={regenerating}>
            <RefreshCw size={13} /> Regenerate
          </Button>
        </div>
        <p className="mt-2 text-[11px] text-[var(--text-secondary)]">
          Regenerating refreshes the data — your notes and status are kept.
        </p>
      </Card>
    </div>
  );
}

function Section({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof TrendingUp;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
        <Icon size={14} /> {title}
      </h3>
      {children}
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-card border border-[var(--border)] bg-[var(--bg-base)] p-2 text-center">
      <p className="font-mono text-sm text-[var(--text-primary)]">{value}</p>
      <p className="font-mono text-[9px] uppercase tracking-[0.08em] text-[var(--text-secondary)]">
        {label}
      </p>
    </div>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <p className="text-xs text-[var(--text-secondary)]">{children}</p>;
}
