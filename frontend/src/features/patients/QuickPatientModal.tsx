import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import type { Patient } from '../../types/api';
import { useCreatePatient } from './api';
import { emptyPatient } from './schemas';

const quickSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  dateOfBirth: z
    .string()
    .min(1, 'Date of birth is required')
    .refine((value) => new Date(value) < new Date(), 'Date of birth must be in the past'),
  sex: z.enum(['MALE', 'FEMALE', 'OTHER', 'UNKNOWN']),
  phone: z
    .string()
    .regex(/^[0-9+()\-. ]{7,30}$/, 'Enter a valid phone number')
    .optional()
    .or(z.literal('')),
});

type QuickForm = z.infer<typeof quickSchema>;

interface QuickPatientModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (patient: Patient) => void;
  initialName?: string;
}

export function QuickPatientModal({ open, onClose, onCreated, initialName }: QuickPatientModalProps) {
  return (
    <Modal title="New patient" open={open} onClose={onClose}>
      {open && <QuickForm onClose={onClose} onCreated={onCreated} initialName={initialName} />}
    </Modal>
  );
}

function QuickForm({
  onClose,
  onCreated,
  initialName,
}: Omit<QuickPatientModalProps, 'open'>) {
  const createPatient = useCreatePatient();
  const [serverError, setServerError] = useState<string | null>(null);

  // "Lastname, Firstname" or "Firstname Lastname" guesses from the search box
  const guess = (initialName ?? '').trim();
  const [guessedFirst, guessedLast] = guess.includes(',')
    ? [guess.split(',')[1]?.trim() ?? '', guess.split(',')[0].trim()]
    : [guess.split(' ')[0] ?? '', guess.split(' ').slice(1).join(' ')];

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<QuickForm>({
    resolver: zodResolver(quickSchema),
    defaultValues: {
      firstName: guessedFirst,
      lastName: guessedLast,
      dateOfBirth: '',
      sex: 'UNKNOWN',
      phone: '',
    },
  });

  const onSubmit = async (values: QuickForm) => {
    setServerError(null);
    try {
      const patient = await createPatient.mutateAsync({
        ...emptyPatient,
        firstName: values.firstName,
        lastName: values.lastName,
        dateOfBirth: values.dateOfBirth,
        sex: values.sex,
        phones: values.phone
          ? [{ type: 'MOBILE', number: values.phone, primary: true }]
          : [],
      });
      onCreated(patient);
      onClose();
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Failed to create patient');
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
        <Input label="First name" error={errors.firstName?.message} {...register('firstName')} />
        <Input label="Last name" error={errors.lastName?.message} {...register('lastName')} />
        <Input
          label="Date of birth"
          type="date"
          error={errors.dateOfBirth?.message}
          {...register('dateOfBirth')}
        />
        <div>
          <label htmlFor="quick-sex" className="block text-sm font-medium text-gray-700">
            Sex
          </label>
          <select
            id="quick-sex"
            className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            {...register('sex')}
          >
            <option value="UNKNOWN">Unknown</option>
            <option value="FEMALE">Female</option>
            <option value="MALE">Male</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <Input
          label="Mobile phone (optional)"
          className="col-span-2"
          error={errors.phone?.message}
          {...register('phone')}
        />
      </div>
      <p className="text-xs text-gray-500">
        Full demographics can be completed later on the patient's profile.
      </p>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          Create patient
        </Button>
      </div>
    </form>
  );
}
