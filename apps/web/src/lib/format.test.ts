import { describe, it, expect } from 'vitest';
import { formatMinutes, impactLabel, relativeDueDate } from './format';

describe('formatMinutes', () => {
  it('formats sub-hour and multi-hour durations', () => {
    expect(formatMinutes(0)).toBe('—');
    expect(formatMinutes(45)).toBe('45 min');
    expect(formatMinutes(60)).toBe('1 h');
    expect(formatMinutes(90)).toBe('1 h 30 min');
  });
});

describe('impactLabel', () => {
  it('buckets the priority score', () => {
    expect(impactLabel(0.8)).toBe('HIGH');
    expect(impactLabel(0.5)).toBe('MEDIUM');
    expect(impactLabel(0.2)).toBe('LOW');
    expect(impactLabel(null)).toBe('MEDIUM');
  });
});

describe('relativeDueDate', () => {
  it('describes near-term due dates relative to now', () => {
    const inTwoDays = new Date(Date.now() + 2 * 86_400_000).toISOString();
    const yesterday = new Date(Date.now() - 86_400_000).toISOString();
    expect(relativeDueDate(inTwoDays)).toBe('due in 2d');
    expect(relativeDueDate(yesterday)).toBe('1d overdue');
    expect(relativeDueDate(null)).toBe('');
  });
});
