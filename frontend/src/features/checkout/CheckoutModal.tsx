import { Check } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import type { Appointment, AppointmentProcedure } from '../../types/api';
import { useUpdateAppointmentStatus } from '../appointments/api';
import { openWalkout, useRecordPayment } from '../billing/api';
import { useAdHocEstimate } from '../insurance/api';
import { usePatient } from '../patients/api';
import {
  useCompletedProcedures,
  useCompleteProcedure,
  useUndoCompletedProcedure,
  type CompletedProcedure,
} from './api';

const money = (n: number) => `$${n.toFixed(2)}`;

const inputClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

const PAYMENT_METHODS = [
  ['CARD', 'Card'],
  ['CASH', 'Cash'],
  ['CHECK', 'Check'],
  ['OTHER', 'Other'],
] as const;

interface CheckoutModalProps {
  appointment: Appointment | null;
  onClose: () => void;
}

export function CheckoutModal({ appointment, onClose }: CheckoutModalProps) {
  return (
    <Modal title="Check out" open={appointment !== null} onClose={onClose} size="lg">
      {appointment && (
        <CheckoutBody key={appointment.id} appointment={appointment} onClose={onClose} />
      )}
    </Modal>
  );
}

function CheckoutBody({
  appointment,
  onClose,
}: {
  appointment: Appointment;
  onClose: () => void;
}) {
  const patientId = appointment.patientId;
  const { data: patient } = usePatient(patientId);
  const { data: completedAll } = useCompletedProcedures(patientId);
  const completeProcedure = useCompleteProcedure();
  const undoProcedure = useUndoCompletedProcedure(patientId);
  const updateStatus = useUpdateAppointmentStatus(appointment.id);
  const recordPayment = useRecordPayment();

  const [error, setError] = useState<string | null>(null);

  // Server is the source of truth: rows completed at this visit (incl. before a reopen).
  const completedHere = useMemo(
    () => (completedAll ?? []).filter((cp) => cp.appointmentId === appointment.id),
    [completedAll, appointment.id],
  );

  const estimateItems = useMemo(
    () => completedHere.map((cp) => ({ procedureCodeId: cp.procedureCodeId, grossFee: cp.fee })),
    [completedHere],
  );
  const { data: estimate } = useAdHocEstimate(patientId, estimateItems);
  const hasEstimate = estimate?.hasCoverage === true;

  const completedTotal = completedHere.reduce((sum, cp) => sum + cp.fee, 0);
  const defaultAmount = hasEstimate ? estimate.totalPatient : completedTotal;

  // Payment state — amount tracks the suggested default until the user edits it.
  const [amountOverride, setAmountOverride] = useState<string | null>(null);
  const amount = amountOverride ?? (defaultAmount > 0 ? defaultAmount.toFixed(2) : '');
  const [method, setMethod] = useState('CARD');
  const [reference, setReference] = useState('');
  const [paidAmount, setPaidAmount] = useState<number | null>(null);

  const [finished, setFinished] = useState(appointment.status === 'COMPLETED');
  const [walkoutError, setWalkoutError] = useState(false);

  const act = async (fn: () => Promise<unknown>, fallback: string) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : fallback);
    }
  };

  const completeRow = (procedure: AppointmentProcedure, tooth: string, fee: string) => {
    const feeValue = Number(fee);
    if (fee.trim() === '' || Number.isNaN(feeValue) || feeValue < 0) {
      setError(`Enter a valid fee for ${procedure.code ?? 'the procedure'}`);
      return;
    }
    void act(
      () =>
        completeProcedure.mutateAsync({
          patientId,
          providerId: appointment.providerId,
          procedureCodeId: procedure.procedureCodeId,
          appointmentId: appointment.id,
          tooth: tooth.trim() || undefined,
          feeOverride: feeValue,
        }),
      'Failed to complete procedure',
    );
  };

  const takePayment = () => {
    const value = Number(amount);
    if (!value || value <= 0) {
      setError('Enter a positive payment amount');
      return;
    }
    void act(async () => {
      await recordPayment.mutateAsync({
        patientId,
        amount: value,
        method,
        description: reference.trim()
          ? `Checkout payment (ref ${reference.trim()})`
          : 'Checkout payment',
      });
      setPaidAmount(value);
    }, 'Failed to record payment');
  };

  const completeAppointment = () =>
    act(async () => {
      await updateStatus.mutateAsync({ status: 'COMPLETED' });
      setFinished(true);
    }, 'Failed to complete appointment');

  return (
    <div className="max-h-[70vh] space-y-6 overflow-y-auto pr-1">
      {error && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <p className="text-sm text-gray-600">
        {appointment.patientLastName}, {appointment.patientFirstName} ·{' '}
        {appointment.providerLastName}, {appointment.providerFirstName} ·{' '}
        {new Date(appointment.startsAt).toLocaleTimeString([], {
          hour: 'numeric',
          minute: '2-digit',
        })}
      </p>

      {/* 1 — Procedures */}
      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          1 · Procedures
        </h3>
        {appointment.procedures.length === 0 && completedHere.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">
            No procedures attached to this appointment. Use the patient chart to record
            completed work.
          </p>
        ) : (
          <div className="mt-2 space-y-2">
            {appointment.procedures.map((procedure) => {
              const completed = completedHere.find(
                (cp) => cp.procedureCodeId === procedure.procedureCodeId,
              );
              return completed ? (
                <CompletedRow
                  key={procedure.procedureCodeId}
                  completed={completed}
                  busy={undoProcedure.isPending}
                  onUndo={() =>
                    void act(
                      () => undoProcedure.mutateAsync(completed.id),
                      'Failed to undo procedure',
                    )
                  }
                />
              ) : (
                <PendingRow
                  key={procedure.procedureCodeId}
                  procedure={procedure}
                  busy={completeProcedure.isPending}
                  onComplete={(tooth, fee) => completeRow(procedure, tooth, fee)}
                />
              );
            })}
            <p className="text-right text-sm font-medium text-gray-700">
              Completed total: {money(completedTotal)}
            </p>
          </div>
        )}
      </section>

      {/* 2 — Insurance estimate */}
      {hasEstimate && completedHere.length > 0 && (
        <section>
          <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
            2 · Insurance estimate
          </h3>
          <div className="mt-2 rounded-md bg-blue-50/60 p-3 text-sm">
            <p className="text-gray-700">
              Est. insurance pays{' '}
              <span className="font-semibold text-blue-700">
                {money(estimate.totalInsurance)}
              </span>
              {estimate.carrierName && (
                <span className="text-xs text-gray-500">
                  {' '}
                  ({estimate.carrierName} · {estimate.planName})
                </span>
              )}
            </p>
            <p className="text-gray-700">
              Est. patient portion{' '}
              <span className="font-semibold text-gray-900">{money(estimate.totalPatient)}</span>
            </p>
          </div>
        </section>
      )}

      {/* 3 — Payment */}
      <section>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          3 · Payment
        </h3>
        {paidAmount !== null ? (
          <p className="mt-2 rounded-md bg-green-50 p-3 text-sm text-green-800">
            <Check size={14} className="mr-1 inline-block" aria-hidden />
            Payment of {money(paidAmount)} recorded.
          </p>
        ) : (
          <div className="mt-2 flex flex-wrap items-end gap-3">
            <div className="w-32">
              <label htmlFor="checkout-amount" className="block text-sm font-medium text-gray-700">
                Amount ($)
              </label>
              <input
                id="checkout-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmountOverride(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="checkout-method" className="block text-sm font-medium text-gray-700">
                Method
              </label>
              <select
                id="checkout-method"
                value={method}
                onChange={(e) => setMethod(e.target.value)}
                className={inputClass}
              >
                {PAYMENT_METHODS.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="min-w-32 flex-1">
              <label
                htmlFor="checkout-reference"
                className="block text-sm font-medium text-gray-700"
              >
                Reference (optional)
              </label>
              <input
                id="checkout-reference"
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                placeholder="Check #, last 4…"
                className={inputClass}
              />
            </div>
            <Button onClick={takePayment} loading={recordPayment.isPending}>
              Take payment
            </Button>
          </div>
        )}
      </section>

      {/* 4 — Finish */}
      <section className="border-t border-gray-100 pt-4">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
          4 · Finish
        </h3>
        {!finished ? (
          <div className="mt-2">
            <Button onClick={completeAppointment} loading={updateStatus.isPending}>
              Complete appointment
            </Button>
          </div>
        ) : (
          <div className="mt-2 space-y-3">
            <p className="rounded-md bg-green-50 p-3 text-sm font-medium text-green-800">
              <Check size={14} className="mr-1 inline-block" aria-hidden />
              Appointment completed.
            </p>
            {walkoutError && (
              <p role="alert" className="text-sm text-red-600">
                Walk-out statement failed.
              </p>
            )}
            <div className="flex flex-wrap items-center gap-3">
              <Button
                variant="secondary"
                onClick={() => {
                  setWalkoutError(false);
                  openWalkout(appointment.id).catch(() => setWalkoutError(true));
                }}
              >
                Walk-out statement
              </Button>
              <Link
                to="/schedule"
                onClick={onClose}
                className="text-sm font-medium text-brand-600 hover:underline"
              >
                Book next visit →
              </Link>
            </div>
            {patient?.nextRecallDate && (
              <p className="text-sm text-gray-600">
                Next recall due <span className="font-medium">{patient.nextRecallDate}</span>
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function PendingRow({
  procedure,
  busy,
  onComplete,
}: {
  procedure: AppointmentProcedure;
  busy: boolean;
  onComplete: (tooth: string, fee: string) => void;
}) {
  const [tooth, setTooth] = useState('');
  const [fee, setFee] = useState(
    procedure.standardFee != null ? procedure.standardFee.toFixed(2) : '',
  );

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-md p-3 ring-1 ring-gray-200">
      <div className="min-w-40 flex-1">
        <p className="text-sm font-medium text-gray-900">
          <span className="font-mono">{procedure.code}</span>
          {procedure.description && (
            <span className="ml-2 font-normal text-gray-600">{procedure.description}</span>
          )}
        </p>
      </div>
      <div className="w-20">
        <label
          htmlFor={`tooth-${procedure.procedureCodeId}`}
          className="block text-xs font-medium text-gray-500"
        >
          Tooth
        </label>
        <input
          id={`tooth-${procedure.procedureCodeId}`}
          value={tooth}
          onChange={(e) => setTooth(e.target.value)}
          className={inputClass}
        />
      </div>
      <div className="w-28">
        <label
          htmlFor={`fee-${procedure.procedureCodeId}`}
          className="block text-xs font-medium text-gray-500"
        >
          Fee ($)
        </label>
        <input
          id={`fee-${procedure.procedureCodeId}`}
          type="number"
          min="0"
          step="0.01"
          value={fee}
          onChange={(e) => setFee(e.target.value)}
          className={inputClass}
        />
      </div>
      <Button variant="secondary" disabled={busy} onClick={() => onComplete(tooth, fee)}>
        Complete
      </Button>
    </div>
  );
}

function CompletedRow({
  completed,
  busy,
  onUndo,
}: {
  completed: CompletedProcedure;
  busy: boolean;
  onUndo: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-green-50 p-3 ring-1 ring-green-200">
      <p className="flex items-center gap-2 text-sm text-green-800">
        <Check size={16} aria-hidden />
        <span className="font-mono">{completed.code}</span>
        {completed.description && <span>{completed.description}</span>}
        {completed.tooth && <span className="text-xs">#{completed.tooth}</span>}
      </p>
      <span className="flex items-center gap-3">
        <span className="text-sm font-semibold text-green-800">{money(completed.fee)}</span>
        <button
          type="button"
          disabled={busy}
          onClick={onUndo}
          className="text-xs font-medium text-brand-600 hover:underline disabled:text-gray-400"
        >
          Undo
        </button>
      </span>
    </div>
  );
}
