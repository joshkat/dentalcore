import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Spinner } from '../../components/Spinner';
import { formatDate } from '../../i18n/format';
import { usePatientAppointments } from '../appointments/api';
import type { Appointment, AppointmentStatus } from '../../types/api';

const statusTone: Record<AppointmentStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  SCHEDULED: 'blue',
  CONFIRMED: 'green',
  CHECKED_IN: 'yellow',
  IN_PROGRESS: 'yellow',
  COMPLETED: 'green',
  NO_SHOW: 'red',
  CANCELLED: 'gray',
};

function calendarLink(appointment: Appointment): string {
  const d = new Date(appointment.startsAt);
  const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
  return `/schedule?date=${date}&appointment=${appointment.id}`;
}

function AppointmentRow({ appointment }: { appointment: Appointment }) {
  const { t } = useTranslation('patients');
  const starts = new Date(appointment.startsAt);
  const ends = new Date(appointment.endsAt);
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
      <div className="flex items-center gap-3">
        <span
          className="h-2.5 w-2.5 shrink-0 rounded-full"
          style={{ backgroundColor: appointment.color }}
          aria-hidden="true"
        />
        <div>
          <p className="text-sm font-medium text-gray-900">
            {formatDate(starts, {
              weekday: 'short',
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
            {' · '}
            {formatDate(starts, { hour: 'numeric', minute: '2-digit' })}–
            {formatDate(ends, { hour: 'numeric', minute: '2-digit' })}
          </p>
          <p className="text-xs text-gray-500">
            {appointment.providerLastName}, {appointment.providerFirstName} ·{' '}
            {appointment.operatoryName}
            {appointment.notes ? ` · ${appointment.notes}` : ''}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-3">
        <Badge tone={statusTone[appointment.status]}>
          {t(`appts.status.${appointment.status}`)}
        </Badge>
        <Link
          to={calendarLink(appointment)}
          className="text-sm font-medium text-brand-600 hover:underline"
        >
          {t('appts.viewInCalendar')}
        </Link>
      </div>
    </li>
  );
}

export function AppointmentsTab({ patientId }: { patientId: string }) {
  const { t } = useTranslation('patients');
  const { data: appointments, isPending } = usePatientAppointments(patientId);

  if (isPending) return <Spinner label={t('appts.loading')} />;
  if (!appointments || appointments.length === 0) {
    return <p className="text-sm text-gray-500">{t('appts.none')}</p>;
  }

  const now = Date.now();
  const upcoming = appointments
    .filter((a) => new Date(a.startsAt).getTime() >= now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const past = appointments
    .filter((a) => new Date(a.startsAt).getTime() < now)
    .sort((a, b) => b.startsAt.localeCompare(a.startsAt));

  return (
    <div className="space-y-6">
      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('appts.upcoming')}
        </h3>
        {upcoming.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">{t('appts.noUpcoming')}</p>
        ) : (
          <ul className="mt-2 divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
            {upcoming.map((a) => (
              <AppointmentRow key={a.id} appointment={a} />
            ))}
          </ul>
        )}
      </section>
      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('appts.past')}
        </h3>
        {past.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">{t('appts.noPast')}</p>
        ) : (
          <ul className="mt-2 divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
            {past.map((a) => (
              <AppointmentRow key={a.id} appointment={a} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
