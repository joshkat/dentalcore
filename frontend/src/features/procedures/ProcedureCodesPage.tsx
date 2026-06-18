import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { formatMoney } from '../../i18n/format';
import { PROCEDURE_CATEGORIES, type ProcedureCode } from '../../types/api';
import type { Translate } from '../auth/schemas';
import { useCreateProcedureCode, useProcedureCodes, useUpdateProcedureCode } from './api';

const makeSchema = (t: Translate) =>
  z.object({
    code: z.string().min(1, t('procedures:validation.codeRequired')).max(20),
    description: z.string().min(1, t('procedures:validation.descriptionRequired')).max(500),
    category: z.enum(PROCEDURE_CATEGORIES as [string, ...string[]]),
    standardFee: z.coerce.number().min(0, t('procedures:validation.feeNegative')).max(99_999_999),
    cdtCode: z.string().max(10).optional().or(z.literal('')),
    active: z.boolean(),
  });

type FormValues = z.infer<ReturnType<typeof makeSchema>>;

export function ProcedureCodesPage() {
  const { t } = useTranslation('procedures');
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProcedureCode | null>(null);
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');

  const { data, isPending, isError } = useProcedureCodes(search, isAdmin);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>
        {isAdmin && (
          <Button
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            {t('newProcedure')}
          </Button>
        )}
      </div>

      <div className="mt-4">
        <input
          type="search"
          placeholder={t('searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label={t('searchAriaLabel')}
          className="w-full max-w-md rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600"
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
                {(
                  [
                    'colCode',
                    'colDescription',
                    'colCategory',
                    'colStandardFee',
                    'colStatus',
                    null,
                  ] as const
                ).map((key, i) => (
                  <th
                    key={i}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                  >
                    {key ? t(key) : ''}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((code) => (
                <tr key={code.id}>
                  <td className="px-4 py-3 text-sm font-mono font-medium text-gray-900">
                    {code.code}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-700">{code.description}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {t(`category.${code.category}`)}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-900">
                    {formatMoney(code.standardFee)}
                  </td>
                  <td className="px-4 py-3">
                    {code.active ? (
                      <Badge tone="green">{t('statusBadge.active')}</Badge>
                    ) : (
                      <Badge tone="gray">{t('statusBadge.inactive')}</Badge>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {isAdmin && (
                      <button
                        onClick={() => {
                          setEditing(code);
                          setModalOpen(true);
                        }}
                        className="text-sm text-brand-600 hover:underline"
                      >
                        {t('edit')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-500">
                    {t('empty')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        title={editing ? t('editProcedure') : t('newProcedure')}
        open={modalOpen}
        onClose={() => setModalOpen(false)}
      >
        {modalOpen && (
          <ProcedureForm
            key={editing?.id ?? 'new'}
            code={editing}
            onClose={() => setModalOpen(false)}
          />
        )}
      </Modal>
    </div>
  );
}

function ProcedureForm({ code, onClose }: { code: ProcedureCode | null; onClose: () => void }) {
  const { t } = useTranslation('procedures');
  const createCode = useCreateProcedureCode();
  const updateCode = useUpdateProcedureCode(code?.id ?? '');
  const [serverError, setServerError] = useState<string | null>(null);
  const schema = useMemo(() => makeSchema(t), [t]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: code
      ? {
          code: code.code,
          description: code.description,
          category: code.category,
          standardFee: code.standardFee,
          cdtCode: code.cdtCode ?? '',
          active: code.active,
        }
      : { code: '', description: '', category: 'DIAGNOSTIC', standardFee: 0, cdtCode: '', active: true },
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    const input = { ...values, cdtCode: values.cdtCode || null };
    try {
      if (code) {
        await updateCode.mutateAsync(input);
      } else {
        await createCode.mutateAsync(input);
      }
      onClose();
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : t('saveFailed'));
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
        <Input label={t('codeLabel')} error={errors.code?.message} {...register('code')} />
        <Input label={t('cdtCodeLabel')} error={errors.cdtCode?.message} {...register('cdtCode')} />
        <Input
          label={t('descriptionLabel')}
          className="col-span-2"
          error={errors.description?.message}
          {...register('description')}
        />
        <div>
          <label htmlFor="proc-category" className="block text-sm font-medium text-gray-700">
            {t('categoryLabel')}
          </label>
          <select
            id="proc-category"
            className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            {...register('category')}
          >
            {PROCEDURE_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {t(`category.${c}`)}
              </option>
            ))}
          </select>
        </div>
        <Input
          label={t('standardFeeLabel')}
          type="number"
          step="0.01"
          min="0"
          error={errors.standardFee?.message}
          {...register('standardFee')}
        />
      </div>
      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          className="h-4 w-4 rounded border-gray-300 text-brand-600"
          {...register('active')}
        />
        {t('activeLabel')}
      </label>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('cancel')}
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {code ? t('saveChanges') : t('createProcedure')}
        </Button>
      </div>
    </form>
  );
}
