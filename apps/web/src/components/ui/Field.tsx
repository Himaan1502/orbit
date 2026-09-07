import { forwardRef, useId, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

const controlClass =
  'w-full rounded-button border border-[var(--border)] bg-[var(--bg-base)] px-3 py-2 text-sm ' +
  'text-[var(--text-primary)] placeholder:text-[var(--text-secondary)] ' +
  'focus-visible:border-[var(--accent-cyan)] focus-visible:outline-none ' +
  'focus-visible:shadow-[0_0_0_2px_rgba(79,209,229,0.25)] disabled:opacity-50';

interface Common {
  label: string;
  hint?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, Common & InputHTMLAttributes<HTMLInputElement>>(
  function Input({ label, hint, error, className, id, ...props }, ref) {
    const gen = useId();
    const fieldId = id ?? gen;
    return (
      <div>
        <label htmlFor={fieldId} className="mb-1.5 block text-xs text-[var(--text-secondary)]">
          {label}
        </label>
        <input
          id={fieldId}
          ref={ref}
          aria-invalid={error ? true : undefined}
          className={cn(controlClass, error && 'border-[var(--color-danger)]', className)}
          {...props}
        />
        {hint && !error && <p className="mt-1 text-xs text-[var(--text-secondary)]">{hint}</p>}
        {error && <p className="mt-1 text-xs text-[var(--color-danger)]">{error}</p>}
      </div>
    );
  }
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  Common & TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ label, hint, error, className, id, rows = 3, ...props }, ref) {
  const gen = useId();
  const fieldId = id ?? gen;
  return (
    <div>
      <label htmlFor={fieldId} className="mb-1.5 block text-xs text-[var(--text-secondary)]">
        {label}
      </label>
      <textarea
        id={fieldId}
        ref={ref}
        rows={rows}
        aria-invalid={error ? true : undefined}
        className={cn(controlClass, 'resize-y', error && 'border-[var(--color-danger)]', className)}
        {...props}
      />
      {hint && !error && <p className="mt-1 text-xs text-[var(--text-secondary)]">{hint}</p>}
      {error && <p className="mt-1 text-xs text-[var(--color-danger)]">{error}</p>}
    </div>
  );
});
