import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { api, ApiError } from '../../lib/api';
import type { Appointment, Provider, RecurrenceFrequency } from '../../types/api';
import { usePatients } from '../patients/api';
import { QuickPatientModal } from '../patients/QuickPatientModal';
import { useProcedureCodes } from '../procedures/api';
import { useProviders } from '../providers/api';
import {
  useCreateAppointment,
  useCreateRecurring,
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
  const { t } = useTranslation('schedule');
  return (
    <Modal
      title={props.appointment ? t('editAppointment') : t('newAppointment')}
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
  const { t } = useTranslation('schedule');
  const createAppointment = useCreateAppointment();
  const createRecurring = useCreateRecurring();
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
  const [asap, setAsap] = useState(appointment?.asap ?? false);
  const [repeat, setRepeat] = useState<'NONE' | RecurrenceFrequency>('NONE');
  const [occurrences, setOccurrences] = useState(4);
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
    if (!patientId) return setError(t('validation.selectPatient'));
    if (!providerId) return setError(t('validation.selectProvider'));
    if (!operatoryId) return setError(t('validation.selectOperatory'));
    if (!date || !time) return setError(t('validation.pickDateTime'));

    const startsAt = new Date(`${date}T${time}:00`);
    const endsAt = new Date(startsAt.getTime() + duration * 60000);
    const input: AppointmentInput = {
      patientId,
      providerId,
      operatoryId,
      startsAt: startsAt.toISOString(),
      endsAt: endsAt.toISOString(),
      asap,
      notes: notes || null,
    };

    setError(null);
    setSubmitting(true);
    try {
      // Recurring series (new appointments only): book the whole run at once.
      if (!appointment && repeat !== 'NONE') {
        const result = await createRecurring.mutateAsync({
          base: input,
          frequency: repeat,
          occurrences,
        });
        if (result.skipped.length > 0) {
          setError(
            t('recurringSummary', {
              created: result.created.length,
              skipped: result.skipped.length,
            }),
          );
          setSubmitting(false);
          return;
        }
        onClose();
        return;
      }

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
      setError(e instanceof ApiError ? e.message : t('validation.failedToSave'));
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
          {t('patient')}
        </label>
        <input
          id="appt-patient"
          type="search"
          value={patientSearch}
          onChange={(e) => {
            setPatientSearch(e.target.value);
            setPatientId('');
          }}
          placeholder={t('searchPatientsPlaceholder')}
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
              <li className="px-3 py-2 text-sm text-gray-500">{t('noMatchingPatients')}</li>
            )}
            <li className="border-t border-gray-100">
              <button
                type="button"
                onClick={() => setCreatingPatient(true)}
                className="block w-full px-3 py-2 text-left text-sm font-medium text-brand-600 hover:bg-brand-50"
              >
                {patientSearch
                  ? t('newPatientNamed', { name: patientSearch })
                  : t('newPatient')}
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
            {t('provider')}
          </label>
          <select
            id="appt-provider"
            value={providerId}
            onChange={(e) => setProviderId(e.target.value)}
            className={selectClass}
          >
            <option value="">{t('select')}</option>
            {providers?.content.map((p: Provider) => (
              <option key={p.id} value={p.id}>
                {p.lastName}, {p.firstName} ({p.type})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="appt-operatory" className="block text-sm font-medium text-gray-700">
            {t('operatory')}
          </label>
          <select
            id="appt-operatory"
            value={operatoryId}
            onChange={(e) => setOperatoryId(e.target.value)}
            className={selectClass}
          >
            <option value="">{t('select')}</option>
            {operatories?.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="appt-date" className="block text-sm font-medium text-gray-700">
            {t('date')}
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
              {t('start')}
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
              {t('duration')}
            </label>
            <select
              id="appt-duration"
              value={duration}
              onChange={(e) => setDuration(Number(e.target.value))}
              className={selectClass}
            >
              {DURATIONS.map((d) => (
                <option key={d} value={d}>
                  {t('durationMinutes', { count: d })}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div>
        <span className="block text-sm font-medium text-gray-700">{t('plannedProcedures')}</span>
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
                  aria-label={t('removeCode', { code: p.code })}
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
          aria-label={t('searchProcedures')}
          value={procedureSearch}
          onChange={(e) => setProcedureSearch(e.target.value)}
          placeholder={t('addProcedurePlaceholder')}
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

      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={asap}
          onChange={(e) => setAsap(e.target.checked)}
          className="h-4 w-4 rounded border-gray-300 text-brand-600 focus:ring-brand-600"
        />
        {t('asapWantsEarlier')}
      </label>

      {!appointment && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="appt-repeat" className="block text-sm font-medium text-gray-700">
              {t('repeat')}
            </label>
            <select
              id="appt-repeat"
              value={repeat}
              onChange={(e) => setRepeat(e.target.value as 'NONE' | RecurrenceFrequency)}
              className={selectClass}
            >
              <option value="NONE">{t('repeatNone')}</option>
              <option value="WEEKLY">{t('repeatWeekly')}</option>
              <option value="BIWEEKLY">{t('repeatBiweekly')}</option>
              <option value="MONTHLY">{t('repeatMonthly')}</option>
            </select>
          </div>
          {repeat !== 'NONE' && (
            <div>
              <label htmlFor="appt-occurrences" className="block text-sm font-medium text-gray-700">
                {t('occurrences')}
              </label>
              <input
                id="appt-occurrences"
                type="number"
                min={2}
                max={52}
                value={occurrences}
                onChange={(e) => setOccurrences(Number(e.target.value))}
                className={selectClass}
              />
            </div>
          )}
        </div>
      )}

      <div>
        <label htmlFor="appt-notes" className="block text-sm font-medium text-gray-700">
          {t('notes')}
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
          {t('common:cancel')}
        </Button>
        <Button onClick={submit} loading={submitting}>
          {appointment ? t('saveChanges') : t('bookAppointment')}
        </Button>
      </div>
    </div>
  );
}
