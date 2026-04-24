import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PaneErrorBoundary } from './PaneErrorBoundary';

function Bomb({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) throw new Error('kaboom in pane');
  return <div>pane content alive</div>;
}

describe('PaneErrorBoundary', () => {
  beforeEach(() => {
    // React logs caught render errors; keep test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the fallback instead of unmounting when a child throws', () => {
    render(
      <PaneErrorBoundary>
        <Bomb shouldThrow />
      </PaneErrorBoundary>,
    );

    expect(screen.getByText('This pane crashed')).toBeInTheDocument();
    expect(screen.getByText('kaboom in pane')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.queryByText('pane content alive')).not.toBeInTheDocument();
  });

  it('recovers via Retry once the child no longer throws', async () => {
    const { rerender } = render(
      <PaneErrorBoundary>
        <Bomb shouldThrow />
      </PaneErrorBoundary>,
    );
    expect(screen.getByText('This pane crashed')).toBeInTheDocument();

    // Fix the child; the boundary keeps showing the fallback until Retry.
    rerender(
      <PaneErrorBoundary>
        <Bomb shouldThrow={false} />
      </PaneErrorBoundary>,
    );
    expect(screen.getByText('This pane crashed')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(screen.getByText('pane content alive')).toBeInTheDocument();
    expect(screen.queryByText('This pane crashed')).not.toBeInTheDocument();
  });

  it('resets when remounted under a new key (generation bump)', () => {
    const { rerender } = render(
      <PaneErrorBoundary key="gen-0">
        <Bomb shouldThrow />
      </PaneErrorBoundary>,
    );
    expect(screen.getByText('This pane crashed')).toBeInTheDocument();

    rerender(
      <PaneErrorBoundary key="gen-1">
        <Bomb shouldThrow={false} />
      </PaneErrorBoundary>,
    );
    expect(screen.getByText('pane content alive')).toBeInTheDocument();
  });
});
