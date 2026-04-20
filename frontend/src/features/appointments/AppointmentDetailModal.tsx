import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import type { Appointment, AppointmentStatus } from '../../types/api';
import { NEXT_STATUSES, STATUS_LABELS, useUpdateAppointmentStatus } from './api';

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
  canWrite: boolean;
}

export function AppointmentDetailModal({
  appointment,
  onClose,
  onEdit,
  canWrite,
}: AppointmentDetailModalProps) {
  return (
    <Modal title="Appointment" open={appointment !== null} onClose={onClose}>
      {appointment && (
        <DetailBody
          key={`${appointment.id}-${appointment.status}`}
          appointment={appointment}
          onClose={onClose}
          onEdit={onEdit}
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
  canWrite,
}: {
  appointment: Appointment;
  onClose: () => void;
  onEdit: (appointment: Appointment) => void;
  canWrite: boolean;
}) {
  const updateStatus = useUpdateAppointmentStatus(appointment.id);
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
      setError(e instanceof ApiError ? e.message : 'Failed to update status');
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
            {starts.toLocaleDateString(undefined, {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
            })}
            {' · '}
            {starts.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}–
            {ends.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
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
        <Badge tone={statusTone[appointment.status]}>{STATUS_LABELS[appointment.status]}</Badge>
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
        <p className="text-sm text-gray-500">Cancellation reason: {appointment.cancelledReason}</p>
      )}

      {canWrite && (nextStatuses.length > 0 || editable) && (
        <div className="border-t border-gray-100 pt-4">
          {confirmingCancel ? (
            <div className="space-y-2">
              <label htmlFor="cancel-reason" className="block text-sm font-medium text-gray-700">
                Cancellation reason
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
                  Confirm cancellation
                </Button>
                <Button variant="secondary" onClick={() => setConfirmingCancel(false)}>
                  Keep appointment
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              {nextStatuses
                .filter((s) => s !== 'CANCELLED')
                .map((status) => (
                  <Button
                    key={status}
                    variant="secondary"
                    disabled={updateStatus.isPending}
                    onClick={() => transition(status)}
                  >
                    {STATUS_LABELS[status]}
                  </Button>
                ))}
              {nextStatuses.includes('CANCELLED') && (
                <Button variant="danger" onClick={() => setConfirmingCancel(true)}>
                  Cancel…
                </Button>
              )}
              {editable && (
                <Button variant="ghost" onClick={() => onEdit(appointment)}>
                  {appointment.status === 'CANCELLED' ? 'Rebook / edit' : 'Reschedule / edit'}
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
