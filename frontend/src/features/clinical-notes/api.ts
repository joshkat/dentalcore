import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { ClinicalNote, NoteTemplate, PageResponse } from '../../types/api';

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

// ---- note templates (auto-notes) ----

export interface NoteTemplateInput {
  name: string;
  noteType: string;
  body: string;
}

export function useNoteTemplates() {
  return useQuery({
    queryKey: ['note-templates'],
    queryFn: async () => {
      const res = await api<NoteTemplate[] | PageResponse<NoteTemplate>>(
        '/api/v1/clinical-notes/templates',
      );
      return Array.isArray(res) ? res : res.content;
    },
  });
}

function useNoteTemplateMutation<TInput>(mutationFn: (input: TInput) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['note-templates'] }),
  });
}

export function useCreateNoteTemplate() {
  return useNoteTemplateMutation((input: NoteTemplateInput) =>
    api<NoteTemplate>('/api/v1/clinical-notes/templates', { method: 'POST', body: input }),
  );
}

export function useUpdateNoteTemplate() {
  return useNoteTemplateMutation((input: { id: string } & NoteTemplateInput) =>
    api<NoteTemplate>(`/api/v1/clinical-notes/templates/${input.id}`, {
      method: 'PUT',
      body: { name: input.name, noteType: input.noteType, body: input.body },
    }),
  );
}

export function useDeleteNoteTemplate() {
  return useNoteTemplateMutation((id: string) =>
    api<void>(`/api/v1/clinical-notes/templates/${id}`, { method: 'DELETE' }),
  );
}
