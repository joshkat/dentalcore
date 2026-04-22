import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  closeLeaf,
  countLeaves,
  deserializeTree,
  initialTree,
  serializeTree,
  setLeafPath,
  setRatio,
  splitLeaf,
  type PaneNode,
  type SplitSide,
} from './paneTree';

const STORAGE_KEY = 'dentalcore.panes';
export const PANE_DRAG_TYPE = 'application/x-dentalcore-route';

interface PaneContextValue {
  tree: PaneNode;
  paneCount: number;
  /** True while a sidebar nav item is being dragged — panes show drop zones. */
  dragging: boolean;
  setDragging: (dragging: boolean) => void;
  split: (targetLeafId: string, side: SplitSide, path: string) => void;
  close: (leafId: string) => void;
  resize: (splitId: string, ratio: number) => void;
  openInPane: (leafId: string, path: string) => void;
  reportPanePath: (leafId: string, path: string) => void;
}

const PaneContext = createContext<PaneContextValue | null>(null);

export function PaneProvider({ children }: { children: ReactNode }) {
  const [tree, setTree] = useState<PaneNode>(
    () => deserializeTree(localStorage.getItem(STORAGE_KEY)) ?? initialTree(),
  );
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, serializeTree(tree));
  }, [tree]);

  const split = useCallback((targetLeafId: string, side: SplitSide, path: string) => {
    setTree((t) => splitLeaf(t, targetLeafId, side, path));
  }, []);

  const close = useCallback((leafId: string) => {
    setTree((t) => closeLeaf(t, leafId));
  }, []);

  const resize = useCallback((splitId: string, ratio: number) => {
    setTree((t) => setRatio(t, splitId, ratio));
  }, []);

  const openInPane = useCallback((leafId: string, path: string) => {
    setTree((t) => setLeafPath(t, leafId, path, true));
  }, []);

  const reportPanePath = useCallback((leafId: string, path: string) => {
    setTree((t) => setLeafPath(t, leafId, path, false));
  }, []);

  const value = useMemo<PaneContextValue>(
    () => ({
      tree,
      paneCount: countLeaves(tree),
      dragging,
      setDragging,
      split,
      close,
      resize,
      openInPane,
      reportPanePath,
    }),
    [tree, dragging, split, close, resize, openInPane, reportPanePath],
  );

  return <PaneContext.Provider value={value}>{children}</PaneContext.Provider>;
}

export function usePanes(): PaneContextValue {
  const ctx = useContext(PaneContext);
  if (!ctx) throw new Error('usePanes must be used within PaneProvider');
  return ctx;
}
