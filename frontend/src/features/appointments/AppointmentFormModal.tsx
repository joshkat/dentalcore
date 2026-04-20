import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { api, ApiError } from '../../lib/api';
import type { Appointment, Provider } from '../../types/api';
import { usePatients } from '../patients/api';
import { QuickPatientModal } from '../patients/QuickPatientModal';
import { useProcedureCodes } from '../procedures/api';
import { useProviders } from '../providers/api';
import {
  useCreateAppointment,
  useOperatories,
  useUpdateAppointment,
  type AppointmentInput,
} from './api';

interface SelectedProcedure {
  procedureCodeId: string;
  code: string;
  description: string;
}

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

const DURATIONS = [15, 30, 45, 60, 90, 120];

interface AppointmentFormModalProps {
  open: boolean;
  onClose: () => void;
  appointment: Appointment | null;
  defaultDate: string; // yyyy-MM-dd
}

export function AppointmentFormModal(props: AppointmentFormModalProps) {
  return (
    <Modal
      title={props.appointment ? 'Edit appointment' : 'New appointment'}
      open={props.open}
      onClose={props.onClose}
    >
      {props.open && <AppointmentForm key={props.appointment?.id ?? 'new'} {...props} />}
    </Modal>
  );
}

function toLocalTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function toLocalDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

