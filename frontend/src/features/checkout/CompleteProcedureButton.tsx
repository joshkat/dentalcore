import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { ApiError } from '../../lib/api';
import { useProviders } from '../providers/api';
import { useTreatmentPlan } from '../treatment-plans/api';
import { useCompleteProcedure } from './api';

const inputClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

interface CompleteProcedureButtonProps {
  patientId: string;
  /** Known up front for plan rows / ad-hoc work. Omit for chart rows (resolved via planId). */
  procedureCodeId?: string;
  /** Chart rows only expose plannedProcedureId — resolve the code + fee from this plan. */
  planId?: string;
  plannedProcedureId?: string;
  tooth?: string | null;
  surfaces?: string | null;
  defaultProviderId?: string | null;
  defaultFee?: number | null;
}

/** Small "Complete" action with a confirm popover (provider + fee) that posts a completed procedure. */
export function CompleteProcedureButton(props: CompleteProcedureButtonProps) {
  const { t } = useTranslation('checkout');
  const [open, setOpen] = useState(false);
  return (
    <span className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="text-xs font-medium text-brand-600 hover:underline"
      >
        {t('complete')}
      </button>
      {open && <CompletePopover {...props} onClose={() => setOpen(false)} />}
    </span>
  );
}

function CompletePopover({
  patientId,
  procedureCodeId,
  planId,
  plannedProcedureId,
  tooth,
  surfaces,
  defaultProviderId,
  defaultFee,
  onClose,
}: CompleteProcedureButtonProps & { onClose: () => void }) {
  const { t } = useTranslation('checkout');
  const { data: providers } = useProviders(false);
  // Chart rows don't carry the catalog code id — pull it from the owning plan.
  const { data: plan } = useTreatmentPlan(
    !procedureCodeId && planId !== undefined ? planId : null,
  );
  const planned = plan?.procedures.find((p) => p.id === plannedProcedureId);
  const resolvedCodeId = procedureCodeId ?? planned?.procedureCodeId;
  const prefillFee = defaultFee ?? planned?.estimatedCost ?? null;

  const completeProcedure = useCompleteProcedure();
  const [providerOverride, setProviderOverride] = useState<string | null>(null);
  const [feeOverride, setFeeOverride] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const providerId = providerOverride ?? defaultProviderId ?? '';
  const fee = feeOverride ?? (prefillFee != null ? prefillFee.toFixed(2) : '');

  const submit = async () => {
    if (!resolvedCodeId) return setError(t('errors.procedureStillLoading'));
    if (!providerId) return setError(t('errors.selectProvider'));
    const feeValue = Number(fee);
    if (fee.trim() === '' || Number.isNaN(feeValue) || feeValue < 0) {
      return setError(t('errors.enterValidFee'));
    }
    setError(null);
    try {
      await completeProcedure.mutateAsync({
        patientId,
        providerId,
        procedureCodeId: resolvedCodeId,
        plannedProcedureId,
        tooth: tooth ?? undefined,
        surfaces: surfaces ?? undefined,
        feeOverride: feeValue,
      });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('errors.failedToCompleteProcedure'));
    }
  };

  return (
    <div className="absolute right-0 top-full z-20 mt-1 w-64 space-y-2 rounded-md bg-white p-3 text-left shadow-lg ring-1 ring-gray-200">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
        {tooth
          ? t('completeProcedureToothHeading', { tooth })
          : t('completeProcedureHeading')}
      </p>
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-xs text-red-700">
          {error}
        </p>
      )}
      <div>
        <label
          htmlFor={`complete-provider-${plannedProcedureId ?? resolvedCodeId}`}
          className="block text-xs font-medium text-gray-700"
        >
          {t('provider')}
        </label>
        <select
          id={`complete-provider-${plannedProcedureId ?? resolvedCodeId}`}
          value={providerId}
          onChange={(e) => setProviderOverride(e.target.value)}
          className={inputClass}
        >
          <option value="">{t('select')}</option>
          {providers?.content.map((p) => (
            <option key={p.id} value={p.id}>
              {p.lastName}, {p.firstName}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label
          htmlFor={`complete-fee-${plannedProcedureId ?? resolvedCodeId}`}
          className="block text-xs font-medium text-gray-700"
        >
          {t('feeLabel')}
        </label>
        <input
          id={`complete-fee-${plannedProcedureId ?? resolvedCodeId}`}
          type="number"
          min="0"
          step="0.01"
          value={fee}
          onChange={(e) => setFeeOverride(e.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="secondary" onClick={onClose}>
          {t('common:cancel')}
        </Button>
        <Button onClick={submit} loading={completeProcedure.isPending}>
          {t('complete')}
        </Button>
      </div>
    </div>
  );
}
