import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import { usePatients } from './api';

const statusTone = {
  ACTIVE: 'green',
  INACTIVE: 'yellow',
  ARCHIVED: 'gray',
} as const;

export function PatientsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK');

  const { data, isPending, isError } = usePatients(search, page);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Patients</h1>
        {canWrite && <Button onClick={() => navigate('/patients/new')}>New patient</Button>}
      </div>

      <div className="mt-4">
        <input
          type="search"
          placeholder="Search by name, email, or phone…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="Search patients"
          className="w-full max-w-md rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600"
        />
      </div>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label="Loading patients…" />
        ) : isError ? (
          <p className="p-8 text-sm text-red-600">Failed to load patients.</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                {['Name', 'Date of birth', 'Phone', 'Email', 'Status'].map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((patient) => (
                <tr key={patient.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-medium">
                    <Link to={`/patients/${patient.id}`} className="text-brand-700 hover:underline">
                      {patient.lastName}, {patient.firstName}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{patient.dateOfBirth}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{patient.primaryPhone ?? '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{patient.email ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-1">
                      <Badge tone={statusTone[patient.status]}>{patient.status}</Badge>
                      {patient.nextRecallDate &&
                        patient.nextRecallDate <= new Date().toISOString().slice(0, 10) && (
                          <Badge tone="red">RECALL DUE</Badge>
                        )}
                    </span>
                  </td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-500">
                    No patients found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
          <span>
            Page {data.page + 1} of {data.totalPages} ({data.totalElements} patients)
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={data.page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