function AppointmentForm({ onClose, appointment, defaultDate }: AppointmentFormModalProps) {
  const createAppointment = useCreateAppointment();
  const updateAppointment = useUpdateAppointment(appointment?.id ?? '');
  const { data: providers } = useProviders(false);
  const { data: operatories } = useOperatories();

  const [patientSearch, setPatientSearch] = useState(
    appointment ? `${appointment.patientLastName}, ${appointment.patientFirstName}` : '',
  );
  const [patientId, setPatientId] = useState(appointment?.patientId ?? '');
  const [creatingPatient, setCreatingPatient] = useState(false);
  const { data: candidates } = usePatients(patientSearch, 0);

  const [providerId, setProviderId] = useState(appointment?.providerId ?? '');
  const [operatoryId, setOperatoryId] = useState(appointment?.operatoryId ?? '');
  const [date, setDate] = useState(
    appointment ? toLocalDate(appointment.startsAt) : defaultDate,
  );
  const [time, setTime] = useState(appointment ? toLocalTime(appointment.startsAt) : '09:00');
  const [duration, setDuration] = useState(() => {
    if (!appointment) return 60;
    return Math.round(
      (new Date(appointment.endsAt).getTime() - new Date(appointment.startsAt).getTime()) / 60000,
    );
  });
  const [notes, setNotes] = useState(appointment?.notes ?? '');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const queryClient = useQueryClient();
  const [procedureSearch, setProcedureSearch] = useState('');
  const [selectedProcedures, setSelectedProcedures] = useState<SelectedProcedure[]>(
    appointment?.procedures.map((p) => ({
      procedureCodeId: p.procedureCodeId,
      code: p.code ?? '',
      description: p.description ?? '',
    })) ?? [],
  );
  const { data: procedureCatalog } = useProcedureCodes(procedureSearch);

  const submit = async () => {
    if (!patientId) return setError('Select a patient');
    if (!providerId) return setError('Select a provider');
    if (!operatoryId) return setError('Select an operatory');
    if (!date || !time) return setError('Pick a date and time');

    const startsAt = new Date(`${date}T${time}:00`);
    const endsAt = new Date(startsAt.getTime() + duration * 60000);
    const input: AppointmentInput = {
      patientId,
      providerId,
      operatoryId,
      startsAt: startsAt.toISOString(),
      endsAt: endsAt.toISOString(),
      notes: notes || null,
    };

    setError(null);
    setSubmitting(true);
    try {
      let appointmentId: string;
      if (appointment) {
        const updated = await updateAppointment.mutateAsync(input);
        appointmentId = updated.id;
      } else {
        const created = await createAppointment.mutateAsync(input);
        appointmentId = created.id;
      }
      if (appointment || selectedProcedures.length > 0) {
        await api<Appointment>(`/api/v1/appointments/${appointmentId}/procedures`, {
          method: 'PUT',
          body: { procedureCodeIds: selectedProcedures.map((p) => p.procedureCodeId) },
        });
        await queryClient.invalidateQueries({ queryKey: ['appointments'] });
      }
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to save appointment');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      {error && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div>
        <label htmlFor="appt-patient" className="block text-sm font-medium text-gray-700">
          Patient
        </label>
        <input
          id="appt-patient"
          type="search"
          value={patientSearch}
          onChange={(e) => {
            setPatientSearch(e.target.value);
            setPatientId('');
          }}
          placeholder="Search patients…"
          className={selectClass}
        />
        {patientSearch && !patientId && (
          <ul className="mt-1 max-h-48 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
            {candidates?.content.slice(0, 8).map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => {
                    setPatientId(p.id);
                    setPatientSearch(`${p.lastName}, ${p.firstName}`);
                  }}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                >
                  {p.lastName}, {p.firstName} ({p.dateOfBirth})
                </button>
              </li>
            ))}
            {candidates?.content.length === 0 && (
              <li className="px-3 py-2 text-sm text-gray-500">No matching patients</li>
            )}
            <li className="border-t border-gray-100">
              <button
                type="button"
                onClick={() => setCreatingPatient(true)}
                className="block w-full px-3 py-2 text-left text-sm font-medium text-brand-600 hover:bg-brand-50"
              >
                + New patient{patientSearch ? ` “${patientSearch}”` : ''}
              </button>
            </li>
          </ul>
        )}
      </div>

      <QuickPatientModal
        open={creatingPatient}
        onClose={() => setCreatingPatient(false)}
        initialName={patientSearch}
        onCreated={(patient) => {
          setPatientId(patient.id);
          setPatientSearch(`${patient.lastName}, ${patient.firstName}`);
        }}
      />

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="appt-provider" className="block text-sm font-medium text-gray-700">
            Provider
          </label>
          <select
            id="appt-provider"
            value={providerId}
            onChange={(e) => setProviderId(e.target.value)}
            className={selectClass}
          >
            <option value="">Select…</option>
            {providers?.content.map((p: Provider) => (
              <option key={p.id} value={p.id}>
                {p.lastName}, {p.firstName} ({p.type})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="appt-operatory" className="block text-sm font-medium text-gray-700">
            Operatory
          </label>
          <select
            id="appt-operatory"
            value={operatoryId}
            onChange={(e) => setOperatoryId(e.target.value)}
            className={selectClass}
          >
            <option value="">Select…</option>
            {operatories?.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="appt-date" className="block text-sm font-medium text-gray-700">
            Date
          </label>
          <input
            id="appt-date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className={selectClass}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label htmlFor="appt-time" className="block text-sm font-medium text-gray-700">
              Start
            </label>
            <input
              id="appt-time"
              type="time"
              step={300}
              value={time}
              onChange={(e) => setTime(e.target.value)}
              className={selectClass}
            />
          </div>
          <div>
            <label htmlFor="appt-duration" className="block text-sm font-medium text-gray-700">
              Duration
            </label>
            <select
              id="appt-duration"
              value={duration}
              onChange={(e) => setDuration(Number(e.target.value))}
              className={selectClass}
            >
              {DURATIONS.map((d) => (
                <option key={d} value={d}>
                  {d} min
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div>
        <span className="block text-sm font-medium text-gray-700">Planned procedures</span>
        {selectedProcedures.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {selectedProcedures.map((p) => (
              <span
                key={p.procedureCodeId}
                className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2 py-1 text-xs font-medium text-brand-700"
              >
                <span className="font-mono">{p.code}</span>
                <button
                  type="button"
                  aria-label={`Remove ${p.code}`}
                  onClick={() =>
                    setSelectedProcedures((current) =>
                      current.filter((s) => s.procedureCodeId !== p.procedureCodeId),
                    )
                  }
                  className="text-brand-500 hover:text-brand-800"
                >
                  ✕
                </button>
              </span>
            ))}
          </div>
        )}
        <input
          type="search"
          aria-label="Search procedures"
          value={procedureSearch}
          onChange={(e) => setProcedureSearch(e.target.value)}
          placeholder="Add procedure (e.g. D1110, crown)…"
          className={selectClass}
        />
        {procedureSearch && (
          <ul className="mt-1 max-h-32 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
            {procedureCatalog?.content
              .filter((c) => !selectedProcedures.some((s) => s.procedureCodeId === c.id))
              .slice(0, 6)
              .map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedProcedures((current) => [
                        ...current,
                        { procedureCodeId: c.id, code: c.code, description: c.description },
                      ]);
                      setProcedureSearch('');
                    }}
                    className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                  >
                    <span className="font-mono">{c.code}</span> — {c.description}
                  </button>
                </li>
              ))}
          </ul>
        )}
      </div>

      <div>
        <label htmlFor="appt-notes" className="block text-sm font-medium text-gray-700">
          Notes
        </label>
        <textarea
          id="appt-notes"
          rows={2}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          className={selectClass}
        />
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={submit} loading={submitting}>
          {appointment ? 'Save changes' : 'Book appointment'}
        </Button>
      </div>
    </div>
  );
}
