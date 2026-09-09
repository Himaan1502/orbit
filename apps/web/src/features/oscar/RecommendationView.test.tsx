import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RecommendationView } from './RecommendationView';
import type { OscarResponse } from '@/lib/api-types';

const base: OscarResponse = {
  kind: 'recommendation',
  message: 'Focus on Kubernetes this week.',
  sections: {
    action: 'Assess Kubernetes',
    why: 'It is your largest critical gap.',
    impact: 'Readiness would rise ~4 points.',
    evidence: [
      { label: 'gap', kind: 'metric', value: '0.62' },
      { label: 'objective', kind: 'fact', value: 'Platform work is active' },
      { label: 'guess', kind: 'inference', value: 'Likely 2h of study' },
    ],
  },
  proposedActions: null,
  citations: ['careerTarget', 'skillGraph'],
  meta: null,
};

describe('RecommendationView', () => {
  it('renders the UX §25.2 sections and tags each evidence item by kind', () => {
    render(<RecommendationView response={base} />);
    expect(screen.getByText('Assess Kubernetes')).toBeInTheDocument();
    expect(screen.getByText('Why this?')).toBeInTheDocument();
    expect(screen.getByText('fact')).toBeInTheDocument();
    expect(screen.getByText('metric')).toBeInTheDocument();
    expect(screen.getByText('inference')).toBeInTheDocument();
    expect(screen.getByText(/Based on: careerTarget, skillGraph/)).toBeInTheDocument();
  });

  it('renders nothing when there are no sections (a plain answer)', () => {
    const { container } = render(
      <RecommendationView response={{ ...base, kind: 'answer', sections: null }} />
    );
    expect(container).toBeEmptyDOMElement();
  });
});
