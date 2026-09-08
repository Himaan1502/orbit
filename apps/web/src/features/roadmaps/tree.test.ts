import { describe, it, expect } from 'vitest';
import { buildTopicTree } from './tree';
import type { RoadmapTopic } from '@/lib/api-types';

function topic(id: string, parentTopicId: string | null): RoadmapTopic {
  return {
    id,
    parentTopicId,
    name: id,
    sortOrder: 0,
    sourceUrl: null,
    status: 'not_started',
    coverage: 0,
    skills: [],
  };
}

describe('buildTopicTree', () => {
  it('nests children under their parent and keeps roots at the top', () => {
    const tree = buildTopicTree([
      topic('a', null),
      topic('a1', 'a'),
      topic('a2', 'a'),
      topic('b', null),
    ]);
    expect(tree.map((t) => t.id)).toEqual(['a', 'b']);
    expect(tree[0].children.map((c) => c.id)).toEqual(['a1', 'a2']);
    expect(tree[1].children).toEqual([]);
  });

  it('promotes an orphan (missing parent) to the top level rather than dropping it', () => {
    const tree = buildTopicTree([topic('a', null), topic('x', 'ghost')]);
    expect(tree.map((t) => t.id).sort()).toEqual(['a', 'x']);
  });

  it('handles a flat roadmap with no parents', () => {
    const tree = buildTopicTree([topic('a', null), topic('b', null), topic('c', null)]);
    expect(tree).toHaveLength(3);
    expect(tree.every((t) => t.children.length === 0)).toBe(true);
  });
});
