import type { RoadmapTopic } from '@/lib/api-types';

export interface TopicNode extends RoadmapTopic {
  children: TopicNode[];
}

/**
 * Builds the roadmap topic tree from the flat, sort-ordered list the API returns
 * (ADR-021 §2 — topics carry `parentTopicId`; the API keeps them flat). Orphan
 * children (parent missing) are promoted to the top level so nothing is dropped.
 */
export function buildTopicTree(flat: RoadmapTopic[]): TopicNode[] {
  const nodes: TopicNode[] = flat.map((t) => ({ ...t, children: [] }));
  const byId = new Map<string, TopicNode>(nodes.map((n) => [n.id, n]));

  const roots: TopicNode[] = [];
  for (const node of nodes) {
    const parent = node.parentTopicId ? byId.get(node.parentTopicId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  return roots;
}
