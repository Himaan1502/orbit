import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { OscarNudge as OscarNudgeType } from '@/lib/api-types';

const nudge = vi.fn();
vi.mock('./api', () => ({ useNudge: () => nudge() }));

import { OscarNudge } from './OscarNudge';

describe('OscarNudge', () => {
  beforeEach(() => {
    nudge.mockReset();
    try {
      localStorage.clear();
    } catch {
      /* jsdom */
    }
  });

  const wrap = (data: OscarNudgeType | undefined) => {
    nudge.mockReturnValue({ data });
    return render(<OscarNudge surface="today" />);
  };

  it('renders nothing when the status is not "ready"', () => {
    const { container } = wrap({ status: 'none', card: null });
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when OSCAR is unavailable', () => {
    const { container } = wrap({ status: 'unavailable', card: null });
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the card headline and body when ready', () => {
    wrap({
      status: 'ready',
      card: {
        headline: 'Lock in your top task',
        body: 'Two tasks are due today.',
        why: 'two due-today tasks',
        proposedAction: null,
        generatedAt: null,
      },
    });
    expect(screen.getByText('Lock in your top task')).toBeInTheDocument();
    expect(screen.getByText('Two tasks are due today.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /dismiss/i })).toBeInTheDocument();
  });
});
