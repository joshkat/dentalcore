import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type {
  FamilyLink,
  MedicalAlert,
  PageResponse,
  Patient,
  PatientStatus,
  PatientSummary,
  TimelineEvent,
  ToothChart,
  ToothCondition,
} from '../../types/api';
import type { PatientFormValues } from './schemas';

export function usePatients(q: string, page: number) {
  return useQuery({
    queryKey: ['patients', { q, page }],
    queryFn: () =>
      api<PageResponse<PatientSummary>>(
        `/api/v1/patients?q=${encodeURIComponent(q)}&page=${page}&size=25`,
      ),
    placeholderData: (previous) => previous,
  });
}

export function usePatient(id: string) {
  return useQuery({
    queryKey: ['patients', id],
    queryFn: () => api<Patient>(`/api/v1/patients/${id}`),
  });
}

function toRequestBody(values: PatientFormValues) {
  return {
    ...values,
    middleName: values.middleName || null,
    email: values.email || null,
    addressLine1: values.addressLine1 || null,
    addressLine2: values.addressLine2 || null,
    city: values.city || null,
    state: values.state || null,
    postalCode: values.postalCode || null,
    preferredLanguage: values.preferredLanguage || null,
    emergencyContactName: values.emergencyContactName || null,
    emergencyContactPhone: values.emergencyContactPhone || null,
    emergencyContactRelationship: values.emergencyContactRelationship || null,
    notes: values.notes || null,
    preferredName: values.preferredName || null,
    pronouns: values.pronouns || null,
    employer: values.employer || null,
    occupation: values.occupation || null,
    referralSource: values.referralSource || null,
    preferredContactMethod: values.preferredContactMethod || null,
    pharmacyName: values.pharmacyName || null,
    pharmacyPhone: values.pharmacyPhone || null,
    primaryProviderId: values.primaryProviderId || null,
  };
}

export function useCreatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: PatientFormValues) =>
      api<Patient>('/api/v1/patients', { method: 'POST', body: toRequestBody(values) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patients'] }),
  });
}

export function useUpdatePatient(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: PatientFormValues) =>
      api<Patient>(`/api/v1/patients/${id}`, { method: 'PUT', body: toRequestBody(values) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patients'] }),
  });
}

export function useUpdatePatientStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (status: PatientStatus) =>
      api<Patient>(`/api/v1/patients/${id}/status`, { method: 'PATCH', body: { status } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patients'] }),
  });
}

// ---- Medical alerts ----

export function useAlerts(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'alerts'],
    queryFn: () => api<MedicalAlert[]>(`/api/v1/patients/${patientId}/alerts`),
  });
}

export interface AlertInput {
  type: string;
  description: string;
  severity: string;
  active?: boolean;
}

export function useCreateAlert(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AlertInput) =>
      api<MedicalAlert>(`/api/v1/patients/${patientId}/alerts`, {
        method: 'POST',
        body: input,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'alerts'] }),
  });
}

export function useDeleteAlert(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) =>
      api<void>(`/api/v1/patients/${patientId}/alerts/${alertId}`, { method: 'DELETE' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'alerts'] }),
  });
}

// ---- Family links ----

export function useFamily(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'family'],
    queryFn: () => api<FamilyLink[]>(`/api/v1/patients/${patientId}/family`),
  });
}

export function useCreateFamilyLink(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { relatedPatientId: string; relationship: string }) =>
      api<FamilyLink>(`/api/v1/patients/${patientId}/family`, { method: 'POST', body: input }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'family'] }),
  });
}

export function useDeleteFamilyLink(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (linkId: string) =>
      api<void>(`/api/v1/patients/${patientId}/family/${linkId}`, { method: 'DELETE' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'family'] }),
  });
}

// ---- Timeline ----

export function useTimeline(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'timeline'],
    queryFn: () => api<TimelineEvent[]>(`/api/v1/patients/${patientId}/timeline`),
  });
}

// ---- Tooth chart ----

export function useToothChart(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'chart'],
    queryFn: () => api<ToothChart>(`/api/v1/patients/${patientId}/chart`),
  });
}

export interface ToothConditionInput {
  tooth: string;
  condition: string;
  surfaces?: string;
  notes?: string;
}

function useChartMutation<TInput>(
  patientId: string,
  mutationFn: (input: TInput) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'chart'] }),
  });
}

export function useAddToothCondition(patientId: string) {
  return useChartMutation(patientId, (input: ToothConditionInput) =>
    api<ToothCondition>(`/api/v1/patients/${patientId}/tooth-conditions`, {
      method: 'POST',
      body: input,
    }),
  );
}

export function useResolveToothCondition(patientId: string) {
  return useChartMutation(patientId, (conditionId: string) =>
    api<ToothCondition>(
      `/api/v1/patients/${patientId}/tooth-conditions/${conditionId}/resolve`,
      { method: 'POST' },
    ),
  );
}

export function useDeleteToothCondition(patientId: string) {
  return useChartMutation(patientId, (conditionId: string) =>
    api<void>(`/api/v1/patients/${patientId}/tooth-conditions/${conditionId}`, {
      method: 'DELETE',
    }),
  );
}

// ---- Recall ----

export function useUpdateRecall(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { intervalMonths: number; nextRecallDate: string | null }) =>
      api<Patient>(`/api/v1/patients/${patientId}/recall`, { method: 'PATCH', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patients'] }),
  });
}

// ---- Perio charting ----

export interface PerioSite {
  tooth: string;
  site: number;
  pocketDepth: number | null;
  recession: number | null;
  bleeding: boolean;
  suppuration: boolean;
}

export interface PerioToothFinding {
  tooth: string;
  mobility: number | null;
  furcation: number | null;
}

export interface PerioExam {
  id: string;
  patientId: string;
  providerId: string | null;
  examDate: string;
  notes: string | null;
  measurements: PerioSite[];
  toothFindings: PerioToothFinding[];
}

export interface PerioExamSummary {
  id: string;
  examDate: string;
  providerId: string | null;
  sitesRecorded: number;
  bleedingSites: number;
  sites4mmPlus: number;
  sites6mmPlus: number;
}

export function usePerioExams(patientId: string) {
  return useQuery({
    queryKey: ['perio', patientId],
    queryFn: () => api<PerioExamSummary[]>(`/api/v1/patients/${patientId}/perio-exams`),
  });
}

export function usePerioExam(patientId: string, examId: string | null) {
  return useQuery({
    queryKey: ['perio', patientId, examId],
    queryFn: () => api<PerioExam>(`/api/v1/patients/${patientId}/perio-exams/${examId}`),
    enabled: examId !== null,
  });
}

export function useCreatePerioExam(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api<PerioExam>(`/api/v1/patients/${patientId}/perio-exams`, { method: 'POST', body: {} }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['perio', patientId] }),
  });
}

export interface SavePerioInput {
  examId: string;
  measurements: Array<{
    tooth: string;
    site: number;
    pocketDepth?: number | null;
    bleeding?: boolean;
  }>;
  toothFindings: Array<{ tooth: string; mobility?: number | null }>;
}

export function useSavePerio(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examId, ...body }: SavePerioInput) =>
      api<PerioExam>(`/api/v1/patients/${patientId}/perio-exams/${examId}/measurements`, {
        method: 'PUT',
        body,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['perio', patientId] }),
  });
}
