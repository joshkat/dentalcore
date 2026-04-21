import { createBrowserRouter } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage';
import { ResetPasswordPage } from '../features/auth/ResetPasswordPage';
import { Layout } from './Layout';
import { pageRoutes } from './pageRoutes';
import { ProtectedRoute } from './ProtectedRoute';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: pageRoutes,
      },
    ],
  },
]);
