import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse, Role, UserResponse } from '../../types/api';

export interface CreateUserInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  roles: Role[];
}

export interface UpdateUserInput {
  firstName: string;
  lastName: string;
  roles: Role[];
  status: 'ACTIVE' | 'DISABLED';
}

const usersKey = (search: string, page: number) => ['users', { search, page }] as const;

export function useUsers(search: string, page: number) {
  return useQuery({
    queryKey: usersKey(search, page),
    queryFn: () =>
      api<PageResponse<UserResponse>>(
        `/api/v1/users?search=${encodeURIComponent(search)}&page=${page}&size=25`,
      ),
    placeholderData: (previous) => previous,
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateUserInput) =>
      api<UserResponse>('/api/v1/users', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useUpdateUser(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateUserInput) =>
      api<UserResponse>(`/api/v1/users/${id}`, { method: 'PUT', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}
