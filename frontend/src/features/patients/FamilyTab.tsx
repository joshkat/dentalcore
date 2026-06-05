import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  useCreateFamilyLink,
  useDeleteFamilyLink,
  useFamily,
  usePatient,
  usePatients,
  useSetGuarantor,
} from './api';

export function FamilyTab({ patientId, canWrite }: { patientId: string; canWrite: boolean }) {
  const { t } = useTranslation('patients');
  const { data: links, isPending } = useFamily(patientId);
  const createLink = useCreateFamilyLink(patientId);
  const deleteLink = useDeleteFamilyLink(patientId);

  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [relationship, setRelationship] = useState('SPOUSE');
  const [error, setError] = useState<string | null>(null);
  const { data: candidates } = usePatients(search, 0);

  const add = async () => {
    if (!selectedId) {
      setError(t('family.selectRequired'));
      return;
    }
    setError(null);
    try {
      await createLink.mutateAsync({ relatedPatientId: selectedId, relationship });
      setSearch('');
      setSelectedId('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('family.linkFailed'));
    }
  };

  if (isPending) return <Spinner label={t('family.loading')} />;

  return (
    <div className="space-y-4">
      <GuarantorSection patientId={patientId} />
      {canWrite && (
        <div className="rounded-md bg-gray-50 p-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-64 flex-1">
              <label htmlFor="family-search" className="block text-sm font-medium text-gray-700">
                {t('family.findPatient')}
              </label>
              <input
                id="family-search"
                type="search"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setSelectedId('');
                }}
                placeholder={t('family.searchByName')}
                className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
              {search && !selectedId && (
                <ul className="mt-1 max-h-40 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
                  {candidates?.content
                    .filter((p) => p.id !== patientId)
                    .slice(0, 8)
                    .map((p) => (
                      <li key={p.id}>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedId(p.id);
                            setSearch(`${p.lastName}, ${p.firstName}`);
                          }}
                          className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                        >
                          {p.lastName}, {p.firstName} ({p.dateOfBirth})
                        </button>
                      </li>
                    ))}
                </ul>
              )}
            </div>
            <div>
              <label htmlFor="family-rel" className="block text-sm font-medium text-gray-700">
                {t('family.relationship')}
              </label>
              <select
                id="family-rel"
                value={relationship}
                onChange={(e) => setRelationship(e.target.value)}
                className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              >
                <option value="SPOUSE">{t('family.relationshipOption.SPOUSE')}</option>
                <option value="CHILD">{t('family.relationshipOption.CHILD')}</option>
                <option value="PARENT">{t('family.relationshipOption.PARENT')}</option>
                <option value="SIBLING">{t('family.relationshipOption.SIBLING')}</option>
                <option value="GUARANTOR">{t('family.relationshipOption.GUARANTOR')}</option>
                <option value="OTHER">{t('family.relationshipOption.OTHER')}</option>
              </select>
            </div>
            <Button onClick={add} loading={createLink.isPending}>
              {t('family.link')}
            </Button>
          </div>
          {error && (
            <p role="alert" className="mt-2 text-sm text-red-600">
              {error}
            </p>
          )}
        </div>
      )}

      {links && links.length === 0 ? (
        <p className="text-sm text-gray-500">{t('family.none')}</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-md bg-white">
          {links?.map((link) => (
            <li key={link.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div>
                <Link
                  to={`/patients/${link.relatedPatientId}`}
                  className="text-sm font-medium text-brand-700 hover:underline"
                >
                  {link.relatedPatientLastName}, {link.relatedPatientFirstName}
                </Link>
                <p className="text-xs text-gray-500">
                  {t(`family.relationshipBadge.${link.relationship}`)}
                </p>
              </div>
              {canWrite && (
                <Button
                  variant="ghost"
                  onClick={() => deleteLink.mutate(link.id)}
                  disabled={deleteLink.isPending}
                >
                  {t('family.unlink')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * Billing guarantor — who receives the family's statements. Separate from the
 * descriptive family links above; write access mirrors the backend gate.
 */
function GuarantorSection({ patientId }: { patientId: string }) {
  const { t } = useTranslation('patients');
  const { data: patient } = usePatient(patientId);
  const setGuarantor = useSetGuarantor(patientId);
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'FRONT_DESK', 'BILLING');

  const [editing, setEditing] = useState(false);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data: candidates } = usePatients(search, 0);

  if (!patient) return null;

  const choose = async (guarantorPatientId: string | null) => {
    setError(null);
    try {
      await setGuarantor.mutateAsync(guarantorPatientId);
      setEditing(false);
      setSearch('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('family.updateGuarantorFailed'));
    }
  };

  return (
    <div className="rounded-md bg-gray-50 p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
        {t('family.guarantor')}
      </p>
      <div className="mt-1 flex flex-wrap items-center gap-3">
        {patient.guarantorId ? (
          <Link
            to={`/patients/${patient.guarantorId}`}
            className="text-sm font-medium text-brand-700 hover:underline"
          >
            {patient.guarantorLastName}, {patient.guarantorFirstName}
          </Link>
        ) : (
          <span className="text-sm font-medium text-gray-900">{t('family.self')}</span>
        )}
        {canEdit && (
          <>
            <Button variant="ghost" onClick={() => setEditing((e) => !e)}>
              {editing ? t('common:cancel') : t('family.changeGuarantor')}
            </Button>
            {patient.guarantorId && (
              <Button
                variant="ghost"
                disabled={setGuarantor.isPending}
                onClick={() => void choose(null)}
              >
                {t('family.clearSelf')}
              </Button>
            )}
          </>
        )}
      </div>
      {editing && canEdit && (
        <div className="mt-2 max-w-md">
          <label htmlFor="guarantor-search" className="block text-sm font-medium text-gray-700">
            {t('family.findGuarantor')}
          </label>
          <input
            id="guarantor-search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('family.searchByName')}
            className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
          {search && (
            <ul className="mt-1 max-h-40 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
              {candidates?.content
                .filter((p) => p.id !== patientId)
                .slice(0, 8)
                .map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      onClick={() => void choose(p.id)}
                      className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                    >
                      {p.lastName}, {p.firstName} ({p.dateOfBirth})
                    </button>
                  </li>
                ))}
            </ul>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
