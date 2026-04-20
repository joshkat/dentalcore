import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { ApiError } from '../../lib/api';
import { useProviders } from '../providers/api';
import { patientSchema, type PatientFormValues } from './schemas';

interface PatientFormProps {
  defaultValues: PatientFormValues;
  submitLabel: string;
  onSubmit: (values: PatientFormValues) => Promise<void>;
  onCancel: () => void;
}

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

export function PatientForm({ defaultValues, submitLabel, onSubmit, onCancel }: PatientFormProps) {
  const [serverError, setServerError] = useState<string | null>(null);
  const { data: providers } = useProviders(false);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<PatientFormValues>({
    resolver: zodResolver(patientSchema),
    defaultValues,
  });
  const phones = useFieldArray({ control, name: 'phones' });

  const submit = async (values: PatientFormValues) => {
    setServerError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Failed to save patient');
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-6">
      {serverError && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {serverError}
        </div>
      )}

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          Demographics
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input label="First name" error={errors.firstName?.message} {...register('firstName')} />
          <Input label="Middle name" error={errors.middleName?.message} {...register('middleName')} />
          <Input label="Last name" error={errors.lastName?.message} {...register('lastName')} />
          <Input
            label="Date of birth"
            type="date"
            error={errors.dateOfBirth?.message}
            {...register('dateOfBirth')}
          />
          <div>
            <label htmlFor="patient-sex" className="block text-sm font-medium text-gray-700">
              Sex
            </label>
            <select id="patient-sex" className={selectClass} {...register('sex')}>
              <option value="UNKNOWN">Unknown</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
          <Input
            label="Preferred language"
            error={errors.preferredLanguage?.message}
            {...register('preferredLanguage')}
          />
          <Input
            label="Preferred name"
            error={errors.preferredName?.message}
            {...register('preferredName')}
          />
          <Input label="Pronouns" error={errors.pronouns?.message} {...register('pronouns')} />
          <div>
            <label htmlFor="patient-smoking" className="block text-sm font-medium text-gray-700">
              Smoking status
            </label>
            <select id="patient-smoking" className={selectClass} {...register('smokingStatus')}>
              <option value="UNKNOWN">Unknown</option>
              <option value="NEVER">Never</option>
              <option value="FORMER">Former</option>
              <option value="CURRENT">Current</option>
            </select>
          </div>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          Contact preferences & consent
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label htmlFor="patient-contact" className="block text-sm font-medium text-gray-700">
              Preferred contact method
            </label>
            <select
              id="patient-contact"
              className={selectClass}
              {...register('preferredContactMethod')}
            >
              <option value="">Not set</option>
              <option value="PHONE">Phone</option>
              <option value="SMS">Text message</option>
              <option value="EMAIL">Email</option>
              <option value="MAIL">Mail</option>
            </select>
          </div>
          <label className="mt-7 flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 text-brand-600"
              {...register('smsConsent')}
            />
            Consents to SMS
          </label>
          <label className="mt-7 flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 text-brand-600"
              {...register('emailConsent')}
            />
            Consents to email
          </label>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          Work, referral & care team
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input label="Employer" error={errors.employer?.message} {...register('employer')} />
          <Input label="Occupation" error={errors.occupation?.message} {...register('occupation')} />
          <Input
            label="Referral source"
            error={errors.referralSource?.message}
            {...register('referralSource')}
          />
          <div>
            <label htmlFor="patient-provider" className="block text-sm font-medium text-gray-700">
              Primary provider
            </label>
            <select id="patient-provider" className={selectClass} {...register('primaryProviderId')}>
              <option value="">Not assigned</option>
              {providers?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.lastName}, {p.firstName} ({p.type})
                </option>
              ))}
            </select>
          </div>
          <Input
            label="Pharmacy name"
            error={errors.pharmacyName?.message}
            {...register('pharmacyName')}
          />
          <Input
            label="Pharmacy phone"
            error={errors.pharmacyPhone?.message}
            {...register('pharmacyPhone')}
          />
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">Phones</h3>
        <div className="mt-3 space-y-3">
          {phones.fields.map((field, index) => (
            <div key={field.id} className="flex flex-wrap items-end gap-3">
              <div>
                <label
                  htmlFor={`phone-type-${index}`}
                  className="block text-sm font-medium text-gray-700"
                >
                  Type
                </label>
                <select
                  id={`phone-type-${index}`}
                  className={selectClass}
                  {...register(`phones.${index}.type`)}
                >
                  <option value="MOBILE">Mobile</option>
                  <option value="HOME">Home</option>
                  <option value="WORK">Work</option>
                </select>
              </div>
              <Input
                label="Number"
                className="min-w-48"
                error={errors.phones?.[index]?.number?.message}
                {...register(`phones.${index}.number`)}
              />
              <label className="mb-2 flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-gray-300 text-brand-600"
                  {...register(`phones.${index}.primary`)}
                />
                Primary
              </label>
              <Button type="button" variant="ghost" onClick={() => phones.remove(index)}>
                Remove
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            onClick={() => phones.append({ type: 'MOBILE', number: '', primary: false })}
          >
            Add phone
          </Button>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">Address</h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label="Address line 1"
            className="sm:col-span-2"
            error={errors.addressLine1?.message}
            {...register('addressLine1')}
          />
          <Input label="Address line 2" error={errors.addressLine2?.message} {...register('addressLine2')} />
          <Input label="City" error={errors.city?.message} {...register('city')} />
          <Input label="State" error={errors.state?.message} {...register('state')} />
          <Input label="Postal code" error={errors.postalCode?.message} {...register('postalCode')} />
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          Emergency contact
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label="Name"
            error={errors.emergencyContactName?.message}
            {...register('emergencyContactName')}
          />
          <Input
            label="Phone"
            error={errors.emergencyContactPhone?.message}
            {...register('emergencyContactPhone')}
          />
          <Input
            label="Relationship"
            error={errors.emergencyContactRelationship?.message}
            {...register('emergencyContactRelationship')}
          />
        </div>
      </section>

      <section>
        <label htmlFor="patient-notes" className="block text-sm font-medium text-gray-700">
          Notes
        </label>
        <textarea
          id="patient-notes"
          rows={3}
          className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600"
          {...register('notes')}
        />
      </section>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
