import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { ClinicalNote, PageResponse } from '../../types/api';

export function useClinicalNotes(patientId: string) {
  return useQuery({
    queryKey: ['clinical-notes', { patientId }],
    queryFn: () =>
      api<PageResponse<ClinicalNote>>(`/api/v1/clinical-notes?patientId=${patientId}&size=50`),
  });
}

function useNoteMutation<TInput>(mutationFn: (input: TInput) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clinical-notes'] }),
  });
}

export function useCreateNote(patientId: string) {
  return useNoteMutation((input: { noteType: string; body: string }) =>
    api<ClinicalNote>(`/api/v1/clinical-notes?patientId=${patientId}`, {
      method: 'POST',
      body: input,
    }),
  );
}

export function useUpdateNote() {
  return useNoteMutation((input: { id: string; noteType: string; body: string }) =>
    api<ClinicalNote>(`/api/v1/clinical-notes/${input.id}`, {
      method: 'PUT',
      body: { noteType: input.noteType, body: input.body },
    }),
  );
}

export function useSignNote() {
  return useNoteMutation((id: string) =>
    api<ClinicalNote>(`/api/v1/clinical-notes/${id}/sign`, { method: 'POST' }),
  );
}

export function useDeleteNote() {
  return useNoteMutation((id: string) =>
    api<void>(`/api/v1/clinical-notes/${id}`, { method: 'DELETE' }),
  );
}
