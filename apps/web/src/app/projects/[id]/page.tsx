'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, ListTodo, Pencil, Plus, Target } from 'lucide-react';
import { useProject, useUpdateProject, useDeleteProject } from '@/features/projects/api';
import { ProjectForm } from '@/features/projects/ProjectForm';
import { useMilestones } from '@/features/objectives/api';
import { useTasks } from '@/features/execution/api';
import { useEvidenceList } from '@/features/evidence/api';
import { EvidenceForm } from '@/features/evidence/EvidenceForm';
import { TaskForm } from '@/features/execution/TaskForm';
import { TaskRow } from '@/features/execution/TaskRow';
import { EvidenceRow } from '@/features/evidence/EvidenceRow';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import { useRouter } from 'next/navigation';
import { formatDate } from '@/lib/format';
import type { ProjectStatus } from '@/lib/api-types';

const NEXT_STATUS: Record<ProjectStatus, { to: ProjectStatus; label: string }[]> = {
  planned: [{ to: 'active', label: 'Start' }],
  active: [
    { to: 'completed', label: 'Mark complete' },
    { to: 'planned', label: 'Back to planned' },
  ],
  completed: [{ to: 'active', label: 'Re-open' }],
  archived: [{ to: 'active', label: 'Un-archive' }],
};

