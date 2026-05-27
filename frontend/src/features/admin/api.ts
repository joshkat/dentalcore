import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { DuplicatePair, MergeResult, PermissionMatrix, Role } from '../../types/api';

export function usePermissionMatrix() {
  return useQuery({
    queryKey: ['admin', 'permissions'],
    queryFn: () => api<PermissionMatrix>('/api/v1/admin/permissions'),
  });
}

export interface UpdateRolePermissionsInput {
  role: Role;
  permissionCodes: string[];
}

export function useUpdateRolePermissions() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ role, permissionCodes }: UpdateRolePermissionsInput) =>
      api<PermissionMatrix>(`/api/v1/admin/roles/${role}/permissions`, {
        method: 'PUT',
        body: { permissionCodes },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'permissions'] }),
  });
}

export function useDuplicatePatients() {
  return useQuery({
    queryKey: ['admin', 'duplicate-patients'],
    queryFn: () => api<DuplicatePair[]>('/api/v1/patients/duplicates'),
  });
}

export interface MergePatientsInput {
  targetId: string;
  sourceId: string;
}

export function useMergePatients() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ targetId, sourceId }: MergePatientsInput) =>
      api<MergeResult>(`/api/v1/patients/${targetId}/merge`, {
        method: 'POST',
        body: { sourceId },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'duplicate-patients'] });
      // Merged source is archived and its records repointed — anything patient-shaped is stale.
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });
}
