import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CapabilityBar } from './CapabilityBar';

describe('CapabilityBar', () => {
  it('shows an explicit "not assessed" rather than a silent zero for null', () => {
    render(<CapabilityBar value={null} />);
    expect(screen.getByText('not assessed')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('renders a rounded percentage for a 0..1 value', () => {
    render(<CapabilityBar value={0.526} />);
    const bar = screen.getByRole('progressbar');
    expect(bar).toHaveAttribute('aria-valuenow', '53');
    expect(screen.getByText('53')).toBeInTheDocument();
  });

  it('clamps out-of-range values', () => {
    render(<CapabilityBar value={1.4} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });
});
