'use client';

import { useMemo, useState } from 'react';
import { FileCheck, Plus, Search } from 'lucide-react';
import { useEvidenceList } from '@/features/evidence/api';
import { EvidenceForm, EVIDENCE_TYPES } from '@/features/evidence/EvidenceForm';
import { EvidenceRow } from '@/features/evidence/EvidenceRow';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';

export default function EvidencePage() {
  const [type, setType] = useState('');
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const { data, isLoading, isError } = useEvidenceList(type ? { type } : {});

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return data ?? [];
    return (data ?? []).filter(
      (e) =>
        e.title.toLowerCase().includes(q) ||
        (e.description ?? '').toLowerCase().includes(q) ||
        e.skills.some((s) => (s.name ?? '').toLowerCase().includes(q))
    );
  }, [data, query]);

  return (
    <div className="animate-fade-in">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)]">Evidence</h1>
          <p className="text-sm text-[var(--text-secondary)]">
            The record of what you&apos;ve actually done — it raises assessment confidence, not the
            score.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus size={16} /> Log evidence
        </Button>
      </header>

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search
            size={14}
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-secondary)]"
          />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search title, description, skill…"
            aria-label="Search evidence"
            className="w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] py-2 pl-9 pr-3 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
          />
        </div>
        <select
          value={type}
          onChange={(e) => setType(e.target.value)}
          aria-label="Filter by type"
          className="rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm text-[var(--text-primary)] focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none"
        >
          <option value="">All types</option>
          {EVIDENCE_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your evidence." />}

      {data && data.length === 0 && (
        <EmptyState
          icon={FileCheck}
          title="No evidence logged yet"
          hint="Log a repository, a deployment, a benchmark, a talk — anything that proves a skill in practice."
          action={
            <Button onClick={() => setCreating(true)}>
              <Plus size={16} /> Log evidence
            </Button>
          }
        />
      )}

      {data && data.length > 0 && rows.length === 0 && (
        <p className="py-10 text-center text-sm text-[var(--text-secondary)]">
          Nothing matches those filters.
        </p>
      )}

      <ul className="space-y-2">
        {rows.map((e) => (
          <EvidenceRow key={e.id} evidence={e} />
        ))}
      </ul>

      <Dialog open={creating} onClose={() => setCreating(false)} title="Log evidence">
        <EvidenceForm onDone={() => setCreating(false)} />
      </Dialog>
    </div>
  );
}
