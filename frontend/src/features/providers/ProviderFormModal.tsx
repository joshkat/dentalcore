import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import type { Provider } from '../../types/api';
import { useCreateProvider, useUpdateProvider } from './api';

const providerSchema = z.object({
  type: z.enum(['DENTIST', 'HYGIENIST', 'ASSISTANT']),
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  npi: z
    .string()
    .regex(/^\d{10}$/, 'NPI must be exactly 10 digits')
    .optional()
    .or(z.literal('')),
  specialty: z.string().max(100).optional().or(z.literal('')),
  licenseNumber: z.string().max(50).optional().or(z.literal('')),
  licenseState: z.string().max(50).optional().or(z.literal('')),
  email: z.string().email('Enter a valid email').max(320).optional().or(z.literal('')),
  phone: z.string().max(30).optional().or(z.literal('')),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Use a hex color like #3b82f6'),
  active: z.boolean(),
});

type ProviderFormValues = z.infer<typeof providerSchema>;

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

interface ProviderFormModalProps {
  open: boolean;
  onClose: () => void;
  provider: Provider | null;
}

export function ProviderFormModal({ open, onClose, provider }: ProviderFormModalProps) {
  return (
    <Modal title={provider ? 'Edit provider' : 'New provider'} open={open} onClose={onClose}>
      <ProviderForm key={provider?.id ?? 'new'} provider={provider} onClose={onClose} />
    </Modal>
  );
}

function ProviderForm({ provider, onClose }: { provider: Provider | null; onClose: () => void }) {
  const createProvider = useCreateProvider();
  const updateProvider = useUpdateProvider(provider?.id ?? '');
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProviderFormValues>({
    resolver: zodResolver(providerSchema),
    defaultValues: provider
      ? {
          type: provider.type,
          firstName: provider.firstName,
          lastName: provider.lastName,
          npi: provider.npi ?? '',
          specialty: provider.specialty ?? '',
          licenseNumber: provider.licenseNumber ?? '',
          licenseState: provider.licenseState ?? '',
          email: provider.email ?? '',
          phone: provider.phone ?? '',
          color: provider.color,
          active: provider.active,
        }
      : {
          type: 'DENTIST',
          firstName: '',
          lastName: '',
          npi: '',
          specialty: '',
          licenseNumber: '',
          licenseState: '',
          email: '',
          phone: '',
          color: '#3b82f6',
          active: true,
        },
  });

  const onSubmit = async (values: ProviderFormValues) => {
    setServerError(null);
    const input = {
      ...values,
      npi: values.npi || null,
      specialty: values.specialty || null,
      licenseNumber: values.licenseNumber || null,
      licenseState: values.licenseState || null,
      email: values.email || null,
      phone: values.phone || null,
    };
    try {
      if (provider) {
        await updateProvider.mutateAsync(input);
      } else {
        await createProvider.mutateAsync(input);
      }
      onClose();
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Failed to save provider');
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
        <div>
          <label htmlFor="provider-type" className="block text-sm font-medium text-gray-700">
            Type
          </label>
          <select id="provider-type" className={selectClass} {...register('type')}>
            <option value="DENTIST">Dentist</option>
            <option value="HYGIENIST">Hygienist</option>
            <option value="ASSISTANT">Assistant</option>
          </select>
        </div>
        <div>
          <label htmlFor="provider-color" className="block text-sm font-medium text-gray-700">
            Calendar color
          </label>
          <input
            id="provider-color"
            type="color"
            className="mt-1 h-9 w-full cursor-pointer rounded-md ring-1 ring-inset ring-gray-300"
            {...register('color')}
          />
          {errors.color && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {errors.color.message}
            </p>
          )}
        </div>
        <Input label="First name" error={errors.firstName?.message} {...register('firstName')} />
        <Input label="Last name" error={errors.lastName?.message} {...register('lastName')} />
        <Input label="NPI" error={errors.npi?.message} {...register('npi')} />
        <Input label="Specialty" error={errors.specialty?.message} {...register('specialty')} />
        <Input
          label="License number"
          error={errors.licenseNumber?.message}
          {...register('licenseNumber')}
        />
        <Input
          label="License state"
          error={errors.licenseState?.message}
          {...register('licenseState')}
        />
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <Input label="Phone" error={errors.phone?.message} {...register('phone')} />
      </div>
      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          className="h-4 w-4 rounded border-gray-300 text-brand-600"
          {...register('active')}
        />
        Active
      </label>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {provider ? 'Save changes' : 'Create provider'}
        </Button>
      </div>
    </form>
  );
}
