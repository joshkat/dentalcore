import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface PaneErrorBoundaryProps {
  children: ReactNode;
}

interface PaneErrorBoundaryState {
  error: Error | null;
}

/**
 * Per-pane error boundary: a crash inside one pane must not unmount the whole
 * workspace. The pane header (and its close button for memory panes) lives
 * outside this boundary, so a crashed memory pane can still be closed.
 * Callers key this component so opening a new path (generation bump) resets it.
 */
export class PaneErrorBoundary extends Component<PaneErrorBoundaryProps, PaneErrorBoundaryState> {
  state: PaneErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): PaneErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Surface for debugging; the fallback UI handles recovery.
    console.error('Pane crashed:', error, info.componentStack);
  }

  private retry = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return <PaneCrashFallback error={this.state.error} onRetry={this.retry} />;
    }
    return this.props.children;
  }
}

/** Functional fallback so the class boundary can use the i18n hook. */
function PaneCrashFallback({ error, onRetry }: { error: Error; onRetry: () => void }) {
  const { t } = useTranslation('common');
  return (
    <div
      role="alert"
      className="flex h-full min-h-0 flex-col items-center justify-center gap-3 p-6 text-center"
    >
      <p className="text-sm font-semibold text-gray-900">{t('paneCrashed')}</p>
      <p className="max-w-full break-words text-xs text-gray-500">{error.message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center justify-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        {t('retry')}
      </button>
    </div>
  );
}
