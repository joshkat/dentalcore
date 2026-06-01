import { useTranslation } from 'react-i18next';

export function Spinner({ label }: { label?: string }) {
  const { t } = useTranslation('common');
  return (
    <div className="flex items-center justify-center gap-3 p-8 text-gray-500" role="status">
      <span className="h-6 w-6 animate-spin rounded-full border-2 border-gray-300 border-t-brand-600" />
      <span className="text-sm">{label ?? t('loading')}</span>
    </div>
  );
}
