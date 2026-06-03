import { useTranslation } from 'react-i18next';
import { Spinner } from '../../components/Spinner';
import { formatDateTime } from '../../i18n/format';
import { useTimeline } from './api';

const KNOWN_ACTIONS = ['CREATE', 'UPDATE', 'STATUS_CHANGE', 'DELETE'] as const;

function describe(value: Record<string, unknown> | null): string | null {
  if (!value) return null;
  return Object.entries(value)
    .map(([key, val]) => `${key}: ${String(val)}`)
    .join(' · ');
}

export function TimelineTab({ patientId }: { patientId: string }) {
  const { t } = useTranslation('patients');
  const { data: events, isPending } = useTimeline(patientId);

  if (isPending) return <Spinner label={t('timeline.loading')} />;
  if (!events || events.length === 0) {
    return <p className="text-sm text-gray-500">{t('timeline.none')}</p>;
  }

  return (
    <ol className="space-y-4">
      {events.map((event) => (
        <li key={event.id} className="flex gap-4">
          <div className="flex flex-col items-center">
            <span className="mt-1 h-2.5 w-2.5 rounded-full bg-brand-500" />
            <span className="w-px flex-1 bg-gray-200" />
          </div>
          <div className="pb-4">
            <p className="text-sm font-medium text-gray-900">
              {(KNOWN_ACTIONS as readonly string[]).includes(event.action)
                ? t(`timeline.action.${event.action}`)
                : event.action}
            </p>
            <p className="text-xs text-gray-500">{formatDateTime(event.occurredAt)}</p>
            {describe(event.newValue) && (
              <p className="mt-1 text-xs text-gray-600">{describe(event.newValue)}</p>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}
