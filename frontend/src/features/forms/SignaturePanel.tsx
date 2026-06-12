import { useRef, useState, type PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';

export interface SignaturePayload {
  /** PNG bytes, base64, without the data: URL prefix. */
  signaturePngBase64: string;
  signedByName: string;
}

const CANVAS_WIDTH = 480;
const CANVAS_HEIGHT = 160;

/**
 * Name + draw-to-sign canvas. Pure pointer-event drawing — no dependencies.
 * The Sign button stays disabled until both a name and at least one stroke exist.
 */
export function SignaturePanel({
  onSign,
  signing = false,
  error = null,
}: {
  onSign: (payload: SignaturePayload) => void;
  signing?: boolean;
  error?: string | null;
}) {
  const { t } = useTranslation('forms');
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [hasDrawing, setHasDrawing] = useState(false);
  const [name, setName] = useState('');

  const point = (e: PointerEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    // map CSS pixels to canvas pixels in case the element is scaled
    const scaleX = rect.width ? e.currentTarget.width / rect.width : 1;
    const scaleY = rect.height ? e.currentTarget.height / rect.height : 1;
    return { x: (e.clientX - rect.left) * scaleX, y: (e.clientY - rect.top) * scaleY };
  };

  const onPointerDown = (e: PointerEvent<HTMLCanvasElement>) => {
    const ctx = e.currentTarget.getContext('2d');
    if (!ctx) return;
    try {
      e.currentTarget.setPointerCapture?.(e.pointerId);
    } catch {
      // jsdom: no active pointer to capture
    }
    drawing.current = true;
    const { x, y } = point(e);
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#111827';
    ctx.beginPath();
    ctx.moveTo(x, y);
    // a dot still counts as ink
    ctx.lineTo(x + 0.1, y + 0.1);
    ctx.stroke();
    setHasDrawing(true);
  };

  const onPointerMove = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const ctx = e.currentTarget.getContext('2d');
    if (!ctx) return;
    const { x, y } = point(e);
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const stopDrawing = () => {
    drawing.current = false;
  };

  const clear = () => {
    const canvas = canvasRef.current;
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
    setHasDrawing(false);
  };

  const sign = () => {
    const canvas = canvasRef.current;
    if (!canvas || !hasDrawing || !name.trim()) return;
    const dataUrl = canvas.toDataURL('image/png');
    onSign({
      signaturePngBase64: dataUrl.replace(/^data:image\/png;base64,/, ''),
      signedByName: name.trim(),
    });
  };

  return (
    <div className="space-y-3 rounded-md bg-gray-50 p-4">
      <p className="text-sm font-medium text-gray-900">{t('signThisForm')}</p>
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}
      <Input
        label={t('signedByFullName')}
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder={t('signedByPlaceholder')}
        className="max-w-sm"
      />
      <div>
        <p className="text-sm font-medium text-gray-700">{t('signature')}</p>
        <canvas
          ref={canvasRef}
          data-testid="signature-canvas"
          width={CANVAS_WIDTH}
          height={CANVAS_HEIGHT}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={stopDrawing}
          onPointerLeave={stopDrawing}
          className="mt-1 w-full max-w-md cursor-crosshair touch-none rounded-md bg-white ring-1 ring-inset ring-gray-300"
        />
        <p className="mt-1 text-xs text-gray-500">{t('drawHint')}</p>
      </div>
      <div className="flex gap-2">
        <Button onClick={sign} disabled={!hasDrawing || !name.trim()} loading={signing}>
          {t('sign')}
        </Button>
        <Button variant="secondary" onClick={clear}>
          {t('clear')}
        </Button>
      </div>
    </div>
  );
}
