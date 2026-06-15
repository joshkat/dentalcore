import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import { ALL_ROLES, type Role, type UserResponse } from '../../types/api';
import { makePasswordRules, type Translate } from '../auth/schemas';
import { useCreateUser, useUpdateUser } from './api';

const makeBaseFields = (t: Translate) => ({
  firstName: z.string().min(1, t('users:validation.firstNameRequired')).max(100),
  lastName: z.string().min(1, t('users:validation.lastNameRequired')).max(100),
  roles: z.array(z.enum(ALL_ROLES as [Role, ...Role[]])).min(1, t('users:validation.rolesRequired')),
});

const makeCreateSchema = (t: Translate) =>
  z.object({
    ...makeBaseFields(t),
    email: z.string().min(1, t('users:validation.emailRequired')).email(t('users:validation.emailInvalid')),
    password: makePasswordRules(t),
  });

const makeEditSchema = (t: Translate) =>
  z.object({
    ...makeBaseFields(t),
    status: z.enum(['ACTIVE', 'DISABLED']),
  });

type CreateForm = z.infer<ReturnType<typeof makeCreateSchema>>;
type EditForm = z.infer<ReturnType<typeof makeEditSchema>>;

interface UserFormModalProps {
  open: boolean;
  onClose: () => void;
  user: UserResponse | null;
}

export function UserFormModal({ open, onClose, user }: UserFormModalProps) {
  const { t } = useTranslation('users');
  return (
    <Modal title={user ? t('editUser') : t('newUser')} open={open} onClose={onClose}>
      {user ? (
        <EditUserForm user={user} onClose={onClose} />
      ) : (
        <CreateUserForm onClose={onClose} />
      )}
    </Modal>
  );
}

function RoleCheckboxes({
  selected,
  toggle,
  error,
}: {
  selected: Role[];
  toggle: (role: Role) => void;
  error?: string;
}) {
  const { t } = useTranslation('users');
  return (
    <fieldset>
      <legend className="block text-sm font-medium text-gray-700">{t('rolesLegend')}</legend>
      <div className="mt-2 grid grid-cols-2 gap-2">
        {ALL_ROLES.map((role) => (
          <label key={role} className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={selected.includes(role)}
              onChange={() => toggle(role)}
              className="h-4 w-4 rounded border-gray-300 text-brand-600 focus:ring-brand-600"
            />
            {t(`role.${role}`)}
          </label>
        ))}
      </div>
      {error && (
        <p role="alert" className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </fieldset>
  );
}

function CreateUserForm({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation('users');
  const createUser = useCreateUser();
  const [serverError, setServerError] = useState<string | null>(null);
  const createSchema = useMemo(() => makeCreateSchema(t), [t]);
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { roles: [] },
  });

  const roles = watch('roles');
  const toggleRole = (role: Role) =>
    setValue(
      'roles',
      roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role],
      { shouldValidate: true },
    );

  const onSubmit = async (data: CreateForm) => {
    setServerError(null);
    try {
      await createUser.mutateAsync(data);
      onClose();
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : t('createFailed'));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      {serverError && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {serverError}
        </div>
      )}
      <Input label={t('emailLabel')} type="email" error={errors.email?.message} {...register('email')} />
      <div className="grid grid-cols-2 gap-4">
        <Input label={t('firstNameLabel')} error={errors.firstName?.message} {...register('firstName')} />
        <Input label={t('lastNameLabel')} error={errors.lastName?.message} {...register('lastName')} />
      </div>
      <Input
        label={t('temporaryPasswordLabel')}
        type="password"
        autoComplete="new-password"
        error={errors.password?.message}
        {...register('password')}
      />
      <RoleCheckboxes selected={roles} toggle={toggleRole} error={errors.roles?.message} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('cancel')}
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {t('createUser')}
        </Button>
      </div>
    </form>
  );
}

function EditUserForm({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const { t } = useTranslation('users');
  const updateUser = useUpdateUser(user.id);
  const [serverError, setServerError] = useState<string | null>(null);
  const editSchema = useMemo(() => makeEditSchema(t), [t]);
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<EditForm>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      firstName: user.firstName,
      lastName: user.lastName,
      roles: user.roles,
      status: user.status,
    },
  });

  const roles = watch('roles');
  const toggleRole = (role: Role) =>
    setValue(
      'roles',
      roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role],
      { shouldValidate: true },
    );

  const onSubmit = async (data: EditForm) => {
    setServerError(null);
    try {
      await updateUser.mutateAsync(data);
      onClose();
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : t('updateFailed'));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      {serverError && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {serverError}
        </div>
      )}
      <div className="grid grid-cols-2 gap-4">
        <Input label={t('firstNameLabel')} error={errors.firstName?.message} {...register('firstName')} />
        <Input label={t('lastNameLabel')} error={errors.lastName?.message} {...register('lastName')} />
      </div>
      <div>
        <label htmlFor="user-status" className="block text-sm font-medium text-gray-700">
          {t('statusLabel')}
        </label>
        <select
          id="user-status"
          className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600"
          {...register('status')}
        >
          <option value="ACTIVE">{t('status.ACTIVE')}</option>
          <option value="DISABLED">{t('status.DISABLED')}</option>
        </select>
      </div>
      <RoleCheckboxes selected={roles} toggle={toggleRole} error={errors.roles?.message} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('cancel')}
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {t('saveChanges')}
        </Button>
      </div>
    </form>
  );
}
