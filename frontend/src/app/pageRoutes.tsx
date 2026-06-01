import type { RouteObject } from 'react-router-dom';
import { AdminPage } from '../features/admin/AdminPage';
import { CalendarPage } from '../features/appointments/CalendarPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ClaimsPage } from '../features/insurance/ClaimsPage';
import { InsurancePage } from '../features/insurance/InsurancePage';
import { FormsPage } from '../features/forms/FormsPage';
import { NewPatientPage } from '../features/patients/NewPatientPage';
import { PatientDetailPage } from '../features/patients/PatientDetailPage';
import { PatientsPage } from '../features/patients/PatientsPage';
import { ProcedureCodesPage } from '../features/procedures/ProcedureCodesPage';
import { ProvidersPage } from '../features/providers/ProvidersPage';
import { RecallPage } from '../features/reminders/RecallPage';
import { ReportsPage } from '../features/reports/ReportsPage';
import { SettingsPage } from '../features/settings/SettingsPage';
import { UsersPage } from '../features/users/UsersPage';
import { WorklistsPage } from '../features/worklists/WorklistsPage';
import i18n from '../i18n';
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
  { path: '/forms', element: <FormsPage /> },
  { path: '/worklists', element: <WorklistsPage /> },
  { path: '/settings', element: <SettingsPage /> },
  {
    element: <ProtectedRoute roles={['ADMIN']} />,
    children: [
      { path: '/users', element: <UsersPage /> },
      { path: '/admin', element: <AdminPage /> },
    ],
  },
];

/** Path → `nav` namespace key. Translated at call time; callers that need to
 *  re-render on language change should subscribe via useTranslation(). */
const titleKeys: Array<[RegExp, string]> = [
  [/^\/$/, 'dashboard'],
  [/^\/schedule/, 'schedule'],
  [/^\/patients\/new/, 'newPatient'],
  [/^\/patients\/.+/, 'patientDetail'],
  [/^\/patients/, 'patients'],
  [/^\/providers/, 'providers'],
  [/^\/procedures/, 'procedures'],
  [/^\/insurance/, 'insurance'],
  [/^\/claims/, 'claims'],
  [/^\/reports/, 'reports'],
  [/^\/recall/, 'recall'],
  [/^\/forms/, 'forms'],
  [/^\/worklists/, 'worklists'],
  [/^\/settings/, 'settings'],
  [/^\/users/, 'users'],
  [/^\/admin/, 'admin'],
];

export function pageTitle(path: string): string {
  const match = titleKeys.find(([re]) => re.test(path));
  return match ? i18n.t(`nav:${match[1]}`) : path;
}
