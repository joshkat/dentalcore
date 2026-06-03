import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
import { useAlerts, useCreateAlert, useDeleteAlert } from './api';

const severityTone = { LOW: 'blue', MEDIUM: 'yellow', HIGH: 'red' } as const;

export function AlertsTab({ patientId, canWrite }: { patientId: string; canWrite: boolean }) {
  const { t } = useTranslation('patients');
  const { data: alerts, isPending } = useAlerts(patientId);
  const createAlert = useCreateAlert(patientId);
  const deleteAlert = useDeleteAlert(patientId);

  const [type, setType] = useState('ALLERGY');
  const [severity, setSeverity] = useState('MEDIUM');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const add = async () => {
    if (!description.trim()) {
      setError(t('alerts.descriptionRequired'));
      return;
    }
    setError(null);
    await createAlert.mutateAsync({ type, severity, description: description.trim() });
    setDescription('');
  };

  if (isPending) return <Spinner label={t('alerts.loading')} />;

  return (
    <div className="space-y-4">
      {canWrite && (
        <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
          <div>
            <label htmlFor="alert-type" className="block text-sm font-medium text-gray-700">
              {t('alerts.type')}
            </label>
            <select
              id="alert-type"
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="ALLERGY">{t('alerts.typeOption.ALLERGY')}</option>
              <option value="CONDITION">{t('alerts.typeOption.CONDITION')}</option>
              <option value="ALERT">{t('alerts.typeOption.ALERT')}</option>
              <option value="MEDICATION">{t('alerts.typeOption.MEDICATION')}</option>
            </select>
          </div>
          <div>
            <label htmlFor="alert-severity" className="block text-sm font-medium text-gray-700">
              {t('alerts.severity')}
            </label>
            <select
              id="alert-severity"
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="LOW">{t('alerts.severityOption.LOW')}</option>
              <option value="MEDIUM">{t('alerts.severityOption.MEDIUM')}</option>
              <option value="HIGH">{t('alerts.severityOption.HIGH')}</option>
            </select>
          </div>
          <Input
            label={t('alerts.description')}
            className="min-w-64 flex-1"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={error ?? undefined}
          />
          <Button onClick={add} loading={createAlert.isPending}>
            {t('alerts.add')}
          </Button>
        </div>
      )}

      {alerts && alerts.length === 0 ? (
        <p className="text-sm text-gray-500">{t('alerts.none')}</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-md bg-white">
          {alerts?.map((alert) => (
            <li key={alert.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="flex items-center gap-3">
                <Badge tone={severityTone[alert.severity]}>
                  {t(`alerts.severityBadge.${alert.severity}`)}
                </Badge>
                <div>
                  <p className="text-sm font-medium text-gray-900">{alert.description}</p>
                  <p className="text-xs text-gray-500">{t(`alerts.typeBadge.${alert.type}`)}</p>
                </div>
              </div>
              {canWrite && (
                <Button
                  variant="ghost"
                  onClick={() => deleteAlert.mutate(alert.id)}
                  disabled={deleteAlert.isPending}
                >
                  {t('alerts.remove')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
