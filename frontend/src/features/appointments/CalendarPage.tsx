import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import type { Appointment } from '../../types/api';
import { CheckoutModal } from '../checkout/CheckoutModal';
import { useAppointments } from './api';
import { AppointmentDetailModal } from './AppointmentDetailModal';
import { AppointmentFormModal } from './AppointmentFormModal';
import { OperatoriesModal } from './OperatoriesModal';

const DAY_START_HOUR = 7;
const DAY_END_HOUR = 19;
const HOUR_PX = 64;
const DAYS_SHOWN = 6; // Mon–Sat

function startOfWeek(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  const day = d.getDay(); // 0 = Sun
  d.setDate(d.getDate() - (day === 0 ? 6 : day - 1));
  return d;
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function toDateInput(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

export function CalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [weekStart, setWeekStart] = useState(() => {
    const dateParam = searchParams.get('date');
    return startOfWeek(dateParam ? new Date(`${dateParam}T00:00:00`) : new Date());
  });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Appointment | null>(null);
  const [selected, setSelected] = useState<Appointment | null>(null);
  const [checkingOut, setCheckingOut] = useState<Appointment | null>(null);
  const [managingOps, setManagingOps] = useState(false);
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK');
  const isAdmin = hasRole('ADMIN');

  const weekEnd = addDays(weekStart, DAYS_SHOWN);
  const { data: appointments, isPending } = useAppointments(
    weekStart.toISOString(),
    weekEnd.toISOString(),
  );

  // Deep link: /schedule?date=YYYY-MM-DD&appointment=<id> opens that appointment.
  const appointmentParam = searchParams.get('appointment');
  useEffect(() => {
    if (!appointmentParam || !appointments) return;
    const target = appointments.find((a) => a.id === appointmentParam);
    if (target) {
      setSelected(target);
      setSearchParams({}, { replace: true });
    }
  }, [appointmentParam, appointments, setSearchParams]);

  // keep the selected appointment fresh after status mutations refetch the list
  const selectedFresh = useMemo(
    () => appointments?.find((a) => a.id === selected?.id) ?? selected,
    [appointments, selected],
  );
  const checkingOutFresh = useMemo(
    () => appointments?.find((a) => a.id === checkingOut?.id) ?? checkingOut,
    [appointments, checkingOut],
  );

  const days = Array.from({ length: DAYS_SHOWN }, (_, i) => addDays(weekStart, i));
  const hours = Array.from(
    { length: DAY_END_HOUR - DAY_START_HOUR },
    (_, i) => DAY_START_HOUR + i,
  );

  const byDay = useMemo(() => {
    const map = new Map<string, Appointment[]>();
    days.forEach((day) => map.set(toDateInput(day), []));
    appointments?.forEach((appointment) => {
      const key = toDateInput(new Date(appointment.startsAt));
      map.get(key)?.push(appointment);
    });
    return map;
  }, [appointments, weekStart]); // eslint-disable-line react-hooks/exhaustive-deps

  const monthLabel = weekStart.toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  });

  return (
    <div className="flex h-full flex-col p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-4">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Schedule</h1>
          <span className="text-sm text-gray-500">{monthLabel}</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            ← Prev
          </Button>
          <Button variant="secondary" onClick={() => setWeekStart(startOfWeek(new Date()))}>
            Today
          </Button>
          <Button variant="secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Next →
          </Button>
          {isAdmin && (
            <Button variant="ghost" onClick={() => setManagingOps(true)}>
              Operatories
            </Button>
          )}
          {canWrite && <Button onClick={() => setCreating(true)}>New appointment</Button>}
        </div>
      </div>

      {isPending ? (
        <Spinner label="Loading schedule…" />
      ) : (
        <div className="flex-1 overflow-auto rounded-lg bg-white shadow">
          <div
            className="grid min-w-[900px]"
            style={{ gridTemplateColumns: `4rem repeat(${DAYS_SHOWN}, minmax(0, 1fr))` }}
          >
            {/* header row */}
            <div className="sticky top-0 z-10 border-b border-gray-200 bg-white" />
            {days.map((day) => {
              const isToday = toDateInput(day) === toDateInput(new Date());
              return (
                <div
                  key={day.toISOString()}
                  className={`sticky top-0 z-10 border-b border-l border-gray-200 bg-white px-2 py-2 text-center text-sm font-medium ${
                    isToday ? 'text-brand-700' : 'text-gray-700'
                  }`}
                >
                  {day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })}
                </div>
              );
            })}

            {/* time gutter */}
            <div className="relative" style={{ height: hours.length * HOUR_PX }}>
              {hours.map((hour, i) => (
                <div
                  key={hour}
                  className="absolute right-2 -translate-y-2 text-xs text-gray-400"
                  style={{ top: i * HOUR_PX }}
                >
                  {hour}:00
                </div>
              ))}
            </div>

            {/* day columns */}
            {days.map((day) => (
              <div
                key={day.toISOString()}
                className="relative border-l border-gray-100"
                style={{ height: hours.length * HOUR_PX }}
              >
                {hours.map((hour, i) => (
                  <div
                    key={hour}
                    className="absolute inset-x-0 border-t border-gray-100"
                    style={{ top: i * HOUR_PX }}
                  />
                ))}
                {byDay.get(toDateInput(day))?.map((appointment) => {
                  const starts = new Date(appointment.startsAt);
                  const ends = new Date(appointment.endsAt);
                  const top =
                    ((starts.getHours() + starts.getMinutes() / 60 - DAY_START_HOUR) * HOUR_PX);
                  const height = Math.max(
                    ((ends.getTime() - starts.getTime()) / 3_600_000) * HOUR_PX,
                    20,
                  );
                  const inactive =
                    appointment.status === 'CANCELLED' || appointment.status === 'NO_SHOW';
                  return (
                    <button
                      key={appointment.id}
                      onClick={() => setSelected(appointment)}
                      className={`absolute inset-x-1 overflow-hidden rounded-md px-2 py-1 text-left text-xs text-white shadow transition-opacity hover:opacity-90 ${
                        inactive ? 'opacity-40 line-through' : ''
                      }`}
                      style={{ top, height, backgroundColor: appointment.color }}
                      title={`${appointment.patientLastName}, ${appointment.patientFirstName}`}
                    >
                      <span className="block truncate font-semibold">
                        {appointment.patientLastName}, {appointment.patientFirstName}
                      </span>
                      <span className="block truncate opacity-80">
                        {starts.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} ·{' '}
                        {appointment.operatoryName}
                      </span>
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </div>
      )}

      <AppointmentFormModal
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        appointment={editing}
        defaultDate={toDateInput(weekStart)}
      />
      <AppointmentDetailModal
        appointment={selectedFresh}
        onClose={() => setSelected(null)}
        canWrite={canWrite}
        onEdit={(appointment) => {
          setSelected(null);
          setEditing(appointment);
        }}
        onCheckout={(appointment) => {
          setSelected(null);
          setCheckingOut(appointment);
        }}
      />
      <CheckoutModal appointment={checkingOutFresh} onClose={() => setCheckingOut(null)} />
      <OperatoriesModal open={managingOps} onClose={() => setManagingOps(false)} />
    </div>
  );
}
