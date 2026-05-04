import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export interface CompletedProcedure {
  id: string;
  patientId: string;
  providerId: string;
  providerFirstName: string | null;
  providerLastName: string | null;
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  tooth: string | null;
  surfaces: string | null;
  fee: number;
  appointmentId: string | null;
  plannedProcedureId: string | null;
  completedAt: string;
  entryDate: string;
}

export interface CompleteProcedureInput {
  patientId: string;
  providerId: string;
  procedureCodeId: string;
  appointmentId?: string;
  plannedProcedureId?: string;
  tooth?: string;
  surfaces?: string;
  feeOverride?: number;
  notes?: string;
}

export function useCompletedProcedures(patientId: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ['completed-procedures', patientId, { from, to }],
    queryFn: () => {
      const params = new URLSearchParams({ patientId });
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      return api<CompletedProcedure[]>(`/api/v1/completed-procedures?${params.toString()}`);
    },
  });
}

/** Completing (or undoing) work touches the chart, plans, and the ledger — refresh them all. */
function invalidateAfterCompletion(queryClient: QueryClient, patientId: string) {
  queryClient.invalidateQueries({ queryKey: ['completed-procedures', patientId] });
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'chart'] });
  queryClient.invalidateQueries({ queryKey: ['treatment-plans'] });
  queryClient.invalidateQueries({ queryKey: ['plan-estimate'] });
  queryClient.invalidateQueries({ queryKey: ['ledger'] });
  queryClient.invalidateQueries({ queryKey: ['balance'] });
}

export function useCompleteProcedure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CompleteProcedureInput) =>
      api<CompletedProcedure>('/api/v1/completed-procedures', { method: 'POST', body: input }),
    onSuccess: (created) => invalidateAfterCompletion(queryClient, created.patientId),
  });
}

/** Undo a completion (backend only allows same business day; errors surface to the caller). */
export function useUndoCompletedProcedure(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (completedProcedureId: string) =>
      api<void>(`/api/v1/completed-procedures/${completedProcedureId}`, { method: 'DELETE' }),
    onSuccess: () => invalidateAfterCompletion(queryClient, patientId),
  });
}
