import { useState } from 'react';
import { Link } from 'react-router-dom';
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
      setError('Select a patient to link');
      return;
    }
    setError(null);
    try {
      await createLink.mutateAsync({ relatedPatientId: selectedId, relationship });
      setSearch('');
      setSelectedId('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to link patients');
    }
  };

  if (isPending) return <Spinner label="Loading family…" />;

  return (
    <div className="space-y-4">
      <GuarantorSection patientId={patientId} />
      {canWrite && (
        <div className="rounded-md bg-gray-50 p-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-64 flex-1">
              <label htmlFor="family-search" className="block text-sm font-medium text-gray-700">
                Find patient
              </label>
              <input
                id="family-search"
                type="search"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setSelectedId('');
                }}
                placeholder="Search by name…"
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
                Relationship
              </label>
              <select
                id="family-rel"
                value={relationship}
                onChange={(e) => setRelationship(e.target.value)}
                className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              >
                <option value="SPOUSE">Spouse</option>
                <option value="CHILD">Child</option>
                <option value="PARENT">Parent</option>
                <option value="SIBLING">Sibling</option>
                <option value="GUARANTOR">Guarantor</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <Button onClick={add} loading={createLink.isPending}>
              Link
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
        <p className="text-sm text-gray-500">No family relationships recorded.</p>
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
                <p className="text-xs text-gray-500">{link.relationship}</p>
              </div>
              {canWrite && (
                <Button
                  variant="ghost"
                  onClick={() => deleteLink.mutate(link.id)}
                  disabled={deleteLink.isPending}
                >
                  Unlink
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
      setError(e instanceof ApiError ? e.message : 'Failed to update guarantor');
    }
  };

  return (
    <div className="rounded-md bg-gray-50 p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Guarantor</p>
      <div className="mt-1 flex flex-wrap items-center gap-3">
        {patient.guarantorId ? (
          <Link
            to={`/patients/${patient.guarantorId}`}
            className="text-sm font-medium text-brand-700 hover:underline"
          >
            {patient.guarantorLastName}, {patient.guarantorFirstName}
          </Link>
        ) : (
          <span className="text-sm font-medium text-gray-900">Self</span>
        )}
        {canEdit && (
          <>
            <Button variant="ghost" onClick={() => setEditing((e) => !e)}>
              {editing ? 'Cancel' : 'Change guarantor'}
            </Button>
            {patient.guarantorId && (
              <Button
                variant="ghost"
                disabled={setGuarantor.isPending}
                onClick={() => void choose(null)}
              >
                Clear (self)
              </Button>
            )}
          </>
        )}
      </div>
      {editing && canEdit && (
        <div className="mt-2 max-w-md">
          <label htmlFor="guarantor-search" className="block text-sm font-medium text-gray-700">
            Find guarantor
          </label>
          <input
            id="guarantor-search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name…"
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
