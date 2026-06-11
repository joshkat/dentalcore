import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { usePatient } from '../patients/api';
import { useProcedureCodes } from '../procedures/api';
import {
  downloadStatement,
  useFamilyLedger,
  useLedger,
  usePostAdjustment,
  usePostCharge,
  useRecordPayment,
  useReverseEntry,
  type LedgerEntryType,
} from './api';
import { FamilyLedgerView } from './FamilyLedgerView';
import { PaymentPlansSection } from './PaymentPlansSection';

const typeTone: Record<LedgerEntryType, 'red' | 'green' | 'blue' | 'yellow'> = {
  CHARGE: 'red',
  PAYMENT: 'green',
  INSURANCE_PAYMENT: 'blue',
  ADJUSTMENT: 'yellow',
};

type FormMode = 'payment' | 'charge' | 'adjustment' | null;

export function LedgerTab({ patientId }: { patientId: string }) {
  const { t } = useTranslation('billing');
  const [page, setPage] = useState(0);
  const [mode, setMode] = useState<FormMode>(null);
  const { hasRole } = useAuth();
  const canBill = hasRole('ADMIN', 'BILLING');
  const canTakePayment = hasRole('ADMIN', 'BILLING', 'FRONT_DESK');

  const { data: ledger, isPending } = useLedger(patientId, page);
  const reverseEntry = useReverseEntry();
  const [error, setError] = useState<string | null>(null);

  // Family view: available when the patient has a guarantor or guarantees others.
  const { data: patient } = usePatient(patientId);
  const effectiveGuarantorId = patient?.guarantorId ?? patientId;
  const { data: familyLedger } = useFamilyLedger(
    effectiveGuarantorId,
    patient !== undefined,
  );
  const [familyView, setFamilyView] = useState(false);
  const hasFamily =
    patient?.guarantorId != null || (familyLedger?.members.length ?? 0) > 1;

  if (isPending || !ledger) return <Spinner label={t('loadingLedger')} />;

  const owes = ledger.balance > 0;

  return (
    <div className="space-y-4">
      {hasFamily && (
        <div className="flex justify-end">
          <Button
            variant="secondary"
            aria-pressed={familyView}
            onClick={() => setFamilyView((v) => !v)}
          >
            {familyView ? t('patientView') : t('familyView')}
          </Button>
        </div>
      )}

      {familyView && familyLedger ? (
        <FamilyLedgerView ledger={familyLedger} currentPatientId={patientId} />
      ) : (
        <>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-gray-50 p-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            {t('accountBalance')}
          </p>
          <p
            className={`text-2xl font-bold ${
              owes ? 'text-red-600' : ledger.balance < 0 ? 'text-green-600' : 'text-gray-900'
            }`}
          >
            {formatMoney(ledger.balance)}
          </p>
          <p className="text-xs text-gray-500">
            {owes
              ? t('patientOwes')
              : ledger.balance < 0
                ? t('creditOnAccount')
                : t('settled')}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canTakePayment && (
            <Button
              variant="secondary"
              onClick={async () => {
                setError(null);
                try {
                  await downloadStatement(patientId);
                } catch {
                  setError(t('statementDownloadFailed'));
                }
              }}
            >
              {t('statementPdf')}
            </Button>
          )}
          {canTakePayment && (
            <Button onClick={() => setMode(mode === 'payment' ? null : 'payment')}>
              {t('recordPayment')}
            </Button>
          )}
          {canBill && (
            <>
              <Button
                variant="secondary"
                onClick={() => setMode(mode === 'charge' ? null : 'charge')}
              >
                {t('addCharge')}
              </Button>
              <Button
                variant="secondary"
                onClick={() => setMode(mode === 'adjustment' ? null : 'adjustment')}
              >
                {t('adjustment')}
              </Button>
            </>
          )}
        </div>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {mode === 'payment' && (
        <PaymentForm patientId={patientId} onDone={() => setMode(null)} />
      )}
      {mode === 'charge' && <ChargeForm patientId={patientId} onDone={() => setMode(null)} />}
      {mode === 'adjustment' && (
        <AdjustmentForm patientId={patientId} onDone={() => setMode(null)} />
      )}

      {ledger.content.length === 0 ? (
        <p className="text-sm text-gray-500">{t('noLedgerActivity')}</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">{t('columns.date')}</th>
              <th className="py-2 pr-3">{t('columns.type')}</th>
              <th className="py-2 pr-3">{t('columns.description')}</th>
              <th className="py-2 pr-3 text-right">{t('columns.amount')}</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {ledger.content.map((entry) => (
              <tr key={entry.id} className={entry.reversed ? 'opacity-50' : ''}>
                <td className="py-2 pr-3 whitespace-nowrap text-gray-600">
                  {formatDate(entry.entryDate)}
                </td>
                <td className="py-2 pr-3">
                  <Badge tone={typeTone[entry.type]}>{t(`type.${entry.type}`)}</Badge>
                  {entry.reversalOf && (
                    <span className="ml-1 text-xs text-gray-400">{t('reversalTag')}</span>
                  )}
                </td>
                <td className="py-2 pr-3">
                  <span className={entry.reversed ? 'line-through' : ''}>
                    {entry.description}
                  </span>
                  {entry.method && (
                    <span className="ml-1 text-xs text-gray-400">{entry.method}</span>
                  )}
                </td>
                <td
                  className={`py-2 pr-3 text-right font-medium ${
                    entry.amount < 0 ? 'text-green-700' : 'text-gray-900'
                  }`}
                >
                  {formatMoney(entry.amount)}
                </td>
                <td className="py-2 text-right">
                  {canBill && !entry.reversed && !entry.reversalOf && (
                    <button
                      onClick={async () => {
                        const reason = window.prompt(t('reversalReasonPrompt'));
                        if (!reason) return;
                        setError(null);
                        try {
                          await reverseEntry.mutateAsync({ entryId: entry.id, reason });
                        } catch (e) {
                          setError(e instanceof ApiError ? e.message : t('reversalFailed'));
                        }
                      }}
                      className="text-xs text-red-600 hover:underline"
                    >
                      {t('reverse')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {ledger.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-gray-600">
          <span>
            {t('common:pageOf', { page: ledger.page + 1, total: ledger.totalPages })}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={ledger.page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              {t('common:previous')}
            </Button>
            <Button
              variant="secondary"
              disabled={ledger.page + 1 >= ledger.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('common:next')}
            </Button>
          </div>
        </div>
      )}
        </>
      )}

      <PaymentPlansSection patientId={patientId} />
    </div>
  );
}

function PaymentForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const { t } = useTranslation('billing');
  const recordPayment = useRecordPayment();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CARD');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return setError(t('form.enterPositiveAmount'));
    setError(null);
    try {
      await recordPayment.mutateAsync({ patientId, amount: value, method });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('form.failedRecordPayment'));
    }
  };

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="w-full text-sm text-red-600">
          {error}
        </p>
      )}
      <Input
        label={t('form.amount')}
        type="number"
        min="0.01"
        step="0.01"
        className="w-36"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <div>
        <label htmlFor="pay-method" className="block text-sm font-medium text-gray-700">
          {t('form.method')}
        </label>
        <select
          id="pay-method"
          value={method}
          onChange={(e) => setMethod(e.target.value)}
          className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        >
          <option value="CARD">{t('form.methodOption.CARD')}</option>
          <option value="CASH">{t('form.methodOption.CASH')}</option>
          <option value="CHECK">{t('form.methodOption.CHECK')}</option>
          <option value="OTHER">{t('form.methodOption.OTHER')}</option>
        </select>
      </div>
      <Button onClick={submit} loading={recordPayment.isPending}>
        {t('recordPayment')}
      </Button>
      <Button variant="secondary" onClick={onDone}>
        {t('common:cancel')}
      </Button>
    </div>
  );
}

function ChargeForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const { t } = useTranslation('billing');
  const postCharge = usePostCharge();
  const [codeSearch, setCodeSearch] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  const submitManual = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return setError(t('form.enterPositiveAmount'));
    if (!description.trim()) return setError(t('form.enterDescription'));
    setError(null);
    try {
      await postCharge.mutateAsync({ patientId, amount: value, description: description.trim() });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('form.failedPostCharge'));
    }
  };

  return (
    <div className="space-y-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="max-w-md">
        <label htmlFor="charge-code" className="block text-sm font-medium text-gray-700">
          {t('form.fromProcedure')}
        </label>
        <input
          id="charge-code"
          type="search"
          value={codeSearch}
          onChange={(e) => setCodeSearch(e.target.value)}
          placeholder={t('form.searchCatalog')}
          className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        />
        {codeSearch && (
          <ul className="mt-1 max-h-32 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
            {catalog?.content.slice(0, 6).map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  onClick={async () => {
                    setError(null);
                    try {
                      await postCharge.mutateAsync({ patientId, procedureCodeId: entry.id });
                      onDone();
                    } catch (e) {
                      setError(e instanceof ApiError ? e.message : 'Failed to post charge');
                    }
                  }}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                >
                  <span className="font-mono">{entry.code}</span> — {entry.description} (
                  {formatMoney(entry.standardFee)})
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="text-xs text-gray-500">{t('form.orManualCharge')}</p>
      <div className="flex flex-wrap items-end gap-3">
        <Input
          label={t('form.amount')}
          type="number"
          min="0.01"
          step="0.01"
          className="w-36"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <Input
          label={t('form.description')}
          className="min-w-64 flex-1"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Button onClick={submitManual} loading={postCharge.isPending}>
          {t('form.postCharge')}
        </Button>
        <Button variant="secondary" onClick={onDone}>
          {t('common:cancel')}
        </Button>
      </div>
    </div>
  );
}

function AdjustmentForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const { t } = useTranslation('billing');
  const postAdjustment = usePostAdjustment();
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const value = Number(amount);
    if (!value) return setError(t('form.enterNonZeroAmount'));
    if (!description.trim()) return setError(t('form.enterDescription'));
    setError(null);
    try {
      await postAdjustment.mutateAsync({
        patientId,
        amount: value,
        description: description.trim(),
      });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('form.failedPostAdjustment'));
    }
  };

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="w-full text-sm text-red-600">
          {error}
        </p>
      )}
      <Input
        label={t('form.amountNegativeCredit')}
        type="number"
        step="0.01"
        className="w-48"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <Input
        label={t('form.description')}
        className="min-w-64 flex-1"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <Button onClick={submit} loading={postAdjustment.isPending}>
        {t('form.postAdjustment')}
      </Button>
      <Button variant="secondary" onClick={onDone}>
        {t('common:cancel')}
      </Button>
    </div>
  );
}
