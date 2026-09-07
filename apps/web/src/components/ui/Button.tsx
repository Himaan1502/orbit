import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-button font-medium transition-colors duration-150 ease-out ' +
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent-cyan)] ' +
  'disabled:cursor-not-allowed disabled:opacity-50';

const variants: Record<Variant, string> = {
  primary: 'bg-[var(--accent-cyan)] text-[var(--bg-base)] hover:brightness-110',
  secondary:
    'border border-[var(--border)] text-[var(--text-primary)] hover:bg-[var(--bg-hover)] hover:border-[var(--card-hover-border)]',
  ghost: 'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]',
  danger:
    'border border-[color:rgba(242,84,91,0.4)] text-[var(--color-danger)] hover:bg-[color:rgba(242,84,91,0.1)]',
};

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-10 px-4 text-sm',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', className, type = 'button', ...props },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(base, variants[variant], sizes[size], className)}
      {...props}
    />
  );
});
