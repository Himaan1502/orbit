'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ArrowUpRight, FileCheck, GitBranch, Plus } from 'lucide-react';
import { useSkill, useSkillDependencies } from '@/features/skills/api';
import { useSkillEvidence } from '@/features/evidence/api';
import { AssessSkillForm } from '@/features/skills/AssessSkillForm';
import { EvidenceForm } from '@/features/evidence/EvidenceForm';
import { EvidenceRow } from '@/features/evidence/EvidenceRow';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Badge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { CapabilityBar } from '@/components/ui/CapabilityBar';
import { ErrorState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';
import type { SkillNeighbour } from '@/lib/api-types';

const DIMENSION_LABELS: Record<string, string> = {
  knowledge: 'Knowledge',
  practical: 'Practical',
  evidence: 'Evidence',
  interview: 'Interview',
  production: 'Production',
};

export default function SkillDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const skill = useSkill(id);
  const deps = useSkillDependencies(id);
  const evidence = useSkillEvidence(id);
  const [assessing, setAssessing] = useState(false);
  const [loggingEvidence, setLoggingEvidence] = useState(false);

  if (skill.isLoading) return <LoadingState />;
  if (skill.isError || !skill.data) return <ErrorState message="This skill could not be found." />;

  const s = skill.data;

  return (
    <div className="animate-fade-in">
      <Link
        href="/skills"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Skills
      </Link>

      <Card className="mb-6">
        <div className="flex items-start gap-5">
          <ProgressRing
            value={s.capability ?? 0}
            size={96}
            label={
              s.capability == null
                ? 'not assessed'
                : `${Math.round(s.capability * 100)}% capability`
            }
          />
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex items-center gap-2">
              <CardLabel>{s.category}</CardLabel>
              {s.confidence && (
                <Badge tone={s.confidence === 'self-reported' ? 'neutral' : 'cyan'}>
                  {s.confidence}
                </Badge>
              )}
            </div>
            <h1 className="text-xl font-semibold text-[var(--text-primary)]">{s.canonicalName}</h1>
            {s.description && (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">{s.description}</p>
            )}
            {s.lastAssessedAt && (
              <p className="mt-3 font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--text-secondary)]">
                Last assessed · {formatDate(s.lastAssessedAt)}
              </p>
            )}
          </div>
          <Button size="sm" onClick={() => setAssessing(true)}>
            {s.capability == null ? 'Assess' : 'Re-assess'}
          </Button>
        </div>

        {s.dimensions && (
          <div className="mt-5 grid gap-2 border-t border-[var(--border)] pt-4 sm:grid-cols-2">
            {Object.entries(s.dimensions).map(([dim, value]) => (
              <div key={dim} className="flex items-center gap-3">
                <span className="w-20 shrink-0 text-xs text-[var(--text-secondary)]">
                  {DIMENSION_LABELS[dim] ?? dim}
                </span>
                <CapabilityBar value={value} />
              </div>
            ))}
          </div>
        )}
      </Card>

      <section>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
          <GitBranch size={15} /> Dependencies
        </h2>
        {deps.isLoading && <LoadingState />}
        {deps.data && (
          <div className="grid gap-4 sm:grid-cols-2">
            <NeighbourList
              title="Prerequisites"
              hint="Learn these first"
              items={deps.data.prerequisites}
            />
            <NeighbourList title="Unlocks" hint="These build on it" items={deps.data.dependents} />
          </div>
        )}
      </section>

      <section className="mt-6">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
            <FileCheck size={15} /> Evidence
          </h2>
          <Button size="sm" variant="secondary" onClick={() => setLoggingEvidence(true)}>
            <Plus size={13} /> Log evidence
          </Button>
        </div>
        <p className="mb-3 text-xs text-[var(--text-secondary)]">
          Logged evidence raises this skill&apos;s assessment <em>confidence</em> — it never changes
          the capability score.
        </p>
        {evidence.isLoading && <LoadingState />}
        {(evidence.data ?? []).length === 0 && !evidence.isLoading && (
          <p className="text-xs text-[var(--text-secondary)]">
            No evidence logged for this skill yet.
          </p>
        )}
        <ul className="space-y-2">
          {(evidence.data ?? []).map((e) => (
            <EvidenceRow key={e.id} evidence={e} />
          ))}
        </ul>
      </section>

      <Dialog
        open={assessing}
        onClose={() => setAssessing(false)}
        title={`Assess — ${s.canonicalName}`}
      >
        <AssessSkillForm skillId={id} current={s.dimensions} onDone={() => setAssessing(false)} />
      </Dialog>
      <Dialog
        open={loggingEvidence}
        onClose={() => setLoggingEvidence(false)}
        title={`Log evidence — ${s.canonicalName}`}
      >
        <EvidenceForm presetSkillId={id} onDone={() => setLoggingEvidence(false)} />
      </Dialog>
    </div>
  );
}

function NeighbourList({
  title,
  hint,
  items,
}: {
  title: string;
  hint: string;
  items: SkillNeighbour[];
}) {
  return (
    <Card className="p-4">
      <div className="mb-2 flex items-baseline justify-between">
        <h3 className="text-xs font-semibold text-[var(--text-primary)]">{title}</h3>
        <span className="text-[10px] text-[var(--text-secondary)]">{hint}</span>
      </div>
      {items.length === 0 ? (
        <p className="text-xs text-[var(--text-secondary)]">None in the catalog.</p>
      ) : (
        <ul className="space-y-1.5">
          {items.map((n) => (
            <li key={n.skillId}>
              <Link
                href={`/skills/${n.skillId}`}
                className="flex items-center justify-between gap-2 text-xs text-[var(--text-primary)] hover:text-[var(--accent-cyan)]"
              >
                <span className="inline-flex items-center gap-1">
                  {n.name}
                  <ArrowUpRight size={12} className="text-[var(--text-secondary)]" />
                </span>
                <span className="font-mono text-[10px] text-[var(--text-secondary)]">
                  {n.capability == null ? '—' : Math.round(n.capability * 100)} · str{' '}
                  {n.strength.toFixed(1)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
