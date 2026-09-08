'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, ExternalLink, FolderKanban, Pencil, Sparkles } from 'lucide-react';
import { useEvidence, useDeleteEvidence } from '@/features/evidence/api';
import { EvidenceForm } from '@/features/evidence/EvidenceForm';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Badge } from '@/components/ui/Badge';
import { ErrorState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';

export default function EvidenceDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const router = useRouter();
  const evidence = useEvidence(id);
  const del = useDeleteEvidence(id);
  const [editing, setEditing] = useState(false);

  if (evidence.isLoading) return <LoadingState />;
  if (evidence.isError || !evidence.data)
    return <ErrorState message="This evidence could not be found." />;

  const e = evidence.data;
  const metaEntries = Object.entries(e.metadata ?? {});

  return (
    <div className="animate-fade-in">
      <Link
        href="/evidence"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Evidence
      </Link>

      <Card className="mb-6">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <Badge tone="neutral">{e.type}</Badge>
              <Badge tone={e.verificationStatus === 'self_attested' ? 'neutral' : 'cyan'}>
                {e.verificationStatus.replace('_', ' ')}
              </Badge>
              {e.occurredAt && (
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--text-secondary)]">
                  {formatDate(e.occurredAt)}
                </span>
              )}
            </div>
            <h1 className="text-xl font-semibold text-[var(--text-primary)]">{e.title}</h1>
            {e.description && (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">{e.description}</p>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-3">
              {e.sourceUrl && (
                <a
                  href={e.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] transition-colors hover:text-[var(--accent-cyan)]"
                >
                  Source <ExternalLink size={12} aria-hidden="true" />
                </a>
              )}
              {e.externalIdentifier && (
                <span className="font-mono text-[11px] text-[var(--text-secondary)]">
                  {e.externalIdentifier}
                </span>
              )}
            </div>
          </div>
          <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
            <Pencil size={13} /> Edit
          </Button>
        </div>

        {metaEntries.length > 0 && (
          <dl className="mt-4 grid gap-1.5 border-t border-[var(--border)] pt-4 sm:grid-cols-2">
            {metaEntries.map(([k, v]) => (
              <div key={k} className="flex gap-2 text-xs">
                <dt className="text-[var(--text-secondary)]">{k}</dt>
                <dd className="font-mono text-[var(--text-primary)]">{String(v)}</dd>
              </div>
            ))}
          </dl>
        )}
      </Card>

      <div className="grid gap-6 sm:grid-cols-2">
        <section>
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
            <Sparkles size={15} /> Skills
          </h2>
          {e.skills.length === 0 ? (
            <p className="text-xs text-[var(--text-secondary)]">No skills linked.</p>
          ) : (
            <ul className="space-y-1.5">
              {e.skills.map((s) => (
                <li key={s.skillId}>
                  <Link
                    href={`/skills/${s.skillId}`}
                    className="flex items-center justify-between gap-2 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-2.5 text-xs text-[var(--text-primary)] hover:border-[var(--card-hover-border)]"
                  >
                    <span className="truncate">{s.name ?? s.skillId}</span>
                    <span className="shrink-0 font-mono text-[10px] text-[var(--text-secondary)]">
                      weight {s.contributionWeight.toFixed(2)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
            <FolderKanban size={15} /> Projects
          </h2>
          {e.projects.length === 0 ? (
            <p className="text-xs text-[var(--text-secondary)]">No projects linked.</p>
          ) : (
            <ul className="space-y-1.5">
              {e.projects.map((pr) => (
                <li key={pr.projectId}>
                  <Link
                    href={`/projects/${pr.projectId}`}
                    className="block truncate rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-2.5 text-xs text-[var(--text-primary)] hover:border-[var(--card-hover-border)]"
                  >
                    {pr.name ?? pr.projectId}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="mt-10 border-t border-[var(--border)] pt-4">
        <CardLabel>
          Logged {formatDate(e.createdAt)}
          {e.updatedAt !== e.createdAt ? ` · updated ${formatDate(e.updatedAt)}` : ''}
        </CardLabel>
        <div className="mt-3">
          <Button
            size="sm"
            variant="danger"
            disabled={del.isPending}
            onClick={() => {
              if (confirm(`Delete "${e.title}"?`)) {
                del.mutate(undefined, { onSuccess: () => router.push('/evidence') });
              }
            }}
          >
            Delete evidence
          </Button>
        </div>
      </div>

      <Dialog open={editing} onClose={() => setEditing(false)} title="Edit evidence">
        <EvidenceForm evidence={e} onDone={() => setEditing(false)} />
      </Dialog>
    </div>
  );
}
