'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Briefcase, FileCheck, FolderKanban, Plus, Target, TrendingUp } from 'lucide-react';
import { useCareerTargets, useGaps, useReadiness } from '@/features/career/api';
import { useCareerAnalytics } from '@/features/analytics/api';
import { Sparkline } from '@/features/analytics/charts';
import { CareerTargetForm } from '@/features/career/CareerTargetForm';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Badge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import type {
  CareerReadiness,
  CriticalGap,
  EvidenceGap,
  HighLeverageSkill,
  ProjectGap,
} from '@/lib/api-types';

export default function CareerPage() {
  const targets = useCareerTargets();
  const readiness = useReadiness();
  const gaps = useGaps();
  const [creating, setCreating] = useState(false);
  const router = useRouter();

  if (targets.isLoading) return <LoadingState />;
  if (targets.isError) return <ErrorState message="Could not load your career targets." />;

  const list = targets.data ?? [];

  return (
    <div className="animate-fade-in">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Career</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            The role you are aiming for, and how ready you are for it.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus size={16} /> New target
        </Button>
      </header>

      {list.length === 0 && (
        <EmptyState
          icon={Briefcase}
          title="No career target yet"
          hint="Define the role you are working toward — ORBIT then measures readiness and surfaces the skill gaps that matter most."
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} /> New target
            </Button>
          }
        />
      )}

      {list.length > 0 && (
        <div className="space-y-6">
          {readiness.isLoading && <LoadingState />}
          {readiness.data && <ReadinessPanel readiness={readiness.data} />}
          <ReadinessTrend />

          {gaps.data && (
            <div className="grid gap-4 lg:grid-cols-2">
              <GapList gaps={gaps.data.criticalGaps} />
              <LeverageList skills={gaps.data.highLeverageSkills} />
              <EvidenceGapList gaps={gaps.data.evidenceGaps} />
              <ProjectGapList gaps={gaps.data.projectGaps} />
            </div>
          )}

          <section>
            <h2 className="mb-3 text-sm font-semibold text-[var(--text-primary)]">All targets</h2>
            <ul className="grid gap-3 sm:grid-cols-2">
              {list.map((t) => (
                <li key={t.id}>
                  <Card className="p-4">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium text-[var(--text-primary)]">{t.name}</p>
                      <Badge tone={t.status === 'active' ? 'cyan' : 'neutral'}>{t.status}</Badge>
                    </div>
                    <div className="mt-1 flex items-center gap-2">
                      {t.roleFamily && <CardLabel>{t.roleFamily}</CardLabel>}
                      <CardLabel>P{t.priority}</CardLabel>
                    </div>
                    <p className="mt-2 font-mono text-[11px] text-[var(--text-secondary)]">
                      {t.skillCount} weighted skill{t.skillCount === 1 ? '' : 's'}
                    </p>
                  </Card>
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}

      <Dialog open={creating} onClose={() => setCreating(false)} title="New career target">
        <CareerTargetForm
          onDone={(id) => {
            setCreating(false);
            if (id) router.push('/career');
          }}
        />
      </Dialog>
    </div>
  );
}

const COMPONENT_LABELS: Record<string, string> = {
  skillCapability: 'Skill capability',
  interviewReadiness: 'Interview readiness',
  evidenceCoverage: 'Evidence coverage',
  projectCoverage: 'Project coverage',
};

function ReadinessPanel({ readiness }: { readiness: CareerReadiness }) {
  return (
    <Card>
      <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
        <div className="flex flex-col items-center gap-1">
          <ProgressRing
            value={readiness.overall}
            size={120}
            label={`${Math.round(readiness.overall * 100)}% ready`}
          />
          <Badge tone="neutral">{readiness.confidence}</Badge>
        </div>
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex items-center gap-2">
            <h2 className="text-sm font-semibold text-[var(--text-primary)]">
              {readiness.targetName}
            </h2>
            <CardLabel>
              {readiness.assessedSkillCount}/{readiness.targetSkillCount} assessed
            </CardLabel>
          </div>
          <p className="text-xs text-[var(--text-secondary)]">{readiness.explanation}</p>

          <p className="mt-3 rounded-card border border-[var(--border)] bg-[var(--bg-base)] p-2.5 text-[11px] leading-relaxed text-[var(--text-secondary)]">
            Readiness now blends four signals — skill capability (0.45), interview readiness (0.20),
            evidence coverage (0.20) and demonstrated project work (0.15). Capability alone no
            longer carries it: a target with strong self-assessments but no logged evidence or
            completed projects will read lower here than it did before, by design.
          </p>

          <ul className="mt-4 space-y-2">
            {readiness.componentBreakdown.map((c) => (
              <li key={c.component} className="flex items-center gap-3 text-xs">
                <span className="w-32 shrink-0 text-[var(--text-secondary)]">
                  {COMPONENT_LABELS[c.component] ?? c.component}
                </span>
                {c.value == null ? (
                  <span className="text-[11px] text-[var(--text-secondary)]">
                    {c.status.replace(/-/g, ' ')}
                  </span>
                ) : (
                  <>
                    <div className="h-1.5 w-full max-w-[180px] overflow-hidden rounded-pill bg-[var(--border)]">
                      <div
                        className="h-full rounded-pill bg-[var(--accent-cyan)]"
                        style={{ width: `${Math.round(c.value * 100)}%` }}
                      />
                    </div>
                    <span className="shrink-0 font-mono text-[11px] text-[var(--text-secondary)]">
                      {Math.round(c.value * 100)} · w{c.weight.toFixed(2)}
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </Card>
  );
}

function ReadinessTrend() {
  const { data } = useCareerAnalytics();
  const points = data?.readinessTrend ?? [];
  if (points.length < 2) return null;
  const first = Math.round(points[0].overall * 100);
  const last = Math.round(points[points.length - 1].overall * 100);
  const delta = last - first;
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <CardLabel>Readiness trend</CardLabel>
        <span
          className={`font-mono text-[11px] ${
            delta > 0
              ? 'text-[var(--color-success)]'
              : delta < 0
                ? 'text-[var(--color-warning)]'
                : 'text-[var(--text-secondary)]'
          }`}
        >
          {delta >= 0 ? '+' : ''}
          {delta} pts over {points.length} points
        </span>
      </div>
      <div className="mt-2">
        <Sparkline data={points.map((p) => ({ v: p.overall }))} yKey="v" height={48} />
      </div>
      <Link
        href="/analytics"
        className="mt-2 inline-block text-[11px] text-[var(--accent-cyan)] hover:underline"
      >
        Full analytics →
      </Link>
    </Card>
  );
}

function GapList({ gaps }: { gaps: CriticalGap[] }) {
  return (
    <Card className="p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
        <Target size={14} /> Critical gaps
      </h3>
      {gaps.length === 0 ? (
        <p className="text-xs text-[var(--text-secondary)]">No outstanding gaps — nicely done.</p>
      ) : (
        <ul className="space-y-1.5">
          {gaps.map((g) => (
            <li key={g.skillId}>
              <Link
                href={`/skills/${g.skillId}`}
                className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
              >
                <span>{g.skill}</span>
                <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                  gap {g.gap.toFixed(2)} · imp {g.importance.toFixed(1)} · cap{' '}
                  {Math.round(g.capability * 100)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function EvidenceGapList({ gaps }: { gaps: EvidenceGap[] }) {
  return (
    <Card className="p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
        <FileCheck size={14} /> Evidence gaps
      </h3>
      {gaps.length === 0 ? (
        <p className="text-xs text-[var(--text-secondary)]">
          Every skill you can do has evidence behind it.
        </p>
      ) : (
        <>
          <p className="mb-2 text-[11px] text-[var(--text-secondary)]">
            You can do these — you just haven&apos;t logged proof.
          </p>
          <ul className="space-y-1.5">
            {gaps.map((g) => (
              <li key={g.skillId}>
                <Link
                  href={`/skills/${g.skillId}`}
                  className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                >
                  <span>{g.skill}</span>
                  <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                    cap {Math.round(g.capability * 100)} · imp {g.importance.toFixed(1)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}

function ProjectGapList({ gaps }: { gaps: ProjectGap[] }) {
  return (
    <Card className="p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
        <FolderKanban size={14} /> Project gaps
      </h3>
      {gaps.length === 0 ? (
        <p className="text-xs text-[var(--text-secondary)]">
          Demonstrated project work covers your target skills.
        </p>
      ) : (
        <>
          <p className="mb-2 text-[11px] text-[var(--text-secondary)]">
            No completed project demonstrates these yet.
          </p>
          <ul className="space-y-1.5">
            {gaps.map((g) => (
              <li key={g.skillId}>
                <Link
                  href={`/skills/${g.skillId}`}
                  className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
                >
                  <span>{g.skill}</span>
                  <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                    cap {Math.round(g.capability * 100)} · imp {g.importance.toFixed(1)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}

function LeverageList({ skills }: { skills: HighLeverageSkill[] }) {
  return (
    <Card className="p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold text-[var(--text-primary)]">
        <TrendingUp size={14} /> High-leverage skills
      </h3>
      {skills.length === 0 ? (
        <p className="text-xs text-[var(--text-secondary)]">
          Nothing stands out — gaps are mostly independent.
        </p>
      ) : (
        <ul className="space-y-1.5">
          {skills.map((s) => (
            <li key={s.skillId}>
              <Link
                href={`/skills/${s.skillId}`}
                className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
              >
                <span>{s.skill}</span>
                <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                  unlocks {s.unlocks} · lev {s.leverage.toFixed(2)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
