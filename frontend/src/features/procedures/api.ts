import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse, ProcedureCode } from '../../types/api';

export interface ProcedureCodeInput {
  code: string;
  description: string;
  category: string;
  standardFee: number;
  cdtCode?: string | null;
  active?: boolean;
}

export function useProcedureCodes(q: string, includeInactive = false) {
  return useQuery({
    queryKey: ['procedure-codes', { q, includeInactive }],
    queryFn: () =>
      api<PageResponse<ProcedureCode>>(
        `/api/v1/procedure-codes?q=${encodeURIComponent(q)}&includeInactive=${includeInactive}&size=200`,
      ),
    placeholderData: (previous) => previous,
  });
}

export function useCreateProcedureCode() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProcedureCodeInput) =>
      api<ProcedureCode>('/api/v1/procedure-codes', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procedure-codes'] }),
  });
}

export function useUpdateProcedureCode(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProcedureCodeInput) =>
      api<ProcedureCode>(`/api/v1/procedure-codes/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procedure-codes'] }),
  });
}
