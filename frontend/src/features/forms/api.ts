import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, getAccessToken, refreshSession } from '../../lib/api';
import type {
  FormAnswerValue,
  FormField,
  FormInstance,
  FormTemplate,
  PageResponse,
} from '../../types/api';

export interface FormTemplateInput {
  name: string;
  description?: string;
  fields: FormField[];
}

/** Lists may come back as a bare array or a page wrapper; accept both. */
function unwrap<T>(res: T[] | PageResponse<T>): T[] {
  return Array.isArray(res) ? res : res.content;
}

// ---- templates ----

export function useFormTemplates() {
  return useQuery({
    queryKey: ['form-templates'],
    queryFn: async () =>
      unwrap(await api<FormTemplate[] | PageResponse<FormTemplate>>('/api/v1/forms/templates')),
  });
}

function useTemplateMutation<TInput>(mutationFn: (input: TInput) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['form-templates'] }),
  });
}

export function useCreateFormTemplate() {
  return useTemplateMutation((input: FormTemplateInput) =>
    api<FormTemplate>('/api/v1/forms/templates', { method: 'POST', body: input }),
  );
}

export function useUpdateFormTemplate() {
  return useTemplateMutation((input: { id: string } & FormTemplateInput) =>
    api<FormTemplate>(`/api/v1/forms/templates/${input.id}`, {
      method: 'PUT',
      body: { name: input.name, description: input.description, fields: input.fields },
    }),
  );
}

export function useDeleteFormTemplate() {
  return useTemplateMutation((id: string) =>
    api<void>(`/api/v1/forms/templates/${id}`, { method: 'DELETE' }),
  );
}

// ---- instances ----

export function useFormInstances(patientId: string) {
  return useQuery({
    queryKey: ['form-instances', { patientId }],
    queryFn: async () =>
      unwrap(
        await api<FormInstance[] | PageResponse<FormInstance>>(
          `/api/v1/forms/instances?patientId=${patientId}`,
        ),
      ),
  });
}

export function useFormInstance(id: string) {
  return useQuery({
    queryKey: ['form-instances', 'detail', id],
    queryFn: () => api<FormInstance>(`/api/v1/forms/instances/${id}`),
  });
}

function useInstanceMutation<TInput, TOut = FormInstance>(
  mutationFn: (input: TInput) => Promise<TOut>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['form-instances'] }),
  });
}

export function useCreateFormInstance(patientId: string) {
  return useInstanceMutation((templateId: string) =>
    api<FormInstance>('/api/v1/forms/instances', {
      method: 'POST',
      body: { patientId, templateId },
    }),
  );
}

export function useSaveFormAnswers() {
  return useInstanceMutation((input: { id: string; answers: Record<string, FormAnswerValue> }) =>
    api<FormInstance>(`/api/v1/forms/instances/${input.id}/answers`, {
      method: 'PUT',
      body: { answers: input.answers },
    }),
  );
}

export function useSignFormInstance() {
  return useInstanceMutation(
    (input: { id: string; signaturePngBase64: string; signedByName: string }) =>
      api<FormInstance>(`/api/v1/forms/instances/${input.id}/sign`, {
        method: 'POST',
        body: { signaturePngBase64: input.signaturePngBase64, signedByName: input.signedByName },
      }),
  );
}

// ---- signed-form PDF download (binary; mirrors features/documents) ----

export async function downloadFormPdf(documentId: string, filename: string): Promise<void> {
  const authHeader = (): Record<string, string> => {
    const token = getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  };
  const attempt = () =>
    fetch(`/api/v1/documents/${documentId}/download`, {
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
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
