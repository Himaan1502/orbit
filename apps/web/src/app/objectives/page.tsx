'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Plus, Target } from 'lucide-react';
import { useObjectives } from '@/features/objectives/api';
import { ObjectiveForm } from '@/features/objectives/ObjectiveForm';
import { Button } from '@/components/ui/Button';
import { Card, CardLabel } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/Badge';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';
import { formatDate } from '@/lib/format';

export default function ObjectivesPage() {
  const { data: objectives, isLoading, isError } = useObjectives();
  const [creating, setCreating] = useState(false);

  return (
    <div className="animate-fade-in">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Objectives</h1>
          <p className="text-sm text-[var(--text-secondary)]">Outcomes you are working toward.</p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus size={16} /> New objective
        </Button>
      </header>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your objectives." />}

      {objectives && objectives.length === 0 && (
        <EmptyState
          icon={Target}
          title="No objectives yet"
          hint="Start with one meaningful outcome — milestones and tasks come next."
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} /> New objective
            </Button>
          }
        />
      )}

      {objectives && objectives.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {objectives.map((o) => (
            <li key={o.id}>
              <Link href={`/objectives/${o.id}`} className="block">
                <Card className="transition-colors hover:border-[var(--card-hover-border)]">
                  <div className="flex items-start gap-4">
                    <ProgressRing value={o.progress} size={64} strokeWidth={5} />
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex items-center gap-2">
                        <StatusBadge status={o.status} />
                        <CardLabel>P{o.priority}</CardLabel>
                      </div>
                      <p className="truncate text-sm font-medium text-[var(--text-primary)]">
                        {o.title}
                      </p>
                      {o.targetDate && (
                        <p className="mt-1 font-mono text-[11px] text-[var(--text-secondary)]">
                          target {formatDate(o.targetDate)}
                        </p>
                      )}
                    </div>
                  </div>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={creating} onClose={() => setCreating(false)} title="New objective">
        <ObjectiveForm onDone={() => setCreating(false)} />
      </Dialog>
    </div>
  );
}
