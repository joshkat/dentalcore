import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { SignaturePanel } from './SignaturePanel';

// jsdom has neither PointerEvent nor a canvas implementation; stub both.
class FakePointerEvent extends MouseEvent {
  pointerId: number;
  constructor(type: string, props: MouseEventInit & { pointerId?: number } = {}) {
    super(type, props);
    this.pointerId = props.pointerId ?? 1;
  }
}

const ctxStub = {
  beginPath: vi.fn(),
  moveTo: vi.fn(),
  lineTo: vi.fn(),
  stroke: vi.fn(),
  clearRect: vi.fn(),
  lineWidth: 0,
  lineCap: '',
  lineJoin: '',
  strokeStyle: '',
};

const FAKE_PNG_DATA_URL = 'data:image/png;base64,iVBORfakePngBytes==';

beforeAll(() => {
  vi.stubGlobal('PointerEvent', FakePointerEvent);
  HTMLCanvasElement.prototype.getContext = vi.fn(
    () => ctxStub,
  ) as unknown as HTMLCanvasElement['getContext'];
  HTMLCanvasElement.prototype.toDataURL = vi.fn(() => FAKE_PNG_DATA_URL);
});

afterAll(() => {
  vi.unstubAllGlobals();
});

function draw(canvas: HTMLElement) {
  fireEvent.pointerDown(canvas, { clientX: 10, clientY: 10 });
  fireEvent.pointerMove(canvas, { clientX: 40, clientY: 30 });
  fireEvent.pointerMove(canvas, { clientX: 70, clientY: 20 });
  fireEvent.pointerUp(canvas, { clientX: 70, clientY: 20 });
}

describe('SignaturePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keeps Sign disabled until both a name and a drawing exist', async () => {
    render(<SignaturePanel onSign={vi.fn()} />);
    const signButton = screen.getByRole('button', { name: 'Sign' });
    expect(signButton).toBeDisabled();

    // name alone is not enough
    await userEvent.type(screen.getByLabelText('Signed by (full name)'), 'Emma Demoson');
    expect(signButton).toBeDisabled();

    // drawing completes the requirements
    draw(screen.getByTestId('signature-canvas'));
    expect(signButton).toBeEnabled();
  });

  it('draw events stroke the canvas and signing emits a non-empty PNG without the data: prefix', async () => {
    const onSign = vi.fn();
    render(<SignaturePanel onSign={onSign} />);

    await userEvent.type(screen.getByLabelText('Signed by (full name)'), 'Emma Demoson');
    draw(screen.getByTestId('signature-canvas'));

    expect(ctxStub.moveTo).toHaveBeenCalled();
    expect(ctxStub.lineTo).toHaveBeenCalled();
    expect(ctxStub.stroke).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Sign' }));
    expect(onSign).toHaveBeenCalledWith({
      signaturePngBase64: 'iVBORfakePngBytes==',
      signedByName: 'Emma Demoson',
    });
    expect(onSign.mock.calls[0][0].signaturePngBase64.length).toBeGreaterThan(0);
    expect(onSign.mock.calls[0][0].signaturePngBase64).not.toContain('data:');
  });

  it('Clear wipes the canvas and disables Sign again', async () => {
    render(<SignaturePanel onSign={vi.fn()} />);
    await userEvent.type(screen.getByLabelText('Signed by (full name)'), 'Emma Demoson');
    draw(screen.getByTestId('signature-canvas'));
    expect(screen.getByRole('button', { name: 'Sign' })).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(ctxStub.clearRect).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Sign' })).toBeDisabled();
  });
});
