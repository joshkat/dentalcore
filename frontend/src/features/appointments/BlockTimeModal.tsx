import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import type { Operatory } from '../../types/api';
import { useCreateBlockout, useOperatories } from './api';

const fieldClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-brand-600';

export function BlockTimeModal({
  defaultDate,
  onClose,
}: {
  defaultDate: string;
  onClose: () => void;
}) {
  const { t } = useTranslation('schedule');
  const { data: operatories } = useOperatories();
  const createBlockout = useCreateBlockout();

  const [operatoryId, setOperatoryId] = useState('');
  const [date, setDate] = useState(defaultDate);
  const [start, setStart] = useState('12:00');
  const [end, setEnd] = useState('13:00');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!operatoryId) {
      setError(t('validation'));
      return;
    }
    const startsAt = new Date(`${date}T${start}`);
    const endsAt = new Date(`${date}T${end}`);
    if (endsAt <= startsAt) {
      setError(t('validation'));
      return;
    }
    try {
      await createBlockout.mutateAsync({
        operatoryId,
        startsAt: startsAt.toISOString(),
        endsAt: endsAt.toISOString(),
        reason: reason || undefined,
      });
      onClose();
    } catch {
      setError(t('blockoutConflict'));
    }
  };

  return (
    <Modal title={t('blockoutTitle')} open onClose={onClose}>
      <div className="space-y-4">
        {error && (
          <p role="alert" className="text-sm text-red-600">
            {error}
          </p>
        )}
        <div>
          <label htmlFor="blk-op" className="block text-sm font-medium text-gray-700">
            {t('operatory')}
          </label>
          <select
            id="blk-op"
            value={operatoryId}
            onChange={(e) => setOperatoryId(e.target.value)}
            className={fieldClass}
          >
            <option value="">{t('select')}</option>
            {operatories?.map((o: Operatory) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label htmlFor="blk-date" className="block text-sm font-medium text-gray-700">
              {t('date')}
            </label>
            <input
              id="blk-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className={fieldClass}
            />
          </div>
          <div>
            <label htmlFor="blk-start" className="block text-sm font-medium text-gray-700">
              {t('start')}
            </label>
            <input
              id="blk-start"
              type="time"
              value={start}
              onChange={(e) => setStart(e.target.value)}
              className={fieldClass}
            />
          </div>
          <div>
            <label htmlFor="blk-end" className="block text-sm font-medium text-gray-700">
              {t('blockoutEnd')}
            </label>
            <input
              id="blk-end"
              type="time"
              value={end}
              onChange={(e) => setEnd(e.target.value)}
              className={fieldClass}
            />
          </div>
        </div>
        <div>
          <label htmlFor="blk-reason" className="block text-sm font-medium text-gray-700">
            {t('blockoutReason')}
          </label>
          <input
            id="blk-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('blockoutReasonPlaceholder')}
            className={fieldClass}
          />
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            {t('keepAppointment')}
          </Button>
          <Button onClick={submit} loading={createBlockout.isPending}>
            {t('blockoutCreate')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
