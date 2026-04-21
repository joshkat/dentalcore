import {
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';
import {
  MemoryRouter,
  Navigate,
  UNSAFE_DataRouterContext,
  UNSAFE_DataRouterStateContext,
  UNSAFE_LocationContext,
  UNSAFE_RouteContext,
  useLocation,
  useNavigate,
  useRoutes,
} from 'react-router-dom';
import { pageRoutes, pageTitle } from '../pageRoutes';
import { PANE_DRAG_TYPE, usePanes } from './PaneProvider';
import {
  MAX_PANES,
  type LeafNode,
  type PaneNode,
  type SplitNode,
  type SplitSide,
} from './paneTree';

type DropZone = SplitSide | 'center';

export function PaneManager({ children }: { children: ReactNode }) {
  const { tree } = usePanes();
  return <div className="flex h-full min-h-0 flex-1">{renderNode(tree, children)}</div>;
}

function renderNode(node: PaneNode, primaryContent: ReactNode): ReactNode {
  if (node.type === 'leaf') {
    return <PaneLeaf key={node.id} leaf={node} primaryContent={primaryContent} />;
  }
  return <Split key={node.id} node={node} primaryContent={primaryContent} />;
}

function Split({ node, primaryContent }: { node: SplitNode; primaryContent: ReactNode }) {
  const { resize } = usePanes();
  const containerRef = useRef<HTMLDivElement>(null);
  const isRow = node.direction === 'row';

  const onDividerPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onDividerPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!e.currentTarget.hasPointerCapture(e.pointerId)) return;
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const ratio = isRow
      ? (e.clientX - rect.left) / rect.width
      : (e.clientY - rect.top) / rect.height;
    resize(node.id, ratio);
  };

  return (
    <div
      ref={containerRef}
      className={`flex min-h-0 min-w-0 flex-1 ${isRow ? 'flex-row' : 'flex-col'}`}
    >
      <div className="flex min-h-0 min-w-0" style={{ flexBasis: `${node.ratio * 100}%` }}>
        {renderNode(node.first, primaryContent)}
      </div>
      <div
        role="separator"
        aria-orientation={isRow ? 'vertical' : 'horizontal'}
        onPointerDown={onDividerPointerDown}
        onPointerMove={onDividerPointerMove}
        className={`shrink-0 bg-gray-200 transition-colors hover:bg-brand-500 ${
          isRow ? 'w-1 cursor-col-resize' : 'h-1 cursor-row-resize'
        }`}
      />
      <div className="flex min-h-0 min-w-0 flex-1">{renderNode(node.second, primaryContent)}</div>
    </div>
  );
}

function PaneLeaf({ leaf, primaryContent }: { leaf: LeafNode; primaryContent: ReactNode }) {
  const { paneCount, dragging, close } = usePanes();
  const isPrimary = leaf.kind === 'primary';

  return (
    <section
      className="relative flex min-h-0 min-w-0 flex-1 flex-col bg-white"
      aria-label={isPrimary ? 'Main pane' : `Pane: ${pageTitle(leaf.path)}`}
      data-pane={leaf.id}
    >
      {paneCount > 1 && (
        <header className="flex h-7 shrink-0 items-center justify-between border-b border-gray-200 bg-gray-50 px-2">
          <span className="truncate text-xs font-medium text-gray-500">
            {isPrimary ? <PrimaryTitle /> : pageTitle(leaf.path)}
          </span>
          {!isPrimary && (
            <button
              onClick={() => close(leaf.id)}
              aria-label="Close pane"
              className="rounded px-1 text-xs text-gray-400 hover:bg-gray-200 hover:text-gray-700"
            >
              ✕
            </button>
          )}
        </header>
      )}
      <div className="min-h-0 flex-1 overflow-auto">
        {isPrimary ? primaryContent : <PaneView leaf={leaf} />}
      </div>
      {dragging && <DropOverlay leaf={leaf} />}
    </section>
  );
}

function PrimaryTitle() {
  const location = useLocation();
  return <>{pageTitle(location.pathname)} · main</>;
}

/**
 * Each pane runs its own MemoryRouter so in-pane navigation never touches the
 * browser URL. React Router v6 forbids nested routers, so the outer router's
 * contexts are reset first — the UNSAFE_* escape hatch endorsed for embedding.
 */
function PaneView({ leaf }: { leaf: LeafNode & { kind: 'memory' } }) {
  return (
    <UNSAFE_DataRouterContext.Provider value={null}>
      <UNSAFE_DataRouterStateContext.Provider value={null}>
        <UNSAFE_LocationContext.Provider value={null as never}>
          <UNSAFE_RouteContext.Provider value={{ outlet: null, matches: [], isDataRoute: false }}>
            <MemoryRouter key={`${leaf.id}-${leaf.generation}`} initialEntries={[leaf.path]}>
              <PaneRoutes leafId={leaf.id} />
            </MemoryRouter>
          </UNSAFE_RouteContext.Provider>
        </UNSAFE_LocationContext.Provider>
      </UNSAFE_DataRouterStateContext.Provider>
    </UNSAFE_DataRouterContext.Provider>
  );
}

function PaneRoutes({ leafId }: { leafId: string }) {
  const element = useRoutes([...pageRoutes, { path: '*', element: <Navigate to="/" replace /> }]);
  const location = useLocation();
  const { reportPanePath } = usePanes();

  useEffect(() => {
    reportPanePath(leafId, location.pathname);
  }, [leafId, location.pathname, reportPanePath]);

  return <>{element}</>;
}

function DropOverlay({ leaf }: { leaf: LeafNode }) {
  const { paneCount, split, openInPane, setDragging } = usePanes();
  const navigate = useNavigate();
  const [zone, setZone] = useState<DropZone | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const splitAllowed = paneCount < MAX_PANES;

  const zoneFor = (e: DragEvent): DropZone => {
    const rect = ref.current!.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    if (!splitAllowed || (x > 0.3 && x < 0.7 && y > 0.3 && y < 0.7)) return 'center';
    const edges: Array<[DropZone, number]> = [
      ['left', x],
      ['right', 1 - x],
      ['top', y],
      ['bottom', 1 - y],
    ];
    edges.sort((a, b) => a[1] - b[1]);
    return edges[0][0];
  };

  const onDragOver = (e: DragEvent) => {
    if (!e.dataTransfer.types.includes(PANE_DRAG_TYPE)) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
    setZone(zoneFor(e));
  };

  const onDrop = (e: DragEvent) => {
    const path = e.dataTransfer.getData(PANE_DRAG_TYPE);
    if (!path) return;
    e.preventDefault();
    const dropZone = zoneFor(e);
    if (dropZone === 'center') {
      if (leaf.kind === 'primary') navigate(path);
      else openInPane(leaf.id, path);
    } else {
      split(leaf.id, dropZone, path);
    }
    setZone(null);
    setDragging(false);
  };

  const highlight: Record<DropZone, string> = {
    center: 'inset-0',
    left: 'inset-y-0 left-0 w-1/2',
    right: 'inset-y-0 right-0 w-1/2',
    top: 'inset-x-0 top-0 h-1/2',
    bottom: 'inset-x-0 bottom-0 h-1/2',
  };

  return (
    <div
      ref={ref}
      data-pane-dropzone={leaf.id}
      className="absolute inset-0 z-20"
      onDragOver={onDragOver}
      onDragLeave={() => setZone(null)}
      onDrop={onDrop}
    >
      {zone && (
        <div
          className={`pointer-events-none absolute rounded-md border-2 border-brand-500 bg-brand-500/20 ${highlight[zone]}`}
        />
      )}
    </div>
  );
}