export default function ProjectDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const router = useRouter();
  const project = useProject(id);
  const update = useUpdateProject(id);
  const del = useDeleteProject(id);
  const allTasks = useTasks();
  const evidence = useEvidenceList({ projectId: id });
  const milestones = useMilestones(project.data?.objectiveId ?? '');

  const [editing, setEditing] = useState(false);
  const [addingTask, setAddingTask] = useState(false);
  const [loggingEvidence, setLoggingEvidence] = useState(false);

  const tasks = useMemo(
    () => (allTasks.data ?? []).filter((t) => t.projectId === id),
    [allTasks.data, id]
  );

  if (project.isLoading) return <LoadingState />;
  if (project.isError || !project.data)
    return <ErrorState message="This project could not be found." />;

  const p = project.data;
  const transitions = NEXT_STATUS[p.status] ?? [];

  return (
    <div className="animate-fade-in">
      <Link
        href="/projects"
        className="mb-4 inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
      >
        <ArrowLeft size={14} /> Projects
      </Link>

      <Card className="mb-6">
        <div className="flex items-start gap-5">
          <ProgressRing value={p.progress} size={104} />
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex items-center gap-2">
              <StatusBadge status={p.status} />
              {p.objectiveId && p.objectiveTitle && (
                <Link
                  href={`/objectives/${p.objectiveId}`}
                  className="inline-flex items-center gap-1 font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--accent-cyan)] hover:underline"
                >
                  <Target size={12} /> {p.objectiveTitle}
                </Link>
              )}
            </div>
            <h1 className="text-xl font-semibold text-[var(--text-primary)]">{p.name}</h1>
            {p.description && (
              <p className="mt-2 text-sm text-[var(--text-secondary)]">{p.description}</p>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-3">
              {p.repositoryUrl && <SourceLink href={p.repositoryUrl} label="Repository" />}
              {p.demoUrl && <SourceLink href={p.demoUrl} label="Demo" />}
              {p.startedAt && (
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--text-secondary)]">
                  Started · {formatDate(p.startedAt)}
                </span>
              )}
              {p.completedAt && (
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-[var(--color-success)]">
                  Completed · {formatDate(p.completedAt)}
                </span>
              )}
            </div>
          </div>
          <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
            <Pencil size={13} /> Edit
          </Button>
        </div>

        {transitions.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2 border-t border-[var(--border)] pt-4">
            {transitions.map((t) => (
              <Button
                key={t.to}
                size="sm"
                variant={t.to === 'completed' ? 'primary' : 'secondary'}
                disabled={update.isPending}
                onClick={() => update.mutate({ status: t.to })}
              >
                {t.label}
              </Button>
            ))}
            {p.status !== 'archived' && (
              <Button
                size="sm"
                variant="ghost"
                disabled={update.isPending}
                onClick={() => update.mutate({ status: 'archived' })}
              >
                Archive
              </Button>
            )}
          </div>
        )}
      </Card>

      <div className="grid gap-8 lg:grid-cols-2">
        {/* Skills */}
        <section>
          <h2 className="mb-3 text-sm font-semibold text-[var(--text-primary)]">Skills</h2>
          {p.skills.length === 0 ? (
            <EmptyState
              icon={Target}
              title="No skills attached"
              hint="Edit the project to say which skills it builds."
            />
          ) : (
            <ul className="space-y-3">
              {p.skills.map((s) => (
                <li key={s.skillId}>
                  <Link
                    href={`/skills/${s.skillId}`}
                    className="block rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3 transition-colors hover:border-[var(--card-hover-border)]"
                  >
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <span className="truncate text-xs font-medium text-[var(--text-primary)]">
                        {s.name}
                      </span>
                    </div>
                    <ContributionBars
                      expected={s.expectedContribution}
                      demonstrated={s.demonstratedContribution}
                    />
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {p.status !== 'completed' && p.skills.length > 0 && (
            <p className="mt-2 text-xs text-[var(--text-secondary)]">
              Demonstrated contribution stays at 0 until the project is completed with linked
              evidence.
            </p>
          )}
        </section>

        {/* Milestones (from the linked objective) */}
        <section>
          <h2 className="mb-3 text-sm font-semibold text-[var(--text-primary)]">Milestones</h2>
          {!p.objectiveId ? (
            <p className="text-xs text-[var(--text-secondary)]">
              Link an objective (Edit) to give this project milestones.
            </p>
          ) : (milestones.data ?? []).length === 0 ? (
            <p className="text-xs text-[var(--text-secondary)]">
              The linked objective has no milestones yet.
            </p>
          ) : (
            <ul className="space-y-2">
              {(milestones.data ?? []).map((m) => (
                <li
                  key={m.id}
                  className="flex items-center gap-3 rounded-card border border-[var(--border)] bg-[var(--bg-surface)] p-3"
                >
                  <ProgressRing value={m.progress} size={36} strokeWidth={4} />
                  <span className="min-w-0 flex-1 truncate text-xs text-[var(--text-primary)]">
                    {m.title}
                  </span>
                  <StatusBadge status={m.status} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {/* Tasks */}
      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-[var(--text-primary)]">Tasks</h2>
          {p.objectiveId ? (
            <Button size="sm" onClick={() => setAddingTask(true)}>
              <Plus size={14} /> Add task
            </Button>
          ) : (
            <span className="text-xs text-[var(--text-secondary)]">
              Link an objective to add tasks
            </span>
          )}
        </div>
        {allTasks.isLoading && <LoadingState />}
        {tasks.length === 0 && !allTasks.isLoading && (
          <EmptyState
            icon={ListTodo}
            title="No tasks on this project"
            hint="Tasks on an active project raise their own priority (the projectImpact factor)."
          />
        )}
        <div className="space-y-2">
          {tasks.map((t) => (
            <TaskRow
              key={t.id}
              task={t}
              objectiveId={p.objectiveId ?? undefined}
              milestones={milestones.data ?? []}
              projectLocked
            />
          ))}
        </div>
      </section>

      {/* Evidence */}
      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-[var(--text-primary)]">Evidence</h2>
          <Button size="sm" variant="secondary" onClick={() => setLoggingEvidence(true)}>
            <Plus size={14} /> Log evidence
          </Button>
        </div>
        {evidence.isLoading && <LoadingState />}
        {(evidence.data ?? []).length === 0 && !evidence.isLoading && (
          <p className="text-xs text-[var(--text-secondary)]">
            Nothing logged for this project yet.
          </p>
        )}
        <ul className="space-y-2">
          {(evidence.data ?? []).map((e) => (
            <EvidenceRow key={e.id} evidence={e} />
          ))}
        </ul>
      </section>

      <div className="mt-10 border-t border-[var(--border)] pt-4">
        <Button
          size="sm"
          variant="danger"
          disabled={del.isPending}
          onClick={() => {
            if (confirm(`Delete "${p.name}"? This cannot be undone.`)) {
              del.mutate(undefined, { onSuccess: () => router.push('/projects') });
            }
          }}
        >
          Delete project
        </Button>
      </div>

      <Dialog open={editing} onClose={() => setEditing(false)} title="Edit project">
        <ProjectForm project={p} onDone={() => setEditing(false)} />
      </Dialog>
      {p.objectiveId && (
        <Dialog open={addingTask} onClose={() => setAddingTask(false)} title="Add task">
          <TaskForm
            objectiveId={p.objectiveId}
            projectId={p.id}
            milestones={milestones.data ?? []}
            onDone={() => setAddingTask(false)}
          />
        </Dialog>
      )}
      <Dialog open={loggingEvidence} onClose={() => setLoggingEvidence(false)} title="Log evidence">
        <EvidenceForm presetProjectId={p.id} onDone={() => setLoggingEvidence(false)} />
      </Dialog>
    </div>
  );
}

function SourceLink({ href, label }: { href: string; label: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1 text-xs text-[var(--text-secondary)] transition-colors hover:text-[var(--accent-cyan)]"
    >
      {label} <ExternalLink size={12} aria-hidden="true" />
    </a>
  );
}

function ContributionBars({ expected, demonstrated }: { expected: number; demonstrated: number }) {
  return (
    <div className="space-y-1.5">
      <Bar label="expected" value={expected} tone="var(--text-secondary)" />
      <Bar label="demonstrated" value={demonstrated} tone="var(--accent-cyan)" />
    </div>
  );
}

function Bar({ label, value, tone }: { label: string; value: number; tone: string }) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  return (
    <div className="flex items-center gap-2">
      <span className="w-24 shrink-0 font-mono text-[10px] uppercase tracking-[0.08em] text-[var(--text-secondary)]">
        {label}
      </span>
      <div className="h-1.5 w-full overflow-hidden rounded-pill bg-[var(--border)]">
        <div className="h-full rounded-pill" style={{ width: `${pct}%`, background: tone }} />
      </div>
      <span className="shrink-0 font-mono text-[10px] text-[var(--text-secondary)]">{pct}</span>
    </div>
  );
}
