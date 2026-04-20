import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import type { Provider } from '../../types/api';
import { useProviders } from './api';
import { AvailabilityModal } from './AvailabilityModal';
import { ProviderFormModal } from './ProviderFormModal';

export function ProvidersPage() {
  const [includeInactive, setIncludeInactive] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Provider | null>(null);
  const [availabilityFor, setAvailabilityFor] = useState<Provider | null>(null);
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');

  const { data, isPending, isError } = useProviders(includeInactive);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Providers</h1>
        {isAdmin && (
          <Button
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            New provider
          </Button>
        )}
      </div>

      <label className="mt-4 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeInactive}
          onChange={(e) => setIncludeInactive(e.target.checked)}
          className="h-4 w-4 rounded border-gray-300 text-brand-600"
        />
        Show inactive providers
      </label>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label="Loading providers…" />
        ) : isError ? (
          <p className="p-8 text-sm text-red-600">Failed to load providers.</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                {['Name', 'Type', 'NPI', 'Specialty', 'License', 'Status', ''].map((h, i) => (
                  <th
                    key={i}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((provider) => (
                <tr key={provider.id}>
                  <td className="px-4 py-3 text-sm font-medium text-gray-900">
                    <span className="flex items-center gap-2">
                      <span
                        className="h-3 w-3 rounded-full"
                        style={{ backgroundColor: provider.color }}
                        aria-hidden="true"
                      />
                      {provider.lastName}, {provider.firstName}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{provider.type}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{provider.npi ?? '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{provider.specialty ?? '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {provider.licenseNumber
                      ? `${provider.licenseNumber} (${provider.licenseState ?? '—'})`
                      : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {provider.active ? (
                      <Badge tone="green">ACTIVE</Badge>
                    ) : (
                      <Badge tone="gray">INACTIVE</Badge>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {isAdmin && (
                      <span className="flex justify-end gap-3">
                        <button
                          onClick={() => setAvailabilityFor(provider)}
                          className="text-sm text-brand-600 hover:underline"
                        >
                          Hours
                        </button>
                        <button
                          onClick={() => {
                            setEditing(provider);
                            setModalOpen(true);
                          }}
                          className="text-sm text-brand-600 hover:underline"
                        >
                          Edit
                        </button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-500">
                    No providers yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <ProviderFormModal open={modalOpen} onClose={() => setModalOpen(false)} provider={editing} />
      <AvailabilityModal provider={availabilityFor} onClose={() => setAvailabilityFor(null)} />
    </div>
  );
}
