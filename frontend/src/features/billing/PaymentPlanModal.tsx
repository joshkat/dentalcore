import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { formatDate, formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useCreatePaymentPlan, type PaymentPlanFrequency } from './api';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
};

export function PaymentPlanModal({
  patientId,
  open,
  onClose,
}: {
  patientId: string;
  open: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation('billing');
  const createPlan = useCreatePaymentPlan();
  const [total, setTotal] = useState('');
  const [down, setDown] = useState('');
  const [installment, setInstallment] = useState('');
  const [frequency, setFrequency] = useState<PaymentPlanFrequency>('MONTHLY');
  const [firstDueDate, setFirstDueDate] = useState(todayIso());
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);

  const totalValue = Number(total);
  const downValue = down.trim() === '' ? 0 : Number(down);
  const installmentValue = Number(installment);
  const financed = totalValue - downValue;

  // Client-side preview of the schedule the backend will generate.
  const installmentCount =
    totalValue > 0 && installmentValue > 0 && financed > 0
      ? Math.ceil(financed / installmentValue)
      : null;
  const finalAmount =
    installmentCount !== null
      ? financed - (installmentCount - 1) * installmentValue
      : null;

  const submit = async () => {
    if (!totalValue || totalValue <= 0) return setError(t('modal.enterPositiveTotal'));
    if (downValue < 0) return setError(t('modal.downNegative'));
    if (downValue >= totalValue)
      return setError(t('modal.downExceedsTotal'));
    if (!installmentValue || installmentValue <= 0)
      return setError(t('modal.enterPositiveInstallment'));
    if (installmentValue > totalValue)
      return setError(t('modal.installmentExceedsTotal'));
    if (!firstDueDate) return setError(t('modal.chooseFirstDueDate'));
    setError(null);
    try {
      await createPlan.mutateAsync({
        patientId,
        totalAmount: totalValue,
        downPayment: downValue > 0 ? downValue : undefined,
        installmentAmount: installmentValue,
        frequency,
        firstDueDate,
        notes: notes.trim() || undefined,
      });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('modal.failedCreate'));
    }
  };

  return (
    <Modal title={t('modal.title')} open={open} onClose={onClose}>
      <div className="space-y-3">
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <div className="grid grid-cols-2 gap-3">
          <Input
            label={t('modal.totalAmount')}
            type="number"
            min="0.01"
            step="0.01"
            value={total}
            onChange={(e) => setTotal(e.target.value)}
          />
          <Input
            label={t('modal.downPayment')}
            type="number"
            min="0"
            step="0.01"
            value={down}
            onChange={(e) => setDown(e.target.value)}
          />
          <Input
            label={t('modal.installment')}
            type="number"
            min="0.01"
            step="0.01"
            value={installment}
            onChange={(e) => setInstallment(e.target.value)}
          />
          <div>
            <label
              htmlFor="plan-frequency"
              className="block text-sm font-medium text-gray-700"
            >
              {t('modal.frequency')}
            </label>
            <select
              id="plan-frequency"
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as PaymentPlanFrequency)}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              <option value="MONTHLY">{t('modal.frequencyOption.MONTHLY')}</option>
              <option value="BIWEEKLY">{t('modal.frequencyOption.BIWEEKLY')}</option>
            </select>
          </div>
          <Input
            label={t('modal.firstDueDate')}
            type="date"
            value={firstDueDate}
            onChange={(e) => setFirstDueDate(e.target.value)}
          />
          <Input
            label={t('modal.notes')}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>

        {installmentCount !== null && finalAmount !== null && (
          <p data-testid="installment-preview" className="rounded-md bg-blue-50/60 p-3 text-sm text-gray-700">
            {t('modal.previewDown', { amount: formatMoney(downValue) })}
            <span className="font-semibold">
              {t('modal.installmentPhrase', {
                count: installmentCount,
                frequency: t(`plans.frequency.${frequency}`),
              })}
            </span>
            {t('modal.previewOf', { amount: formatMoney(installmentValue) })}
            {finalAmount !== installmentValue &&
              t('modal.previewFinal', { amount: formatMoney(finalAmount) })}
            {t('modal.previewStarting', {
              date: firstDueDate ? formatDate(firstDueDate) : firstDueDate,
            })}
          </p>
        )}

        <div className="flex gap-2">
          <Button onClick={submit} loading={createPlan.isPending}>
            Create plan
          </Button>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </div>
    </Modal>
  );
}
