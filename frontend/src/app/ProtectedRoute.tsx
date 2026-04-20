import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '../components/Spinner';
import { useAuth } from '../lib/auth';
import type { Role } from '../types/api';

export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const { user, initializing, hasRole } = useAuth();
  const location = useLocation();

  if (initializing) {
    return <Spinner label="Checking session…" />;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (roles && !hasRole(...roles)) {
    return (
      <div className="p-8 text-sm text-gray-600">
        You do not have permission to view this page.
      </div>
    );
  }
  return <Outlet />;
}
