import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { formatDateTime } from '../../i18n/format';
import type { Provider } from '../../types/api';
import {
  useAddTimeOff,
  useProviderHours,
  useProviderTimeOff,
  useRemoveTimeOff,
  useReplaceProviderHours,
  type HoursBlock,
} from './api';

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export function AvailabilityModal({
  provider,
  onClose,
}: {
  provider: Provider | null;
  onClose: () => void;
}) {
  const { t } = useTranslation('providers');
  return (
    <Modal
      title={
        provider
          ? t('availabilityTitle', {
              lastName: provider.lastName,
              firstName: provider.firstName,
            })
          : ''
      }
      open={provider !== null}
      onClose={onClose}
    >
      {provider && <AvailabilityBody key={provider.id} providerId={provider.id} />}
    </Modal>
  );
}

function AvailabilityBody({ providerId }: { providerId: string }) {
  const { t } = useTranslation('providers');
  const { data: savedHours, isPending } = useProviderHours(providerId);
  const replaceHours = useReplaceProviderHours(providerId);
  const { data: timeOff } = useProviderTimeOff(providerId);
  const addTimeOff = useAddTimeOff(providerId);
  const removeTimeOff = useRemoveTimeOff(providerId);

  const [blocks, setBlocks] = useState<HoursBlock[]>([]);
  const [offStart, setOffStart] = useState('');
  const [offEnd, setOffEnd] = useState('');
  const [offReason, setOffReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (savedHours) {
      setBlocks(savedHours.map((b) => ({ ...b, startTime: b.startTime.slice(0, 5),
        endTime: b.endTime.slice(0, 5) })));
    }
  }, [savedHours]);

  if (isPending) return <Spinner label={t('loadingAvailability')} />;

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('actionFailed'));
    }
  };

  return (
    <div className="space-y-5">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <section>
        <h3 className="text-sm font-semibold text-gray-900">{t('weeklyWorkingHours')}</h3>
        <p className="text-xs text-gray-500">{t('weeklyHoursHint')}</p>
        <div className="mt-2 space-y-2">
          {blocks.map((block, index) => (
            <div key={index} className="flex items-center gap-2">
              <select
                aria-label={t('dayAriaLabel')}
                value={block.dayOfWeek}
                onChange={(e) =>
                  setBlocks((current) =>
                    current.map((b, i) =>
                      i === index ? { ...b, dayOfWeek: Number(e.target.value) } : b,
                    ),
                  )
                }
                className="rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              >
                {DAYS.map((d, i) => (
                  <option key={d} value={i + 1}>
                    {t(`day.${d}`)}
                  </option>
                ))}
              </select>
              <input
                type="time"
                aria-label={t('startTime')}
                value={block.startTime}
                onChange={(e) =>
                  setBlocks((current) =>
                    current.map((b, i) =>
                      i === index ? { ...b, startTime: e.target.value } : b,
                    ),
                  )
                }
                className="rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
              <span className="text-gray-400">–</span>
              <input
                type="time"
                aria-label={t('endTime')}
                value={block.endTime}
                onChange={(e) =>
                  setBlocks((current) =>
                    current.map((b, i) => (i === index ? { ...b, endTime: e.target.value } : b)),
                  )
                }
                className="rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
              <button
                onClick={() => setBlocks((current) => current.filter((_, i) => i !== index))}
                className="text-xs text-red-600 hover:underline"
              >
                {t('remove')}
              </button>
            </div>
          ))}
          <div className="flex gap-2">
            <Button
              variant="secondary"
              onClick={() =>
                setBlocks((current) => [
                  ...current,
                  { dayOfWeek: 1, startTime: '09:00', endTime: '17:00' },
                ])
              }
            >
              {t('addBlock')}
            </Button>
            <Button
              loading={replaceHours.isPending}
              onClick={() => act(() => replaceHours.mutateAsync(blocks))}
            >
              {t('saveHours')}
            </Button>
          </div>
        </div>
      </section>

      <section className="border-t border-gray-100 pt-4">
        <h3 className="text-sm font-semibold text-gray-900">{t('timeOff')}</h3>
        <ul className="mt-2 space-y-1">
          {timeOff?.map((block) => (
            <li key={block.id} className="flex items-center justify-between text-sm">
              <span>
                {formatDateTime(block.startsAt)} →{' '}
                {formatDateTime(block.endsAt)}
                {block.reason && <span className="text-gray-500"> · {block.reason}</span>}
              </span>
              <button
                onClick={() => act(() => removeTimeOff.mutateAsync(block.id))}
                className="text-xs text-red-600 hover:underline"
              >
                {t('remove')}
              </button>
            </li>
          ))}
          {timeOff && timeOff.length === 0 && (
            <li className="text-sm text-gray-500">{t('noTimeOff')}</li>
          )}
        </ul>
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="off-start" className="block text-xs font-medium text-gray-700">
              {t('from')}
            </label>
            <input
              id="off-start"
              type="datetime-local"
              value={offStart}
              onChange={(e) => setOffStart(e.target.value)}
              className="mt-1 rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <div>
            <label htmlFor="off-end" className="block text-xs font-medium text-gray-700">
              {t('to')}
            </label>
            <input
              id="off-end"
              type="datetime-local"
              value={offEnd}
              onChange={(e) => setOffEnd(e.target.value)}
              className="mt-1 rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <div>
            <label htmlFor="off-reason" className="block text-xs font-medium text-gray-700">
              {t('reason')}
            </label>
            <input
              id="off-reason"
              value={offReason}
              onChange={(e) => setOffReason(e.target.value)}
              className="mt-1 rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <Button
            loading={addTimeOff.isPending}
            onClick={() =>
              act(async () => {
                if (!offStart || !offEnd) throw new ApiError(400, { detail: t('pickBothTimes') });
                await addTimeOff.mutateAsync({
                  startsAt: new Date(offStart).toISOString(),
                  endsAt: new Date(offEnd).toISOString(),
                  reason: offReason || undefined,
                });
                setOffStart('');
                setOffEnd('');
                setOffReason('');
              })
            }
          >
            {t('blockTime')}
          </Button>
        </div>
      </section>
    </div>
  );
}
