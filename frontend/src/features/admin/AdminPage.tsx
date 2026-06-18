import { useTranslation } from 'react-i18next';
import { DuplicatePatientsSection } from './DuplicatePatientsSection';
import { PermissionMatrixSection } from './PermissionMatrixSection';

export function AdminPage() {
  const { t } = useTranslation('admin');
  return (
    <div className="space-y-6 p-8">
      <h1 className="text-2xl font-bold text-gray-900">{t('pageTitle')}</h1>
      <PermissionMatrixSection />
      <DuplicatePatientsSection />
    </div>
  );
}
