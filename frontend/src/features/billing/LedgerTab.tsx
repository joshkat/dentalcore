import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
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

const typeLabel: Record<LedgerEntryType, string> = {
  CHARGE: 'Charge',
  PAYMENT: 'Payment',
  INSURANCE_PAYMENT: 'Ins. payment',
  ADJUSTMENT: 'Adjustment',
};

const money = (n: number) =>
  `${n < 0 ? '−' : ''}$${Math.abs(n).toFixed(2)}`;

type FormMode = 'payment' | 'charge' | 'adjustment' | null;

export function LedgerTab({ patientId }: { patientId: string }) {
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

  if (isPending || !ledger) return <Spinner label="Loading ledger…" />;

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
            {familyView ? 'Patient view' : 'Family view'}
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
            Account balance
          </p>
          <p
            className={`text-2xl font-bold ${
              owes ? 'text-red-600' : ledger.balance < 0 ? 'text-green-600' : 'text-gray-900'
            }`}
          >
            {money(ledger.balance)}
          </p>
          <p className="text-xs text-gray-500">
            {owes
              ? 'Patient owes'
              : ledger.balance < 0
                ? 'Credit on account'
                : 'Settled'}
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
                  setError('Statement download failed');
                }
              }}
            >
              Statement (PDF)
            </Button>
          )}
          {canTakePayment && (
            <Button onClick={() => setMode(mode === 'payment' ? null : 'payment')}>
              Record payment
            </Button>
          )}
          {canBill && (
            <>
              <Button
                variant="secondary"
                onClick={() => setMode(mode === 'charge' ? null : 'charge')}
              >
                Add charge
              </Button>
              <Button
                variant="secondary"
                onClick={() => setMode(mode === 'adjustment' ? null : 'adjustment')}
              >
                Adjustment
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
        <p className="text-sm text-gray-500">No ledger activity yet.</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">Date</th>
              <th className="py-2 pr-3">Type</th>
              <th className="py-2 pr-3">Description</th>
              <th className="py-2 pr-3 text-right">Amount</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {ledger.content.map((entry) => (
              <tr key={entry.id} className={entry.reversed ? 'opacity-50' : ''}>
                <td className="py-2 pr-3 whitespace-nowrap text-gray-600">{entry.entryDate}</td>
                <td className="py-2 pr-3">
                  <Badge tone={typeTone[entry.type]}>{typeLabel[entry.type]}</Badge>
                  {entry.reversalOf && (
                    <span className="ml-1 text-xs text-gray-400">(reversal)</span>
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
                  {money(entry.amount)}
                </td>
                <td className="py-2 text-right">
                  {canBill && !entry.reversed && !entry.reversalOf && (
                    <button
                      onClick={async () => {
                        const reason = window.prompt('Reversal reason?');
                        if (!reason) return;
                        setError(null);
                        try {
                          await reverseEntry.mutateAsync({ entryId: entry.id, reason });
                        } catch (e) {
                          setError(e instanceof ApiError ? e.message : 'Reversal failed');
                        }
                      }}
                      className="text-xs text-red-600 hover:underline"
                    >
                      Reverse
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
            Page {ledger.page + 1} of {ledger.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={ledger.page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={ledger.page + 1 >= ledger.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
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
  const recordPayment = useRecordPayment();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CARD');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return setError('Enter a positive amount');
    setError(null);
    try {
      await recordPayment.mutateAsync({ patientId, amount: value, method });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to record payment');
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
        label="Amount ($)"
        type="number"
        min="0.01"
        step="0.01"
        className="w-36"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <div>
        <label htmlFor="pay-method" className="block text-sm font-medium text-gray-700">
          Method
        </label>
        <select
          id="pay-method"
          value={method}
          onChange={(e) => setMethod(e.target.value)}
          className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        >
          <option value="CARD">Card</option>
          <option value="CASH">Cash</option>
          <option value="CHECK">Check</option>
          <option value="OTHER">Other</option>
        </select>
      </div>
      <Button onClick={submit} loading={recordPayment.isPending}>
        Record payment
      </Button>
      <Button variant="secondary" onClick={onDone}>
        Cancel
      </Button>
    </div>
  );
}

function ChargeForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const postCharge = usePostCharge();
  const [codeSearch, setCodeSearch] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  const submitManual = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return setError('Enter a positive amount');
    if (!description.trim()) return setError('Enter a description');
    setError(null);
    try {
      await postCharge.mutateAsync({ patientId, amount: value, description: description.trim() });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to post charge');
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
          From procedure (uses catalog fee)
        </label>
        <input
          id="charge-code"
          type="search"
          value={codeSearch}
          onChange={(e) => setCodeSearch(e.target.value)}
          placeholder="Search catalog…"
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
                  <span className="font-mono">{entry.code}</span> — {entry.description} ($
                  {entry.standardFee.toFixed(2)})
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="text-xs text-gray-500">or post a manual charge:</p>
      <div className="flex flex-wrap items-end gap-3">
        <Input
          label="Amount ($)"
          type="number"
          min="0.01"
          step="0.01"
          className="w-36"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <Input
          label="Description"
          className="min-w-64 flex-1"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Button onClick={submitManual} loading={postCharge.isPending}>
          Post charge
        </Button>
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </div>
  );
}

function AdjustmentForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const postAdjustment = usePostAdjustment();
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const value = Number(amount);
    if (!value) return setError('Enter a non-zero amount (negative = credit)');
    if (!description.trim()) return setError('Enter a description');
    setError(null);
    try {
      await postAdjustment.mutateAsync({
        patientId,
        amount: value,
        description: description.trim(),
      });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to post adjustment');
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
        label="Amount ($, negative = credit)"
        type="number"
        step="0.01"
        className="w-48"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <Input
        label="Description"
        className="min-w-64 flex-1"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <Button onClick={submit} loading={postAdjustment.isPending}>
        Post adjustment
      </Button>
      <Button variant="secondary" onClick={onDone}>
        Cancel
      </Button>
    </div>
  );
}
