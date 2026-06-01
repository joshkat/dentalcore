import { useMemo, useState } from 'react';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { ALL_ROLES, type Role } from '../../types/api';
import { usePermissionMatrix, useUpdateRolePermissions } from './api';

/**
 * Permissions ADMIN can never lose — the server rejects these revocations (400),
 * so the checkboxes are disabled up front.
 */
const LOCKED_ADMIN_PERMISSIONS = new Set(['PERMISSIONS_MANAGE', 'USERS_MANAGE']);

export function prettifyCode(code: string): string {
  const lower = code.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function sameSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  const set = new Set(a);
  return b.every((code) => set.has(code));
}

export function PermissionMatrixSection() {
  const { data, isPending, isError } = usePermissionMatrix();
  const updateRole = useUpdateRolePermissions();
  // Per-role draft grants; a role is "dirty" when its draft differs from the server state.
  const [drafts, setDrafts] = useState<Partial<Record<Role, string[]>>>({});
  const [savingRole, setSavingRole] = useState<Role | null>(null);
  const [error, setError] = useState<string | null>(null);

  const grouped = useMemo(() => {
    const permissions = data?.permissions ?? [];
    const byCategory = new Map<string, typeof permissions>();
    for (const permission of permissions) {
      const list = byCategory.get(permission.category) ?? [];
      list.push(permission);
      byCategory.set(permission.category, list);
    }
    return [...byCategory.entries()];
  }, [data]);

  if (isPending) return <Spinner label="Loading permissions…" />;
  if (isError || !data)
    return (
      <p role="alert" className="text-sm text-red-600">
        Failed to load the permission matrix.
      </p>
    );

  const grantsFor = (role: Role): string[] => drafts[role] ?? data.roles[role] ?? [];
  const isDirty = (role: Role): boolean => {
    const draft = drafts[role];
    return draft !== undefined && !sameSet(draft, data.roles[role] ?? []);
  };

  const toggle = (role: Role, code: string) => {
    const current = grantsFor(role);
    const next = current.includes(code)
      ? current.filter((c) => c !== code)
      : [...current, code];
    setDrafts((d) => ({ ...d, [role]: next }));
  };

  const save = (role: Role) => {
    setError(null);
    setSavingRole(role);
    updateRole.mutate(
      { role, permissionCodes: grantsFor(role) },
      {
        onSuccess: () =>
          setDrafts((d) => {
            const { [role]: _saved, ...rest } = d;
            return rest;
          }),
        onError: (e) =>
          setError(e instanceof ApiError ? e.message : 'Failed to save permissions'),
        onSettled: () => setSavingRole(null),
      },
    );
  };

  return (
    <section aria-label="Permission matrix" className="rounded-lg bg-white p-6 shadow">
      <h2 className="text-lg font-semibold text-gray-900">Permission matrix</h2>
      <p className="mt-2 rounded-md bg-yellow-50 p-2 text-sm text-yellow-800 ring-1 ring-inset ring-yellow-600/20">
        Changes apply immediately to active sessions.
      </p>
      {error && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-4 overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">Permission</th>
              {ALL_ROLES.map((role) => (
                <th key={role} className="px-2 py-2 text-center align-top">
                  <span className="block">{role}</span>
                  {isDirty(role) && (
                    <Button
                      className="mt-1 px-2 py-1 text-xs normal-case"
                      onClick={() => save(role)}
                      loading={savingRole === role && updateRole.isPending}
                      aria-label={`Save ${role}`}
                    >
                      Save
                    </Button>
                  )}
                </th>
              ))}
            </tr>
          </thead>
          {grouped.map(([category, permissions]) => (
            <tbody key={category} className="divide-y divide-gray-100">
              <tr>
                <td
                  colSpan={1 + ALL_ROLES.length}
                  className="bg-gray-50 py-1.5 pl-2 pr-3 text-xs font-semibold uppercase tracking-wide text-gray-500"
                >
                  {category}
                </td>
              </tr>
              {permissions.map((permission) => (
                <tr key={permission.code}>
                  <td className="py-2 pr-3">
                    <p className="font-medium text-gray-900" title={permission.description}>
                      {prettifyCode(permission.code)}
                    </p>
                    <p className="text-xs text-gray-500">{permission.description}</p>
                  </td>
                  {ALL_ROLES.map((role) => {
                    const locked =
                      role === 'ADMIN' && LOCKED_ADMIN_PERMISSIONS.has(permission.code);
                    return (
                      <td key={role} className="px-2 py-2 text-center">
                        <input
                          type="checkbox"
                          className="h-4 w-4 rounded border-gray-300 text-brand-600 focus:ring-brand-600 disabled:opacity-40"
                          aria-label={`${permission.code} ${role}`}
                          data-role={role}
                          data-code={permission.code}
                          checked={grantsFor(role).includes(permission.code)}
                          disabled={locked}
                          title={
                            locked
                              ? 'ADMIN always keeps this permission; the server refuses to revoke it.'
                              : undefined
                          }
                          onChange={() => toggle(role, permission.code)}
                        />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          ))}
        </table>
      </div>
    </section>
  );
}
