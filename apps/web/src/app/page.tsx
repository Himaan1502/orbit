export default function CommandCenterPage() {
  return (
    <div className="flex flex-col items-center justify-center py-20">
      <div className="mb-8">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <circle
            cx="32"
            cy="32"
            r="26"
            stroke="var(--accent-cyan)"
            strokeWidth="2"
            strokeLinecap="round"
            strokeDasharray="130 33"
          />
          <circle cx="32" cy="32" r="4" fill="var(--accent-cyan)" />
        </svg>
      </div>
      <h1 className="mb-2 text-2xl font-semibold text-[var(--text-primary)]">Command Center</h1>
      <p className="text-sm text-[var(--text-secondary)]">Coming in Milestone 3</p>
    </div>
  );
}
