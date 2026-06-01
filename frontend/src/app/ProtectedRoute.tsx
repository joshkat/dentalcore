import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '../components/Spinner';
import { useAuth } from '../lib/auth';
import type { Role } from '../types/api';

export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const { t } = useTranslation('common');
  const { user, initializing, hasRole } = useAuth();
  const location = useLocation();

  if (initializing) {
    return <Spinner label={t('checkingSession')} />;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (roles && !hasRole(...roles)) {
    return <div className="p-8 text-sm text-gray-600">{t('noPermission')}</div>;
  }
  return <Outlet />;
}
