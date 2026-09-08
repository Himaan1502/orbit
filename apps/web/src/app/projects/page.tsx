'use client';

import { useState } from 'react';
import Link from 'next/link';
import { FolderKanban, Plus } from 'lucide-react';
import { useProjects } from '@/features/projects/api';
import { ProjectForm } from '@/features/projects/ProjectForm';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';

const ORDER: Record<string, number> = { active: 0, planned: 1, completed: 2, archived: 3 };

export default function ProjectsPage() {
  const { data, isLoading, isError } = useProjects();
  const [creating, setCreating] = useState(false);

  const projects = [...(data ?? [])].sort(
    (a, b) => (ORDER[a.status] ?? 9) - (ORDER[b.status] ?? 9)
  );

  return (
    <div className="animate-fade-in">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Projects</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            The work that turns skills into evidence.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus size={16} /> New project
        </Button>
      </header>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your projects." />}

      {data && data.length === 0 && (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          hint="Create a project for something you're building — attach the skills it exercises, then log evidence as you go."
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} /> New project
            </Button>
          }
        />
      )}

      {projects.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {projects.map((p) => (
            <li key={p.id}>
              <Link href={`/projects/${p.id}`} className="block">
                <Card className="transition-colors hover:border-[var(--card-hover-border)]">
                  <div className="flex items-start gap-4">
                    <ProgressRing value={p.progress} size={64} strokeWidth={5} />
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex items-center gap-2">
                        <StatusBadge status={p.status} />
                        {p.skillCount > 0 && (
                          <CardLabel>
                            {p.skillCount} skill{p.skillCount === 1 ? '' : 's'}
                          </CardLabel>
                        )}
                      </div>
                      <p className="truncate text-sm font-medium text-[var(--text-primary)]">
                        {p.name}
                      </p>
                      <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                        {p.evidenceCount} evidence
                        {p.objectiveTitle ? ` · ${p.objectiveTitle}` : ''}
                      </p>
                    </div>
                  </div>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={creating} onClose={() => setCreating(false)} title="New project">
        <ProjectForm onDone={() => setCreating(false)} />
      </Dialog>
    </div>
  );
}
