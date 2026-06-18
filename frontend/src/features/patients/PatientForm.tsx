import { useMemo, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { ApiError } from '../../lib/api';
import { useProviders } from '../providers/api';
import { makePatientSchema, type PatientFormValues } from './schemas';

interface PatientFormProps {
  defaultValues: PatientFormValues;
  submitLabel: string;
  onSubmit: (values: PatientFormValues) => Promise<void>;
  onCancel: () => void;
}

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

export function PatientForm({ defaultValues, submitLabel, onSubmit, onCancel }: PatientFormProps) {
  const { t } = useTranslation('patients');
  const [serverError, setServerError] = useState<string | null>(null);
  const { data: providers } = useProviders(false);
  const patientSchema = useMemo(() => makePatientSchema(t), [t]);
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
      setServerError(error instanceof ApiError ? error.message : t('form.saveFailed'));
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
          {t('form.demographics')}
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label={t('form.firstName')}
            error={errors.firstName?.message}
            {...register('firstName')}
          />
          <Input
            label={t('form.middleName')}
            error={errors.middleName?.message}
            {...register('middleName')}
          />
          <Input
            label={t('form.lastName')}
            error={errors.lastName?.message}
            {...register('lastName')}
          />
          <Input
            label={t('form.dateOfBirth')}
            type="date"
            error={errors.dateOfBirth?.message}
            {...register('dateOfBirth')}
          />
          <div>
            <label htmlFor="patient-sex" className="block text-sm font-medium text-gray-700">
              {t('form.sex')}
            </label>
            <select id="patient-sex" className={selectClass} {...register('sex')}>
              <option value="UNKNOWN">{t('sexOption.UNKNOWN')}</option>
              <option value="FEMALE">{t('sexOption.FEMALE')}</option>
              <option value="MALE">{t('sexOption.MALE')}</option>
              <option value="OTHER">{t('sexOption.OTHER')}</option>
            </select>
          </div>
          <Input
            label={t('form.email')}
            type="email"
            error={errors.email?.message}
            {...register('email')}
          />
          <Input
            label={t('form.preferredLanguage')}
            error={errors.preferredLanguage?.message}
            {...register('preferredLanguage')}
          />
          <Input
            label={t('form.preferredName')}
            error={errors.preferredName?.message}
            {...register('preferredName')}
          />
          <Input
            label={t('form.pronouns')}
            error={errors.pronouns?.message}
            {...register('pronouns')}
          />
          <div>
            <label htmlFor="patient-smoking" className="block text-sm font-medium text-gray-700">
              {t('form.smokingStatus')}
            </label>
            <select id="patient-smoking" className={selectClass} {...register('smokingStatus')}>
              <option value="UNKNOWN">{t('form.smokingOption.UNKNOWN')}</option>
              <option value="NEVER">{t('form.smokingOption.NEVER')}</option>
              <option value="FORMER">{t('form.smokingOption.FORMER')}</option>
              <option value="CURRENT">{t('form.smokingOption.CURRENT')}</option>
            </select>
          </div>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('form.contactConsent')}
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label htmlFor="patient-contact" className="block text-sm font-medium text-gray-700">
              {t('form.preferredContactMethod')}
            </label>
            <select
              id="patient-contact"
              className={selectClass}
              {...register('preferredContactMethod')}
            >
              <option value="">{t('form.notSet')}</option>
              <option value="PHONE">{t('form.contactOption.PHONE')}</option>
              <option value="SMS">{t('form.contactOption.SMS')}</option>
              <option value="EMAIL">{t('form.contactOption.EMAIL')}</option>
              <option value="MAIL">{t('form.contactOption.MAIL')}</option>
            </select>
          </div>
          <label className="mt-7 flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 text-brand-600"
              {...register('smsConsent')}
            />
            {t('form.smsConsent')}
          </label>
          <label className="mt-7 flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 text-brand-600"
              {...register('emailConsent')}
            />
            {t('form.emailConsent')}
          </label>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('form.workReferral')}
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label={t('form.employer')}
            error={errors.employer?.message}
            {...register('employer')}
          />
          <Input
            label={t('form.occupation')}
            error={errors.occupation?.message}
            {...register('occupation')}
          />
          <Input
            label={t('form.referralSource')}
            error={errors.referralSource?.message}
            {...register('referralSource')}
          />
          <div>
            <label htmlFor="patient-provider" className="block text-sm font-medium text-gray-700">
              {t('form.primaryProvider')}
            </label>
            <select id="patient-provider" className={selectClass} {...register('primaryProviderId')}>
              <option value="">{t('form.notAssigned')}</option>
              {providers?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.lastName}, {p.firstName} ({p.type})
                </option>
              ))}
            </select>
          </div>
          <Input
            label={t('form.pharmacyName')}
            error={errors.pharmacyName?.message}
            {...register('pharmacyName')}
          />
          <Input
            label={t('form.pharmacyPhone')}
            error={errors.pharmacyPhone?.message}
            {...register('pharmacyPhone')}
          />
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('form.phones')}
        </h3>
        <div className="mt-3 space-y-3">
          {phones.fields.map((field, index) => (
            <div key={field.id} className="flex flex-wrap items-end gap-3">
              <div>
                <label
                  htmlFor={`phone-type-${index}`}
                  className="block text-sm font-medium text-gray-700"
                >
                  {t('form.type')}
                </label>
                <select
                  id={`phone-type-${index}`}
                  className={selectClass}
                  {...register(`phones.${index}.type`)}
                >
                  <option value="MOBILE">{t('form.phoneTypeOption.MOBILE')}</option>
                  <option value="HOME">{t('form.phoneTypeOption.HOME')}</option>
                  <option value="WORK">{t('form.phoneTypeOption.WORK')}</option>
                </select>
              </div>
              <Input
                label={t('form.number')}
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
                {t('form.primary')}
              </label>
              <Button type="button" variant="ghost" onClick={() => phones.remove(index)}>
                {t('form.remove')}
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            onClick={() => phones.append({ type: 'MOBILE', number: '', primary: false })}
          >
            {t('form.addPhone')}
          </Button>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('form.address')}
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label={t('form.addressLine1')}
            className="sm:col-span-2"
            error={errors.addressLine1?.message}
            {...register('addressLine1')}
          />
          <Input
            label={t('form.addressLine2')}
            error={errors.addressLine2?.message}
            {...register('addressLine2')}
          />
          <Input label={t('form.city')} error={errors.city?.message} {...register('city')} />
          <Input label={t('form.state')} error={errors.state?.message} {...register('state')} />
          <Input
            label={t('form.postalCode')}
            error={errors.postalCode?.message}
            {...register('postalCode')}
          />
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          {t('form.emergencyContact')}
        </h3>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Input
            label={t('form.name')}
            error={errors.emergencyContactName?.message}
            {...register('emergencyContactName')}
          />
          <Input
            label={t('form.phone')}
            error={errors.emergencyContactPhone?.message}
            {...register('emergencyContactPhone')}
          />
          <Input
            label={t('form.relationship')}
            error={errors.emergencyContactRelationship?.message}
            {...register('emergencyContactRelationship')}
          />
        </div>
      </section>

      <section>
        <label htmlFor="patient-notes" className="block text-sm font-medium text-gray-700">
          {t('form.notes')}
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
          {t('common:cancel')}
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
