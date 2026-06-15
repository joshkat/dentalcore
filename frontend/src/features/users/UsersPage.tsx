import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import type { UserResponse } from '../../types/api';
import { useUsers } from './api';
import { UserFormModal } from './UserFormModal';

export function UsersPage() {
  const { t } = useTranslation('users');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<UserResponse | null>(null);

  const { data, isPending, isError } = useUsers(search, page);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };
  const openEdit = (user: UserResponse) => {
    setEditing(user);
    setModalOpen(true);
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>
        <Button onClick={openCreate}>{t('newUser')}</Button>
      </div>

      <div className="mt-4">
        <input
          type="search"
          placeholder={t('searchPlaceholder')}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label={t('searchAriaLabel')}
          className="w-full max-w-sm rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600"
        />
      </div>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label={t('loading')} />
        ) : isError ? (
          <p className="p-8 text-sm text-red-600">{t('loadFailed')}</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500">
                  {t('colName')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500">
                  {t('colEmail')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500">
                  {t('colRoles')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500">
                  {t('colStatus')}
                </th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((user) => (
                <tr key={user.id}>
                  <td className="px-4 py-3 text-sm font-medium text-gray-900">
                    {user.lastName}, {user.firstName}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{user.email}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {user.roles.map((role) => (
                        <Badge key={role} tone="blue">
                          {t(`role.${role}`)}
                        </Badge>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {user.locked ? (
                      <Badge tone="yellow">{t('statusBadge.LOCKED')}</Badge>
                    ) : user.status === 'ACTIVE' ? (
                      <Badge tone="green">{t('statusBadge.ACTIVE')}</Badge>
                    ) : (
                      <Badge tone="red">{t('statusBadge.DISABLED')}</Badge>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEdit(user)}
                      className="text-sm text-brand-600 hover:underline"
                    >
                      {t('edit')}
                    </button>
                  </td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-500">
                    {t('empty')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
          <span>
            {t('pageInfo', {
              page: data.page + 1,
              totalPages: data.totalPages,
              count: data.totalElements,
            })}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={data.page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              {t('previous')}
            </Button>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('next')}
            </Button>
          </div>
        </div>
      )}

      <UserFormModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        user={editing}
      />
    </div>
  );
}
