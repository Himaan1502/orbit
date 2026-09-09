'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Activity, ArrowRight, Briefcase, Sparkles } from 'lucide-react';
import {
  useCareerAnalytics,
  useExecutionAnalytics,
  useSkillAnalytics,
} from '@/features/analytics/api';
import { CompareChart, Sparkline, TrendChart } from '@/features/analytics/charts';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import type { CareerAnalytics, ExecutionAnalytics, SkillAnalytics } from '@/lib/api-types';

const TABS = ['Execution', 'Career', 'Skills'] as const;
type Tab = (typeof TABS)[number];

const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>('Execution');

  return (
    <div className="animate-fade-in">
      <header className="mb-5 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Analytics</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            Measurable progress over time — every series is derived from your real data.
          </p>
        </div>
        <Link href="/analytics/review">
          <Button variant="secondary" size="sm">
            Weekly Review <ArrowRight size={14} />
          </Button>
        </Link>
      </header>

      <div className="mb-6 flex gap-1 border-b border-[var(--border)]">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            aria-pressed={tab === t}
            className={`-mb-px border-b-2 px-3 py-2 text-sm transition-colors ${
              tab === t
                ? 'border-[var(--accent-violet)] text-[var(--text-primary)]'
                : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Execution' && <ExecutionPanel />}
      {tab === 'Career' && <CareerPanel />}
      {tab === 'Skills' && <SkillsPanel />}
    </div>
  );
}

function NoData({ hint }: { hint: string }) {
  return <EmptyState icon={Activity} title="Nothing to chart yet" hint={hint} />;
}

// ===================================================================

function ExecutionPanel() {
  const { data, isLoading, isError } = useExecutionAnalytics();
  if (isLoading) return <LoadingState />;
  if (isError || !data) return <ErrorState message="Could not load execution analytics." />;
  if (data.status === 'no-data-yet')
    return <NoData hint="Complete a few tasks and generate a daily plan — trends appear here." />;

  const d = data as ExecutionAnalytics;
  const weekly = d.weekly.map((w) => ({ ...w, week: shortDate(w.weekStart) }));

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-3">
        <Stat label="Completion rate" value={pct(d.completionRate)} />
        <Stat label="Throughput" value={`${d.throughputPerWeek.toFixed(1)} / wk`} />
        <Stat label="Blocked now" value={String(d.statusCounts.blocked)} />
      </div>

      <Card>
        <CardLabel>Planned vs completed</CardLabel>
        <div className="mt-3">
          <TrendChart
            data={weekly}
            xKey="week"
            series={[
              { key: 'completed', label: 'Completed', tone: 'primary' },
              { key: 'planned', label: 'Planned', tone: 'secondary' },
            ]}
          />
        </div>
      </Card>

      <Card>
        <CardLabel>Focused minutes / week</CardLabel>
        <div className="mt-3">
          <CompareChart
            data={weekly}
            xKey="week"
            series={[{ key: 'focusedMinutes', label: 'Focused minutes', tone: 'primary' }]}
          />
        </div>
      </Card>

      {d.objectiveProgress.length > 0 && (
        <Card>
          <CardLabel>Objective progress</CardLabel>
          <ul className="mt-3 space-y-3">
            {d.objectiveProgress.map((o) => (
              <li key={o.objectiveId} className="flex items-center gap-3">
                <Link
                  href={`/objectives/${o.objectiveId}`}
                  className="w-40 shrink-0 truncate text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                >
                  {o.title}
                </Link>
                <div className="min-w-0 flex-1">
                  <Sparkline data={o.points.map((p) => ({ v: p.progress }))} yKey="v" />
                </div>
                <span className="shrink-0 font-mono text-[11px] text-[var(--text-secondary)]">
                  {Math.round((o.points.at(-1)?.progress ?? 0) * 100)}%
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {d.blockers.length > 0 && (
        <Card className="p-4">
          <h3 className="mb-2 text-xs font-semibold text-[var(--text-primary)]">
            Recurring blockers
          </h3>
          <ul className="space-y-1.5">
            {d.blockers.map((b) => (
              <li
                key={b.taskId}
                className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)]"
              >
                <span className="truncate">{b.title}</span>
                <span className="shrink-0 font-mono text-[10px] text-[var(--color-warning)]">
                  blocked {b.daysBlocked}d
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}

// ===================================================================

function CareerPanel() {
  const { data, isLoading } = useCareerAnalytics();
  if (isLoading) return <LoadingState />;
  if (!data)
    return (
      <NoData hint="Set a career target and self-assess some skills to see readiness over time." />
    );

  const d = data as CareerAnalytics;
  if (d.status === 'no-data-yet')
    return (
      <NoData hint="Self-assess your target's skills — the readiness trend needs assessments." />
    );

  const trend = d.readinessTrend.map((p) => ({
    at: shortDate(p.at),
    overall: Math.round(p.overall * 100),
    capability: Math.round(p.skillCapability * 100),
  }));
  const evidence = d.evidenceGrowth.map((w) => ({ ...w, week: shortDate(w.weekStart) }));
  const progression = d.targetProgression.map((p) => ({
    at: shortDate(p.at),
    covered: p.skillsAtThreshold,
  }));

  return (
    <div className="space-y-6">
      <Card>
        <div className="flex items-center gap-2">
          <Briefcase size={14} className="text-[var(--text-secondary)]" />
          <CardLabel>{d.targetName} — readiness</CardLabel>
        </div>
        <div className="mt-3">
          <TrendChart
            data={trend}
            xKey="at"
            yDomain={[0, 100]}
            series={[
              { key: 'overall', label: 'Readiness', tone: 'primary' },
              { key: 'capability', label: 'Skill capability', tone: 'secondary' },
            ]}
          />
        </div>
        <p className="mt-2 text-[11px] text-[var(--text-secondary)]">
          Recomputed at each assessment with the same formula — a step between points, not a guess.
        </p>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardLabel>Evidence growth</CardLabel>
          <div className="mt-3">
            <CompareChart
              data={evidence}
              xKey="week"
              series={[{ key: 'count', label: 'Evidence logged', tone: 'primary' }]}
              height={160}
            />
          </div>
        </Card>
        <Card>
          <CardLabel>Target skills covered ({'≥'} 70%)</CardLabel>
          <div className="mt-3">
            <TrendChart
              data={progression}
              xKey="at"
              series={[{ key: 'covered', label: 'Skills covered', tone: 'primary' }]}
              height={160}
            />
          </div>
        </Card>
      </div>
    </div>
  );
}

// ===================================================================

function SkillsPanel() {
  const { data, isLoading, isError } = useSkillAnalytics();
  if (isLoading) return <LoadingState />;
  if (isError || !data) return <ErrorState message="Could not load skill analytics." />;
  if (data.status === 'no-data-yet')
    return (
      <NoData hint="Self-assess a skill — progression and bottlenecks appear once there's history." />
    );

  const d = data as SkillAnalytics;

  return (
    <div className="space-y-6">
      <Card>
        <CardLabel>Skill progression</CardLabel>
        <ul className="mt-3 space-y-3">
          {d.progression.slice(0, 8).map((s) => (
            <li key={s.skillId} className="flex items-center gap-3">
              <Link
                href={`/skills/${s.skillId}`}
                className="w-36 shrink-0 truncate text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
              >
                {s.name}
              </Link>
              <div className="min-w-0 flex-1">
                <Sparkline data={s.points.map((p) => ({ v: p.overall }))} yKey="v" />
              </div>
              <span className="shrink-0 font-mono text-[11px] text-[var(--text-secondary)]">
                {Math.round((s.points.at(-1)?.overall ?? 0) * 100)}
              </span>
            </li>
          ))}
        </ul>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-4">
          <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
            <Sparkles size={13} /> High-leverage skills
          </h3>
          {d.highLeverage.length === 0 ? (
            <p className="text-xs text-[var(--text-secondary)]">Nothing stands out.</p>
          ) : (
            <ul className="space-y-1.5">
              {d.highLeverage.map((s) => (
                <li key={s.skillId}>
                  <Link
                    href={`/skills/${s.skillId}`}
                    className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                  >
                    <span>{s.name}</span>
                    <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                      unlocks {s.unlocks} · lev {s.leverage.toFixed(2)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="p-4">
          <h3 className="mb-2 text-xs font-semibold text-[var(--text-primary)]">
            Neglected skills
          </h3>
          {d.neglected.length === 0 ? (
            <p className="text-xs text-[var(--text-secondary)]">
              Your target skills are all recent.
            </p>
          ) : (
            <ul className="space-y-1.5">
              {d.neglected.map((s) => (
                <li key={s.skillId}>
                  <Link
                    href={`/skills/${s.skillId}`}
                    className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                  >
                    <span>{s.name}</span>
                    <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                      {s.daysSince == null ? 'never assessed' : `${s.daysSince}d ago`}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {d.bottlenecks.length > 0 && (
        <Card className="p-4">
          <h3 className="mb-2 text-xs font-semibold text-[var(--text-primary)]">
            Dependency bottlenecks
          </h3>
          <p className="mb-2 text-[11px] text-[var(--text-secondary)]">
            Unassessed prerequisites of skills you&apos;re working on.
          </p>
          <ul className="space-y-1.5">
            {d.bottlenecks.map((b) => (
              <li key={b.skillId} className="text-xs">
                <Link
                  href={`/skills/${b.skillId}`}
                  className="text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                >
                  {b.name}
                </Link>
                <span className="ml-2 text-[10px] text-[var(--text-secondary)]">
                  blocks {b.blockedDependents.join(', ')}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}

// ===================================================================

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card className="p-4">
      <CardLabel>{label}</CardLabel>
      <p className="mt-1 text-xl font-semibold text-[var(--text-primary)]">{value}</p>
    </Card>
  );
}

function pct(v: number | null): string {
  return v == null ? '—' : `${Math.round(v * 100)}%`;
}
