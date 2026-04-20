import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../types/api';

export type DocumentCategory =
  | 'XRAY'
  | 'PHOTO'
  | 'CONSENT'
  | 'INSURANCE'
  | 'REFERRAL'
  | 'OTHER';

export const DOCUMENT_CATEGORIES: DocumentCategory[] = [
  'XRAY',
  'PHOTO',
  'CONSENT',
  'INSURANCE',
  'REFERRAL',
  'OTHER',
];

export interface PatientDocument {
  id: string;
  patientId: string;
  category: DocumentCategory;
  filename: string;
  contentType: string;
  sizeBytes: number;
  notes: string | null;
  uploadedBy: string | null;
  createdAt: string;
}

export function useDocuments(patientId: string) {
  return useQuery({
    queryKey: ['documents', { patientId }],
    queryFn: () =>
      api<PageResponse<PatientDocument>>(`/api/v1/documents?patientId=${patientId}&size=100`),
  });
}

export function useUploadDocument(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: { file: File; category: string; notes?: string }) => {
      // multipart: bypass the JSON helper but keep auth + refresh semantics simple
      const form = new FormData();
      form.append('file', input.file);
      const params = new URLSearchParams({
        patientId,
        category: input.category,
        ...(input.notes ? { notes: input.notes } : {}),
      });
      return uploadWithAuth(`/api/v1/documents?${params}`, form);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  });
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/documents/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  });
}

// ---- helpers that need raw fetch (multipart / binary) ----

import { ApiError, getAccessToken, refreshSession } from '../../lib/api';

async function uploadWithAuth(url: string, form: FormData): Promise<PatientDocument> {
  const attempt = () =>
    fetch(url, {
      method: 'POST',
      headers: authHeader(),
      credentials: 'include',
      body: form,
    });
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    let problem = null;
    try {
      problem = await response.json();
    } catch {
      // ignore
    }
    throw new ApiError(response.status, problem);
  }
  return (await response.json()) as PatientDocument;
}

export async function downloadDocument(doc: PatientDocument): Promise<void> {
  const attempt = () =>
    fetch(`/api/v1/documents/${doc.id}/download`, {
      headers: authHeader(),
      credentials: 'include',
    });
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    throw new ApiError(response.status, null);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = doc.filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function authHeader(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
