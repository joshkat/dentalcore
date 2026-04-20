import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
import { useAlerts, useCreateAlert, useDeleteAlert } from './api';

const severityTone = { LOW: 'blue', MEDIUM: 'yellow', HIGH: 'red' } as const;

export function AlertsTab({ patientId, canWrite }: { patientId: string; canWrite: boolean }) {
  const { data: alerts, isPending } = useAlerts(patientId);
  const createAlert = useCreateAlert(patientId);
  const deleteAlert = useDeleteAlert(patientId);

  const [type, setType] = useState('ALLERGY');
  const [severity, setSeverity] = useState('MEDIUM');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const add = async () => {
    if (!description.trim()) {
      setError('Description is required');
      return;
    }
    setError(null);
    await createAlert.mutateAsync({ type, severity, description: description.trim() });
    setDescription('');
  };

  if (isPending) return <Spinner label="Loading alerts…" />;

  return (
    <div className="space-y-4">
      {canWrite && (
        <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
          <div>
            <label htmlFor="alert-type" className="block text-sm font-medium text-gray-700">
              Type
            </label>
            <select
              id="alert-type"
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="ALLERGY">Allergy</option>
              <option value="CONDITION">Condition</option>
              <option value="ALERT">Alert</option>
              <option value="MEDICATION">Medication</option>
            </select>
          </div>
          <div>
            <label htmlFor="alert-severity" className="block text-sm font-medium text-gray-700">
              Severity
            </label>
            <select
              id="alert-severity"
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
          </div>
          <Input
            label="Description"
            className="min-w-64 flex-1"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={error ?? undefined}
          />
          <Button onClick={add} loading={createAlert.isPending}>
            Add alert
          </Button>
        </div>
      )}

      {alerts && alerts.length === 0 ? (
        <p className="text-sm text-gray-500">No medical alerts recorded.</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-md bg-white">
          {alerts?.map((alert) => (
            <li key={alert.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="flex items-center gap-3">
                <Badge tone={severityTone[alert.severity]}>{alert.severity}</Badge>
                <div>
                  <p className="text-sm font-medium text-gray-900">{alert.description}</p>
                  <p className="text-xs text-gray-500">{alert.type}</p>
                </div>
              </div>
              {canWrite && (
                <Button
                  variant="ghost"
                  onClick={() => deleteAlert.mutate(alert.id)}
                  disabled={deleteAlert.isPending}
                >
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
