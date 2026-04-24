export type SplitSide = 'left' | 'right' | 'top' | 'bottom';

export type LeafNode =
  | { type: 'leaf'; id: string; kind: 'primary' }
  | { type: 'leaf'; id: string; kind: 'memory'; path: string; generation: number };

export interface SplitNode {
  type: 'split';
  id: string;
  direction: 'row' | 'column';
  ratio: number;
  first: PaneNode;
  second: PaneNode;
}

export type PaneNode = LeafNode | SplitNode;

export const MAX_PANES = 4;
export const PRIMARY_PANE_ID = 'primary';

let counter = 0;
function nextId(): string {
  counter += 1;
  return `pane-${Date.now().toString(36)}-${counter}`;
}

export function initialTree(): PaneNode {
  return { type: 'leaf', id: PRIMARY_PANE_ID, kind: 'primary' };
}

export function countLeaves(node: PaneNode): number {
  return node.type === 'leaf' ? 1 : countLeaves(node.first) + countLeaves(node.second);
}

export function findLeaf(node: PaneNode, id: string): LeafNode | null {
  if (node.type === 'leaf') {
    return node.id === id ? node : null;
  }
  return findLeaf(node.first, id) ?? findLeaf(node.second, id);
}

/** Split the target leaf, placing a new memory pane on the given side. No-op at MAX_PANES. */
export function splitLeaf(root: PaneNode, targetId: string, side: SplitSide, path: string): PaneNode {
  if (countLeaves(root) >= MAX_PANES) return root;

  const newLeaf: LeafNode = { type: 'leaf', id: nextId(), kind: 'memory', path, generation: 0 };

  const replace = (node: PaneNode): PaneNode => {
    if (node.type === 'leaf') {
      if (node.id !== targetId) return node;
      const direction = side === 'left' || side === 'right' ? 'row' : 'column';
      const newFirst = side === 'left' || side === 'top';
      return {
        type: 'split',
        id: nextId(),
        direction,
        ratio: 0.5,
        first: newFirst ? newLeaf : node,
        second: newFirst ? node : newLeaf,
      };
    }
    return { ...node, first: replace(node.first), second: replace(node.second) };
  };

  return replace(root);
}

/** Remove a leaf; its sibling takes the parent's slot. The primary pane cannot be closed. */
export function closeLeaf(root: PaneNode, targetId: string): PaneNode {
  if (targetId === PRIMARY_PANE_ID) return root;

  const remove = (node: PaneNode): PaneNode => {
    if (node.type === 'leaf') return node;
    if (node.first.type === 'leaf' && node.first.id === targetId) return remove(node.second);
    if (node.second.type === 'leaf' && node.second.id === targetId) return remove(node.first);
    return { ...node, first: remove(node.first), second: remove(node.second) };
  };

  return remove(root);
}

export function setRatio(root: PaneNode, splitId: string, ratio: number): PaneNode {
  const clamped = Math.min(0.8, Math.max(0.2, ratio));
  const walk = (node: PaneNode): PaneNode => {
    if (node.type === 'leaf') return node;
    if (node.id === splitId) return { ...node, ratio: clamped };
    return { ...node, first: walk(node.first), second: walk(node.second) };
  };
  return walk(root);
}

/**
 * Point a memory pane at a new path. `remount` bumps the generation so the pane's
 * MemoryRouter is recreated (used for drops); without it this just records internal
 * navigation for persistence.
 */
export function setLeafPath(root: PaneNode, targetId: string, path: string, remount: boolean): PaneNode {
  const walk = (node: PaneNode): PaneNode => {
    if (node.type === 'leaf') {
      if (node.id !== targetId || node.kind !== 'memory') return node;
      if (node.path === path && !remount) return node;
      return { ...node, path, generation: remount ? node.generation + 1 : node.generation };
    }
    return { ...node, first: walk(node.first), second: walk(node.second) };
  };
  return walk(root);
}

/**
 * Whitelist of pane path shapes. paneTree must stay dependency-free, so this
 * is hardcoded — KEEP IN SYNC with the route table in ../pageRoutes.tsx.
 * Persisted pane paths come from localStorage, which an attacker (or a stale
 * build) can poison; anything not matching falls back to the initial layout.
 */
const ALLOWED_PANE_PATHS: readonly RegExp[] = [
  /^\/$/, // Dashboard
  /^\/schedule$/,
  /^\/patients$/,
  /^\/patients\/new$/,
  /^\/patients\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i, // /patients/:id (uuid)
  /^\/providers$/,
  /^\/procedures$/,
  /^\/insurance$/,
  /^\/claims$/,
  /^\/reports$/,
  /^\/recall$/,
  /^\/users$/,
];

export function isAllowedPanePath(path: string): boolean {
  return ALLOWED_PANE_PATHS.some((re) => re.test(path));
}

export function serializeTree(root: PaneNode): string {
  return JSON.stringify(root);
}

export function deserializeTree(raw: string | null): PaneNode | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    return isValid(parsed) && hasOnePrimary(parsed as PaneNode) ? (parsed as PaneNode) : null;
  } catch {
    return null;
  }
}

function isValid(node: unknown): node is PaneNode {
  if (typeof node !== 'object' || node === null) return false;
  const n = node as Record<string, unknown>;
  if (n.type === 'leaf') {
    if (typeof n.id !== 'string') return false;
    if (n.kind === 'primary') return true;
    return (
      n.kind === 'memory' &&
      typeof n.path === 'string' &&
      isAllowedPanePath(n.path) &&
      typeof n.generation === 'number'
    );
  }
  if (n.type === 'split') {
    return (
      typeof n.id === 'string' &&
      (n.direction === 'row' || n.direction === 'column') &&
      typeof n.ratio === 'number' &&
      n.ratio >= 0.2 &&
      n.ratio <= 0.8 &&
      isValid(n.first) &&
      isValid(n.second)
    );
  }
  return false;
}

function hasOnePrimary(node: PaneNode): boolean {
  const count = (n: PaneNode): number =>
    n.type === 'leaf' ? (n.kind === 'primary' ? 1 : 0) : count(n.first) + count(n.second);
  return count(node) === 1 && countLeaves(node) <= MAX_PANES;
}
