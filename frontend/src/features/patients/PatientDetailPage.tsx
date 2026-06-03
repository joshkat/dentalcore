import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Spinner } from '../../components/Spinner';
import { formatMoney } from '../../i18n/format';
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

// Stable tab keys; labels come from the patients:tabs.* catalog entries.
const TABS = [
  'demographics',
  'chart',
  'perio',
  'appointments',
  'treatmentPlans',
  'notes',
  'insurance',
  'ledger',
  'documents',
  'forms',
  'alerts',
  'family',
  'timeline',
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
  const { t } = useTranslation('patients');
  const { id } = useParams<{ id: string }>();
  const [tab, setTab] = useState<Tab>('demographics');
  const [editing, setEditing] = useState(false);
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK');
  const canWriteClinical = hasRole('ADMIN', 'DENTIST', 'HYGIENIST');
  // mirror the backend read gates so restricted roles don't trigger 403s
  const canViewLedger = hasRole('ADMIN', 'BILLING', 'FRONT_DESK', 'DENTIST');
  const canViewNotes = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'READ_ONLY');
  const tabs = TABS.filter(
    (tabKey) => (tabKey !== 'ledger' || canViewLedger) && (tabKey !== 'notes' || canViewNotes),
  );

  const { data: patient, isPending, isError } = usePatient(id!);
  const { data: alerts } = useAlerts(id!);
  const { data: balanceData } = useBalance(id!, canViewLedger);
  const updatePatient = useUpdatePatient(id!);
  const updateStatus = useUpdatePatientStatus(id!);
  const updateRecall = useUpdateRecall(id!);

  if (isPending) return <Spinner label={t('loadingPatient')} />;
  if (isError || !patient) {
    return <p className="p-8 text-sm text-red-600">{t('notFound')}</p>;
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
            {t('dobLine', { date: patient.dateOfBirth, age })} · {t(`sexBadge.${patient.sex}`)}
            {patient.pronouns && ` · ${patient.pronouns}`}
            {patient.primaryProviderLastName &&
              ` · ${t('doctorName', { name: patient.primaryProviderLastName })}`}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {highAllergies.length > 0 && (
              <Badge tone="red">
                {t('allergyBadge', { list: highAllergies.map((a) => a.description).join(', ') })}
              </Badge>
            )}
            {patient.nextRecallDate && (
              <Badge tone={recallOverdue ? 'red' : 'blue'}>
                {recallOverdue
                  ? t('recallOverdueBadge', { date: patient.nextRecallDate })
                  : t('recallBadge', { date: patient.nextRecallDate })}
              </Badge>
            )}
            {balanceData && balanceData.balance !== 0 && (
              <Badge tone={balanceData.balance > 0 ? 'yellow' : 'green'}>
                {balanceData.balance > 0
                  ? t('balanceBadge', { amount: formatMoney(balanceData.balance) })
                  : t('creditBadge', { amount: formatMoney(Math.abs(balanceData.balance)) })}
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
                {t('setRecall', { months: patient.recallIntervalMonths })}
              </button>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Badge tone={statusTone[patient.status]}>{t(`statusBadge.${patient.status}`)}</Badge>
          {canWrite && (
            <select
              aria-label={t('changeStatusAria')}
              value={patient.status}
              onChange={(e) => updateStatus.mutate(e.target.value as PatientStatus)}
              className="rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="ACTIVE">{t('statusOption.ACTIVE')}</option>
              <option value="INACTIVE">{t('statusOption.INACTIVE')}</option>
              <option value="ARCHIVED">{t('statusOption.ARCHIVED')}</option>
            </select>
          )}
        </div>
      </div>

      <nav className="mt-6 flex gap-1 border-b border-gray-200" aria-label={t('sectionsAria')}>
        {tabs.map((tabKey) => (
          <button
            key={tabKey}
            onClick={() => setTab(tabKey)}
            className={`px-4 py-2 text-sm font-medium ${
              tab === tabKey
                ? 'border-b-2 border-brand-600 text-brand-700'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t(`tabs.${tabKey}`)}
          </button>
        ))}
      </nav>

      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        {tab === 'demographics' &&
          (editing && canWrite ? (
            <PatientForm
              defaultValues={toFormValues(patient)}
              submitLabel={t('saveChanges')}
              onCancel={() => setEditing(false)}
              onSubmit={async (values) => {
                await updatePatient.mutateAsync(values);
                setEditing(false);
              }}
            />
          ) : (
            <DemographicsView patient={patient} canWrite={canWrite} onEdit={() => setEditing(true)} />
          ))}
        {tab === 'chart' && <ChartTab patientId={patient.id} canChart={canWriteClinical} />}
        {tab === 'perio' && <PerioTab patientId={patient.id} canChart={canWriteClinical} />}
        {tab === 'appointments' && <AppointmentsTab patientId={patient.id} />}
        {tab === 'treatmentPlans' && (
          <TreatmentPlansTab patientId={patient.id} canWrite={canWriteClinical} />
        )}
        {tab === 'notes' && canViewNotes && (
          <ClinicalNotesTab patientId={patient.id} canWriteClinical={canWriteClinical} />
        )}
        {tab === 'insurance' && (
          <InsuranceTab
            patientId={patient.id}
            canWrite={hasRole('ADMIN', 'FRONT_DESK', 'BILLING')}
          />
        )}
        {tab === 'ledger' && canViewLedger && <LedgerTab patientId={patient.id} />}
        {tab === 'documents' && <DocumentsTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'forms' && <PatientFormsTab patientId={patient.id} />}
        {tab === 'alerts' && <AlertsTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'family' && <FamilyTab patientId={patient.id} canWrite={canWrite} />}
        {tab === 'timeline' && <TimelineTab patientId={patient.id} />}
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
  const { t } = useTranslation('patients');
  const rows: Array<[string, string | null]> = [
    [t('demo.email'), patient.email],
    [
      t('demo.phones'),
      patient.phones.length
        ? patient.phones
            .map((p) =>
              t(p.primary ? 'demo.phoneDisplayPrimary' : 'demo.phoneDisplay', {
                number: p.number,
                type: t(`phoneTypeBadge.${p.type}`),
              }),
            )
            .join(' · ')
        : null,
    ],
    [
      t('demo.address'),
      [patient.addressLine1, patient.addressLine2, patient.city, patient.state, patient.postalCode]
        .filter(Boolean)
        .join(', ') || null,
    ],
    [t('demo.preferredLanguage'), patient.preferredLanguage],
    [
      t('demo.emergencyContact'),
      patient.emergencyContactName
        ? t('demo.emergencyContactDisplay', {
            name: patient.emergencyContactName,
            relationship: patient.emergencyContactRelationship ?? t('demo.contactFallback'),
            phone: patient.emergencyContactPhone ?? '',
          })
        : null,
    ],
    [t('demo.notes'), patient.notes],
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
          {t('demo.edit')}
        </button>
      )}
    </div>
  );
}
