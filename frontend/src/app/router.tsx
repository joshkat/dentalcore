import { createBrowserRouter } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage';
import { ResetPasswordPage } from '../features/auth/ResetPasswordPage';
import { CalendarPage } from '../features/appointments/CalendarPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ClaimsPage } from '../features/insurance/ClaimsPage';
import { InsurancePage } from '../features/insurance/InsurancePage';
import { NewPatientPage } from '../features/patients/NewPatientPage';
import { PatientDetailPage } from '../features/patients/PatientDetailPage';
import { PatientsPage } from '../features/patients/PatientsPage';
import { ProcedureCodesPage } from '../features/procedures/ProcedureCodesPage';
import { ProvidersPage } from '../features/providers/ProvidersPage';
import { RecallPage } from '../features/reminders/RecallPage';
import { ReportsPage } from '../features/reports/ReportsPage';
import { UsersPage } from '../features/users/UsersPage';
import { Layout } from './Layout';
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
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/schedule', element: <CalendarPage /> },
          { path: '/patients', element: <PatientsPage /> },
          { path: '/patients/new', element: <NewPatientPage /> },
          { path: '/patients/:id', element: <PatientDetailPage /> },
          { path: '/providers', element: <ProvidersPage /> },
          { path: '/procedures', element: <ProcedureCodesPage /> },
          { path: '/insurance', element: <InsurancePage /> },
          { path: '/claims', element: <ClaimsPage /> },
          { path: '/reports', element: <ReportsPage /> },
          { path: '/recall', element: <RecallPage /> },
        ],
      },
    ],
  },
  {
    element: <ProtectedRoute roles={['ADMIN']} />,
    children: [
      {
        element: <Layout />,
        children: [{ path: '/users', element: <UsersPage /> }],
      },
    ],
  },
]);
