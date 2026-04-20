import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import type { InsurancePlan } from '../../types/api';
import { useCarrierPlans, useCarriers, useCreateCarrier, useCreatePlan } from './api';
import { FeeSchedulesSection } from './FeeSchedulesSection';
import { PlanCoverageEditor } from './PlanCoverageEditor';

const PLAN_TYPES = ['PPO', 'HMO', 'INDEMNITY', 'MEDICAID', 'DISCOUNT', 'OTHER'];

const money = (n: number | null) => (n == null ? '—' : `$${n.toFixed(2)}`);

export function InsurancePage() {
  const [search, setSearch] = useState('');
  const [openCarrierId, setOpenCarrierId] = useState<string | null>(null);
  const [addingCarrier, setAddingCarrier] = useState(false);
  const { hasRole } = useAuth();
  const canManage = hasRole('ADMIN', 'BILLING');

  const { data: carriers, isPending } = useCarriers(search);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Insurance</h1>
        {canManage && !addingCarrier && (
          <Button onClick={() => setAddingCarrier(true)}>New carrier</Button>
        )}
      </div>

      {addingCarrier && <NewCarrierForm onDone={() => setAddingCarrier(false)} />}

      <div className="mt-4">
        <FeeSchedulesSection canManage={canManage} />
      </div>

      <div className="mt-4">
        <input
          type="search"
          placeholder="Search carriers…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search carriers"
          className="w-full max-w-md rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        />
      </div>

      {isPending ? (
        <Spinner label="Loading carriers…" />
      ) : (
        <ul className="mt-4 space-y-3">
          {carriers?.content.map((carrier) => (
            <li key={carrier.id} className="rounded-lg bg-white shadow">
              <button
                onClick={() =>
                  setOpenCarrierId(openCarrierId === carrier.id ? null : carrier.id)
                }
                className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-gray-50"
              >
                <div>
                  <p className="text-sm font-semibold text-gray-900">{carrier.name}</p>
                  <p className="text-xs text-gray-500">
                    Payer ID {carrier.payerId ?? '—'} · {carrier.planCount} plans
                  </p>
                </div>
                <span className="text-sm text-gray-400">
                  {openCarrierId === carrier.id ? '▾' : '▸'}
                </span>
              </button>
              {openCarrierId === carrier.id && (
                <CarrierPlans carrierId={carrier.id} canManage={canManage} />
              )}
            </li>
          ))}
          {carriers?.content.length === 0 && (
            <p className="p-4 text-sm text-gray-500">No carriers found.</p>
          )}
        </ul>
      )}
    </div>
  );
}

function NewCarrierForm({ onDone }: { onDone: () => void }) {
  const createCarrier = useCreateCarrier();
  const [name, setName] = useState('');
  const [payerId, setPayerId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!name.trim()) return setError('Carrier name is required');
    setError(null);
    try {
      await createCarrier.mutateAsync({ name: name.trim(), payerId: payerId || null });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to create carrier');
    }
  };

  return (
    <div className="mt-4 flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="w-full text-sm text-red-600">
          {error}
        </p>
      )}
      <Input
        label="Carrier name"
        className="min-w-64"
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <Input label="Payer ID" value={payerId} onChange={(e) => setPayerId(e.target.value)} />
      <Button onClick={submit} loading={createCarrier.isPending}>
        Create
      </Button>
      <Button variant="secondary" onClick={onDone}>
        Cancel
      </Button>
    </div>
  );
}

function CarrierPlans({ carrierId, canManage }: { carrierId: string; canManage: boolean }) {
  const { data: plans, isPending } = useCarrierPlans(carrierId);
  const createPlan = useCreatePlan();
  const [adding, setAdding] = useState(false);
  const [planName, setPlanName] = useState('');
  const [planType, setPlanType] = useState('PPO');
  const [annualMax, setAnnualMax] = useState('');
  const [deductible, setDeductible] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (isPending) return <Spinner label="Loading plans…" />;

  const submit = async () => {
    if (!planName.trim()) return setError('Plan name is required');
    setError(null);
    try {
      await createPlan.mutateAsync({
        carrierId,
        planName: planName.trim(),
        planType,
        annualMax: annualMax ? Number(annualMax) : null,
        deductible: deductible ? Number(deductible) : null,
      });
      setPlanName('');
      setAdding(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to create plan');
    }
  };

  return (
    <div className="border-t border-gray-100 px-4 py-3">
      {plans && plans.length > 0 ? (
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-1 pr-3">Plan</th>
              <th className="py-1 pr-3">Type</th>
              <th className="py-1 pr-3">Group #</th>
              <th className="py-1 pr-3">Annual max</th>
              <th className="py-1 pr-3">Deductible</th>
              <th className="py-1" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {plans.map((plan) => (
              <PlanRow key={plan.id} plan={plan} canManage={canManage} />
            ))}
          </tbody>
        </table>
      ) : (
        <p className="text-sm text-gray-500">No plans yet.</p>
      )}

      {canManage &&
        (adding ? (
          <div className="mt-3 flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-3">
            {error && (
              <p role="alert" className="w-full text-sm text-red-600">
                {error}
              </p>
            )}
            <Input
              label="Plan name"
              className="min-w-48"
              value={planName}
              onChange={(e) => setPlanName(e.target.value)}
            />
            <div>
              <label htmlFor={`plan-type-${carrierId}`} className="block text-sm font-medium text-gray-700">
                Type
              </label>
              <select
                id={`plan-type-${carrierId}`}
                value={planType}
                onChange={(e) => setPlanType(e.target.value)}
                className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              >
                {PLAN_TYPES.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </div>
            <Input
              label="Annual max"
              type="number"
              className="w-32"
              value={annualMax}
              onChange={(e) => setAnnualMax(e.target.value)}
            />
            <Input
              label="Deductible"
              type="number"
              className="w-32"
              value={deductible}
              onChange={(e) => setDeductible(e.target.value)}
            />
            <Button onClick={submit} loading={createPlan.isPending}>
              Add plan
            </Button>
            <Button variant="secondary" onClick={() => setAdding(false)}>
              Cancel
            </Button>
          </div>
        ) : (
          <Button variant="ghost" className="mt-2" onClick={() => setAdding(true)}>
            + Add plan
          </Button>
        ))}
    </div>
  );
}

function PlanRow({ plan, canManage }: { plan: InsurancePlan; canManage: boolean }) {
  const [showCoverage, setShowCoverage] = useState(false);
  return (
    <>
      <tr>
        <td className="py-2 pr-3 font-medium text-gray-900">{plan.planName}</td>
        <td className="py-2 pr-3">
          <Badge tone="blue">{plan.planType}</Badge>
        </td>
        <td className="py-2 pr-3 text-gray-600">{plan.groupNumber ?? '—'}</td>
        <td className="py-2 pr-3 text-gray-600">{money(plan.annualMax)}</td>
        <td className="py-2 pr-3 text-gray-600">{money(plan.deductible)}</td>
        <td className="py-2 text-right">
          <button
            onClick={() => setShowCoverage((v) => !v)}
            className="text-sm text-brand-600 hover:underline"
          >
            Coverage
          </button>
        </td>
      </tr>
      {showCoverage && (
        <tr>
          <td colSpan={6}>
            <PlanCoverageEditor
              planId={plan.id}
              currentFeeScheduleId={plan.feeScheduleId}
              canManage={canManage}
            />
          </td>
        </tr>
      )}
    </>
  );
}
