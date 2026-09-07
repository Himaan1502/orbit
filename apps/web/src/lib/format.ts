export function formatMinutes(min: number | null | undefined): string {
  if (!min || min <= 0) return '—';
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export function relativeDueDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const days = Math.round((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (days < 0) return `${Math.abs(days)}d overdue`;
  if (days === 0) return 'due today';
  if (days === 1) return 'due tomorrow';
  if (days <= 14) return `due in ${days}d`;
  return `due ${formatDate(iso)}`;
}

/** ISO date (yyyy-MM-dd) for "today" in the browser's timezone. */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

export function impactLabel(score: number | null | undefined): 'HIGH' | 'MEDIUM' | 'LOW' {
  if (score == null) return 'MEDIUM';
  if (score >= 0.62) return 'HIGH';
  if (score >= 0.48) return 'MEDIUM';
  return 'LOW';
}
