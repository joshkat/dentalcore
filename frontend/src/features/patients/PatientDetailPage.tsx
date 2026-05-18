import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import type { Patient, PatientStatus } from '../../types/api';
import { usePatient, useUpdatePatient, useUpdatePatientStatus } from './api';
import { useBalance } from '../billing/api';
import { LedgerTab } from '../billing/LedgerTab';
import { ClinicalNotesTab } from '../clinical-notes/ClinicalNotesTab';
import { DocumentsTab } from '../documents/DocumentsTab';
import { PatientFormsTab } from '../forms/PatientFormsTab';
import { InsuranceTab } from '../insurance/InsuranceTab';
import { TreatmentPlansTab } from '../treatment-plans/TreatmentPlansTab';
import { AlertsTab } from './AlertsTab';
import { AppointmentsTab } from './AppointmentsTab';
import { ChartTab } from './ChartTab';
import { useAlerts, useUpdateRecall } from './api';
import { FamilyTab } from './FamilyTab';
import { PatientForm } from './PatientForm';
import { PerioTab } from './PerioTab';
import { TimelineTab } from './TimelineTab';
import type { PatientFormValues } from './schemas';

const TABS = [
  'Demographics',
  'Chart',
  'Perio',
  'Appointments',
  'Treatment Plans',
  'Notes',
  'Insurance',
  'Ledger',
  'Documents',
  'Forms',
  'Alerts',
  'Family',
  'Timeline',
] as const;
type Tab = (typeof TABS)[number];

const statusTone = { ACTIVE: 'green', INACTIVE: 'yellow', ARCHIVED: 'gray' } as const;

function toFormValues(patient: Patient): PatientFormValues {
  return {
    firstName: patient.firstName,
    middleName: patient.middleName ?? '',
    lastName: patient.lastName,
    dateOfBirth: patient.dateOfBirth,
    sex: patient.sex,
    email: patient.email ?? '',
    addressLine1: patient.addressLine1 ?? '',
    addressLine2: patient.addressLine2 ?? '',
    city: patient.city ?? '',
    state: patient.state ?? '',
    postalCode: patient.postalCode ?? '',
    preferredLanguage: patient.preferredLanguage ?? '',
    emergencyContactName: patient.emergencyContactName ?? '',
    emergencyContactPhone: patient.emergencyContactPhone ?? '',
    emergencyContactRelationship: patient.emergencyContactRelationship ?? '',
    notes: patient.notes ?? '',
    phones: patient.phones.map((p) => ({ ...p })),
    preferredName: patient.preferredName ?? '',
    pronouns: patient.pronouns ?? '',
    employer: patient.employer ?? '',
    occupation: patient.occupation ?? '',
    referralSource: patient.referralSource ?? '',
    preferredContactMethod: patient.preferredContactMethod ?? '',
    smsConsent: patient.smsConsent,
    emailConsent: patient.emailConsent,
    pharmacyName: patient.pharmacyName ?? '',
    pharmacyPhone: patient.pharmacyPhone ?? '',
    primaryProviderId: patient.primaryProviderId ?? '',
    smokingStatus: patient.smokingStatus,
  };
}

