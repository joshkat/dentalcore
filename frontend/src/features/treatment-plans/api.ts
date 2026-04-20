import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type {
  PageResponse,
  TreatmentPlan,
  TreatmentPlanStatus,
  TreatmentPlanSummary,
} from '../../types/api';

export function useTreatmentPlans(patientId: string) {
  return useQuery({
    queryKey: ['treatment-plans', { patientId }],
    queryFn: () =>
      api<PageResponse<TreatmentPlanSummary>>(
        `/api/v1/treatment-plans?patientId=${patientId}&size=50`,
      ),
  });
}

export function useTreatmentPlan(id: string | null) {
  return useQuery({
    queryKey: ['treatment-plans', 'detail', id],
    queryFn: () => api<TreatmentPlan>(`/api/v1/treatment-plans/${id}`),
    enabled: id !== null,
  });
}

function usePlanMutation<TInput>(
  _patientId: string,
  mutationFn: (input: TInput) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['treatment-plans'] }),
  });
}

export function useCreatePlan(patientId: string) {
  return usePlanMutation(patientId, (input: { providerId: string; title: string }) =>
    api<TreatmentPlan>('/api/v1/treatment-plans', {
      method: 'POST',
      body: { ...input, patientId },
    }),
  );
}

export function useUpdatePlanStatus(patientId: string) {
  return usePlanMutation(patientId, (input: { planId: string; status: TreatmentPlanStatus }) =>
    api<TreatmentPlan>(`/api/v1/treatment-plans/${input.planId}/status`, {
      method: 'PATCH',
      body: { status: input.status },
    }),
  );
}

export interface AddProcedureInput {
  planId: string;
  procedureCodeId: string;
  tooth?: string;
  surface?: string;
  priority?: number;
}

export function useAddPlanProcedure(patientId: string) {
  return usePlanMutation(patientId, ({ planId, ...body }: AddProcedureInput) =>
    api<TreatmentPlan>(`/api/v1/treatment-plans/${planId}/procedures`, {
      method: 'POST',
      body,
    }),
  );
}

export function useRemovePlanProcedure(patientId: string) {
  return usePlanMutation(patientId, (input: { planId: string; procedureId: string }) =>
    api<TreatmentPlan>(`/api/v1/treatment-plans/${input.planId}/procedures/${input.procedureId}`, {
      method: 'DELETE',
    }),
  );
}

export function useUpdatePlanProcedureStatus(patientId: string) {
  return usePlanMutation(
    patientId,
    (input: { planId: string; procedureId: string; status: string }) =>
      api<TreatmentPlan>(
        `/api/v1/treatment-plans/${input.planId}/procedures/${input.procedureId}/status`,
        { method: 'PATCH', body: { status: input.status } },
      ),
  );
}

export const PLAN_NEXT_STATUSES: Record<TreatmentPlanStatus, TreatmentPlanStatus[]> = {
  DRAFT: ['PRESENTED', 'CANCELLED'],
  PRESENTED: ['APPROVED', 'DRAFT', 'CANCELLED'],
  APPROVED: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
};

export const PLAN_STATUS_LABELS: Record<TreatmentPlanStatus, string> = {
  DRAFT: 'Draft',
  PRESENTED: 'Presented',
  APPROVED: 'Approved',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};
