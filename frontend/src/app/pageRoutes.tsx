import type { RouteObject } from 'react-router-dom';
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
import { ProtectedRoute } from './ProtectedRoute';

/** Single source of truth for in-app pages — used by the main router and by each pane's MemoryRouter. */
export const pageRoutes: RouteObject[] = [
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
  {
    element: <ProtectedRoute roles={['ADMIN']} />,
    children: [{ path: '/users', element: <UsersPage /> }],
  },
];

const titles: Array<[RegExp, string]> = [
  [/^\/$/, 'Dashboard'],
  [/^\/schedule/, 'Schedule'],
  [/^\/patients\/new/, 'New Patient'],
  [/^\/patients\/.+/, 'Patient'],
  [/^\/patients/, 'Patients'],
  [/^\/providers/, 'Providers'],
  [/^\/procedures/, 'Procedures'],
  [/^\/insurance/, 'Insurance'],
  [/^\/claims/, 'Claims'],
  [/^\/reports/, 'Reports'],
  [/^\/recall/, 'Recall'],
  [/^\/users/, 'Users'],
];

export function pageTitle(path: string): string {
  const match = titles.find(([re]) => re.test(path));
  return match ? match[1] : path;
}