export function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [tab, setTab] = useState<Tab>('Demographics');
  const [editing, setEditing] = useState(false);
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK');
  const canWriteClinical = hasRole('ADMIN', 'DENTIST', 'HYGIENIST');
  // mirror the backend read gates so restricted roles don't trigger 403s
  const canViewLedger = hasRole('ADMIN', 'BILLING', 'FRONT_DESK', 'DENTIST');
  const canViewNotes = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'READ_ONLY');
  const tabs = TABS.filter(
    (t) => (t !== 'Ledger' || canViewLedger) && (t !== 'Notes' || canViewNotes),
  );

  const { data: patient, isPending, isError } = usePatient(id!);
  const { data: alerts } = useAlerts(id!);
  const { data: balanceData } = useBalance(id!, canViewLedger);
  const updatePatient = useUpdatePatient(id!);
  const updateStatus = useUpdatePatientStatus(id!);
  const updateRecall = useUpdateRecall(id!);

  if (isPending) return <Spinner label="Loading patient…" />;
  if (isError || !patient) {
    return <p className="p-8 text-sm text-red-600">Patient not found.</p>;
  }

  const age = Math.floor(
    (Date.now() - new Date(patient.dateOfBirth).getTime()) / (365.25 * 24 * 3600 * 1000),
  );
  const highAllergies = (alerts ?? []).filter(
    (a) => a.type === 'ALLERGY' && a.severity === 'HIGH' && a.active,
  );
  const recallOverdue =
    patient.nextRecallDate != null && patient.nextRecallDate <= new Date().toISOString().slice(0, 10);

  return (
    <div className="p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {patient.lastName}, {patient.firstName}
            {patient.preferredName && (
              <span className="ml-2 text-base font-normal text-gray-500">
                “{patient.preferredName}”
              </span>
            )}
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            DOB {patient.dateOfBirth} (age {age}) · {patient.sex}
            {patient.pronouns && ` · ${patient.pronouns}`}
            {patient.primaryProviderLastName &&
              ` · Dr. ${patient.primaryProviderLastName}`}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {highAllergies.length > 0 && (
              <Badge tone="red">
                ⚠ ALLERGY: {highAllergies.map((a) => a.description).join(', ')}
              </Badge>
            )}
            {patient.nextRecallDate && (
              <Badge tone={recallOverdue ? 'red' : 'blue'}>
                {recallOverdue ? 'RECALL OVERDUE' : 'Recall'} {patient.nextRecallDate}
              </Badge>
            )}
            {balanceData && balanceData.balance !== 0 && (
              <Badge tone={balanceData.balance > 0 ? 'yellow' : 'green'}>
                {balanceData.balance > 0
                  ? `BALANCE $${balanceData.balance.toFixed(2)}`
                  : `CREDIT $${Math.abs(balanceData.balance).toFixed(2)}`}
              </Badge>
            )}
            {canWrite && (
              <button
                onClick={() => {
                  const base = new Date();
                  base.setMonth(base.getMonth() + patient.recallIntervalMonths);
                  updateRecall.mutate({
                    intervalMonths: patient.recallIntervalMonths,
                    nextRecallDate: base.toISOString().slice(0, 10),
                  });
                }}
                className="text-xs text-brand-600 hover:underline"
              >
                Set recall +{patient.recallIntervalMonths}mo
              </button>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Badge tone={statusTone[patient.status]}>{patient.status}</Badge>
          {canWrite && (
            <select
              aria-label="Change status"
              value={patient.status}
              onChange={(e) => updateStatus.mutate(e.target.value as PatientStatus)}
              className="rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          )}
        </div>
      </div>

      <nav className="mt-6 flex gap-1 border-b border-gray-200" aria-label="Patient sections">
        {tabs.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium ${
              tab === t
                ? 'border-b-2 border-brand-600 text-brand-700'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t}
          </button>
        ))}
      </nav>

      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        {tab === 'Demographics' &&
          (editing && canWrite ? (
            <PatientForm
              defaultValues={toFormValues(patient)}
              submitLabel="Save changes"
              onCancel={() => setEditing(false)}
              onSubmit={async (values) => {
                await updatePatient.mutateAsync(values);
                setEditing(false);
              }}
            />
          ) : (
            <DemographicsView patient={patient} canWrite={canWrite} onEdit={() => setEditing(true)} />
          ))}
        {tab === 'Chart' && <ChartTab patientId={patient.id} canChart={canWriteClinical} />}
        {tab === 'Perio' && <PerioTab patientId={patient.id} canChart={canWriteClinical} />}
        {tab === 'Appointments' && <AppointmentsTab patientId={patient.id} />}
        {tab === 'Treatment Plans' && (
          <TreatmentPlansTab patientId={patient.id} canWrite={canWriteClinical} />
        )}
        {tab === 'Notes' && canViewNotes && (
          <ClinicalNotesTab patientId={patient.id} canWriteClinical={canWriteClinical} />
        )}
        {tab === 'Insurance' && (
          <InsuranceTab
            patientId={patient.id}
            canWrite={hasRole('ADMIN', 'FRONT_DESK', 'BILLING')}
          />
        )}
        {tab === 'Ledger' && canViewLedger && <LedgerTab patientId={patient.id} />}
        {tab === 'Documents' && <DocumentsTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'Forms' && <PatientFormsTab patientId={patient.id} />}
        {tab === 'Alerts' && <AlertsTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'Family' && <FamilyTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'Timeline' && <TimelineTab patientId={patient.id} />}
      </div>
    </div>
  );
}

function DemographicsView({
  patient,
  canWrite,
  onEdit,
}: {
  patient: Patient;
  canWrite: boolean;
  onEdit: () => void;
}) {
  const rows: Array<[string, string | null]> = [
    ['Email', patient.email],
    [
      'Phones',
      patient.phones.length
        ? patient.phones
            .map((p) => `${p.number} (${p.type}${p.primary ? ', primary' : ''})`)
            .join(' · ')
        : null,
    ],
    [
      'Address',
      [patient.addressLine1, patient.addressLine2, patient.city, patient.state, patient.postalCode]
        .filter(Boolean)
        .join(', ') || null,
    ],
    ['Preferred language', patient.preferredLanguage],
    [
      'Emergency contact',
      patient.emergencyContactName
        ? `${patient.emergencyContactName} (${patient.emergencyContactRelationship ?? 'contact'}) ${
            patient.emergencyContactPhone ?? ''
          }`
        : null,
    ],
    ['Notes', patient.notes],
  ];

  return (
    <div>
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</dt>
            <dd className="mt-1 text-sm text-gray-900">{value ?? '—'}</dd>
          </div>
        ))}
      </dl>
      {canWrite && (
        <button onClick={onEdit} className="mt-6 text-sm font-medium text-brand-600 hover:underline">
          Edit demographics
        </button>
      )}
    </div>
  );
}
