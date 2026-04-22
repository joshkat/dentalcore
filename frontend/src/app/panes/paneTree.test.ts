import { describe, expect, it } from 'vitest';
import {
  closeLeaf,
  countLeaves,
  deserializeTree,
  findLeaf,
  initialTree,
  MAX_PANES,
  PRIMARY_PANE_ID,
  serializeTree,
  setLeafPath,
  setRatio,
  splitLeaf,
  type PaneNode,
  type SplitNode,
} from './paneTree';

function memoryIds(node: PaneNode): string[] {
  if (node.type === 'leaf') return node.kind === 'memory' ? [node.id] : [];
  return [...memoryIds(node.first), ...memoryIds(node.second)];
}

describe('paneTree', () => {
  it('splits the primary pane to the right into a row layout', () => {
    const tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/patients');
    expect(tree.type).toBe('split');
    const split = tree as SplitNode;
    expect(split.direction).toBe('row');
    expect(split.first).toMatchObject({ kind: 'primary' });
    expect(split.second).toMatchObject({ kind: 'memory', path: '/patients' });
    expect(countLeaves(tree)).toBe(2);
  });

  it('places the new pane first for left/top drops', () => {
    const tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'top', '/schedule') as SplitNode;
    expect(tree.direction).toBe('column');
    expect(tree.first).toMatchObject({ kind: 'memory', path: '/schedule' });
    expect(tree.second).toMatchObject({ kind: 'primary' });
  });

  it('builds the tmux 3-way: right split, then bottom of the right pane', () => {
    let tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/patients');
    const [rightId] = memoryIds(tree);
    tree = splitLeaf(tree, rightId, 'bottom', '/schedule');
    expect(countLeaves(tree)).toBe(3);
    const outer = tree as SplitNode;
    expect(outer.direction).toBe('row');
    const inner = outer.second as SplitNode;
    expect(inner.direction).toBe('column');
    expect(inner.first).toMatchObject({ path: '/patients' });
    expect(inner.second).toMatchObject({ path: '/schedule' });
  });

  it('refuses to grow past MAX_PANES', () => {
    let tree = initialTree();
    tree = splitLeaf(tree, PRIMARY_PANE_ID, 'right', '/a');
    tree = splitLeaf(tree, PRIMARY_PANE_ID, 'bottom', '/b');
    tree = splitLeaf(tree, memoryIds(tree)[0], 'bottom', '/c');
    expect(countLeaves(tree)).toBe(MAX_PANES);
    const before = serializeTree(tree);
    tree = splitLeaf(tree, PRIMARY_PANE_ID, 'right', '/d');
    expect(serializeTree(tree)).toBe(before);
  });

  it('closing a pane promotes its sibling; primary cannot be closed', () => {
    let tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/patients');
    const [memId] = memoryIds(tree);
    expect(closeLeaf(tree, PRIMARY_PANE_ID)).toBe(tree);
    tree = closeLeaf(tree, memId);
    expect(tree).toMatchObject({ type: 'leaf', kind: 'primary' });
  });

  it('clamps ratios to 0.2–0.8', () => {
    const tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/a') as SplitNode;
    const updated = setRatio(tree, tree.id, 0.05) as SplitNode;
    expect(updated.ratio).toBe(0.2);
    const updated2 = setRatio(tree, tree.id, 0.95) as SplitNode;
    expect(updated2.ratio).toBe(0.8);
  });

  it('setLeafPath records navigation without remount and bumps generation on remount', () => {
    let tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/patients');
    const [memId] = memoryIds(tree);
    tree = setLeafPath(tree, memId, '/patients/123', false);
    let leaf = findLeaf(tree, memId);
    expect(leaf).toMatchObject({ path: '/patients/123', generation: 0 });
    tree = setLeafPath(tree, memId, '/reports', true);
    leaf = findLeaf(tree, memId);
    expect(leaf).toMatchObject({ path: '/reports', generation: 1 });
  });

  it('round-trips through serialization and rejects junk', () => {
    let tree = splitLeaf(initialTree(), PRIMARY_PANE_ID, 'right', '/claims');
    tree = splitLeaf(tree, PRIMARY_PANE_ID, 'bottom', '/reports');
    const restored = deserializeTree(serializeTree(tree));
    expect(restored).toEqual(tree);
    expect(deserializeTree(null)).toBeNull();
    expect(deserializeTree('not json')).toBeNull();
    expect(deserializeTree('{"type":"leaf","id":"x","kind":"memory"}')).toBeNull();
    expect(deserializeTree(JSON.stringify({ type: 'leaf', id: 'x', kind: 'primary' }))).toMatchObject({
      kind: 'primary',
    });
  });
});
