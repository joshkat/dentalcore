import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { InstanceConfig, SupportedLanguage, UserPreferences } from '../../types/api';

const PREFERENCES_KEY = ['users', 'me', 'preferences'] as const;

/** Public endpoint (no auth) — instance-wide defaults. */
export function useInstanceConfig() {
  return useQuery({
    queryKey: ['config'],
    queryFn: () => api<InstanceConfig>('/api/v1/config'),
    staleTime: Infinity,
  });
}

export function usePreferences() {
  return useQuery({
    queryKey: PREFERENCES_KEY,
    queryFn: () => api<UserPreferences>('/api/v1/users/me/preferences'),
  });
}

export interface UpdatePreferencesRequest {
  uiLanguage: SupportedLanguage | null;
  exportLanguage: SupportedLanguage | null;
}

export function useUpdatePreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdatePreferencesRequest) =>
      api<UserPreferences>('/api/v1/users/me/preferences', { method: 'PUT', body }),
    onSuccess: (data) => {
      queryClient.setQueryData(PREFERENCES_KEY, data);
    },
  });
}
