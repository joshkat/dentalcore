import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { formatDate } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import type { Appointment, AppointmentStatus } from '../../types/api';
import { NEXT_STATUSES, useSendConfirmation, useUpdateAppointmentStatus } from './api';

const statusTone: Record<AppointmentStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  SCHEDULED: 'blue',
  CONFIRMED: 'green',
  CHECKED_IN: 'yellow',
  IN_PROGRESS: 'yellow',
  COMPLETED: 'green',
  NO_SHOW: 'red',
  CANCELLED: 'gray',
};

interface AppointmentDetailModalProps {
  appointment: Appointment | null;
  onClose: () => void;
  onEdit: (appointment: Appointment) => void;
  onCheckout: (appointment: Appointment) => void;
  canWrite: boolean;
}

export function AppointmentDetailModal({
  appointment,
  onClose,
  onEdit,
  onCheckout,
  canWrite,
}: AppointmentDetailModalProps) {
  const { t } = useTranslation('schedule');
  return (
    <Modal title={t('appointment')} open={appointment !== null} onClose={onClose}>
      {appointment && (
        <DetailBody
          key={`${appointment.id}-${appointment.status}`}
          appointment={appointment}
          onClose={onClose}
          onEdit={onEdit}
          onCheckout={onCheckout}
          canWrite={canWrite}
        />
      )}
    </Modal>
  );
}

function DetailBody({
  appointment,
  onClose,
  onEdit,
  onCheckout,
  canWrite,
}: {
  appointment: Appointment;
  onClose: () => void;
  onEdit: (appointment: Appointment) => void;
  onCheckout: (appointment: Appointment) => void;
  canWrite: boolean;
}) {
  const { t } = useTranslation('schedule');
  const updateStatus = useUpdateAppointmentStatus(appointment.id);
  const sendConfirmation = useSendConfirmation();
  const [error, setError] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [confirmingCancel, setConfirmingCancel] = useState(false);

  const starts = new Date(appointment.startsAt);
  const ends = new Date(appointment.endsAt);
  const nextStatuses = NEXT_STATUSES[appointment.status];
  // COMPLETED records history and NO_SHOW is locked; CANCELLED can be rebooked.
  const editable = appointment.status !== 'COMPLETED' && appointment.status !== 'NO_SHOW';

  const transition = async (status: AppointmentStatus, reason?: string) => {
    setError(null);
    try {
      await updateStatus.mutateAsync({ status, cancelReason: reason });
      if (status === 'CANCELLED') onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('failedToUpdateStatus'));
    }
  };

  return (
    <div className="space-y-4">
      {error && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex items-start justify-between">
        <div>
          <Link
            to={`/patients/${appointment.patientId}`}
            className="text-lg font-semibold text-brand-700 hover:underline"
          >
            {appointment.patientLastName}, {appointment.patientFirstName}
          </Link>
          <p className="mt-1 text-sm text-gray-600">
            {formatDate(starts, {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
            })}
            {' · '}
            {formatDate(starts, { hour: 'numeric', minute: '2-digit' })}–
            {formatDate(ends, { hour: 'numeric', minute: '2-digit' })}
          </p>
          <p className="mt-1 text-sm text-gray-600">
            <span
              className="mr-1 inline-block h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: appointment.color }}
            />
            {appointment.providerLastName}, {appointment.providerFirstName} ·{' '}
            {appointment.operatoryName}
          </p>
        </div>
        <span className="flex shrink-0 flex-wrap items-center justify-end gap-2">
          {appointment.asap && <Badge tone="yellow">{t('asapBadge')}</Badge>}
          {appointment.seriesId && <Badge tone="blue">{t('seriesBadge')}</Badge>}
          {appointment.confirmationSentAt && (
            <Badge tone="green">{t('confirmationSentBadge')}</Badge>
          )}
          <Badge tone={statusTone[appointment.status]}>{t(`status.${appointment.status}`)}</Badge>
        </span>
      </div>

      {appointment.procedures.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {appointment.procedures.map((p) => (
            <span
              key={p.procedureCodeId}
              title={p.description ?? undefined}
              className="inline-flex items-center rounded-md bg-gray-100 px-2 py-1 text-xs font-medium text-gray-700"
            >
              <span className="font-mono">{p.code}</span>
              {p.description ? <span className="ml-1">· {p.description}</span> : null}
            </span>
          ))}
        </div>
      )}

      {appointment.notes && (
        <p className="rounded-md bg-gray-50 p-3 text-sm text-gray-700">{appointment.notes}</p>
      )}
      {appointment.cancelledReason && (
        <p className="text-sm text-gray-500">
          {t('cancellationReasonText', { reason: appointment.cancelledReason })}
        </p>
      )}

      {canWrite && (nextStatuses.length > 0 || editable) && (
        <div className="border-t border-gray-100 pt-4">
          {confirmingCancel ? (
            <div className="space-y-2">
              <label htmlFor="cancel-reason" className="block text-sm font-medium text-gray-700">
                {t('cancellationReason')}
              </label>
              <input
                id="cancel-reason"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                className="block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
              <div className="flex gap-2">
                <Button
                  variant="danger"
                  loading={updateStatus.isPending}
                  onClick={() => transition('CANCELLED', cancelReason || undefined)}
                >
                  {t('confirmCancellation')}
                </Button>
                <Button variant="secondary" onClick={() => setConfirmingCancel(false)}>
                  {t('keepAppointment')}
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              {(appointment.status === 'CHECKED_IN' || appointment.status === 'IN_PROGRESS') && (
                <Button onClick={() => onCheckout(appointment)}>{t('checkOut')}</Button>
              )}
              {nextStatuses
                .filter((s) => s !== 'CANCELLED')
                .map((status) => (
                  <Button
                    key={status}
                    variant="secondary"
                    disabled={updateStatus.isPending}
                    onClick={() => transition(status)}
                  >
                    {t(`status.${status}`)}
                  </Button>
                ))}
              {(appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED') && (
                <Button
                  variant="secondary"
                  disabled={sendConfirmation.isPending}
                  onClick={() =>
                    sendConfirmation.mutate(appointment.id, {
                      onError: () => setError(t('confirmationFailed')),
                    })
                  }
                >
                  {appointment.confirmationSentAt
                    ? t('resendConfirmation')
                    : t('sendConfirmation')}
                </Button>
              )}
              {nextStatuses.includes('CANCELLED') && (
                <Button variant="danger" onClick={() => setConfirmingCancel(true)}>
                  {t('cancelEllipsis')}
                </Button>
              )}
              {editable && (
                <Button variant="ghost" onClick={() => onEdit(appointment)}>
                  {appointment.status === 'CANCELLED' ? t('rebookEdit') : t('rescheduleEdit')}
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
