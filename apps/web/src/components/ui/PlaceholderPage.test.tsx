import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PlaceholderPage } from './PlaceholderPage';
import { Target } from 'lucide-react';

describe('PlaceholderPage', () => {
  it('renders title and milestone', () => {
    render(<PlaceholderPage title="Objectives" icon={Target} milestone={2} />);

    expect(screen.getByText('Objectives')).toBeInTheDocument();
    expect(screen.getByText('Coming in Milestone 2')).toBeInTheDocument();
  });
});
