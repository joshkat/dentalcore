import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import type { TreatmentPlanStatus } from '../../types/api';
import { usePlanEstimate } from '../insurance/api';
import { useProcedureCodes } from '../procedures/api';
import { useProviders } from '../providers/api';
import {
  PLAN_NEXT_STATUSES,
  PLAN_STATUS_LABELS,
  useAddPlanProcedure,
  useCreatePlan,
  useRemovePlanProcedure,
  useTreatmentPlan,
  useTreatmentPlans,
  useUpdatePlanProcedureStatus,
  useUpdatePlanStatus,
} from './api';

const planTone: Record<TreatmentPlanStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  DRAFT: 'gray',
  PRESENTED: 'blue',
  APPROVED: 'green',
  IN_PROGRESS: 'yellow',
  COMPLETED: 'green',
  CANCELLED: 'red',
};

const money = (n: number) => `$${n.toFixed(2)}`;

export function TreatmentPlansTab({
  patientId,
  canWrite,
}: {
  patientId: string;
  canWrite: boolean;
}) {
  const { data: plans, isPending } = useTreatmentPlans(patientId);
  const [openPlanId, setOpenPlanId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  if (isPending) return <Spinner label="Loading treatment plans…" />;

  return (
    <div className="space-y-4">
      {canWrite && !creating && (
        <Button onClick={() => setCreating(true)}>New treatment plan</Button>
      )}
      {creating && (
        <NewPlanForm patientId={patientId} onDone={() => setCreating(false)} />
      )}

      {plans && plans.content.length === 0 ? (
        <p className="text-sm text-gray-500">No treatment plans yet.</p>
      ) : (
        <ul className="space-y-3">
          {plans?.content.map((plan) => (
            <li key={plan.id} className="rounded-md ring-1 ring-gray-200">
              <button
                onClick={() => setOpenPlanId(openPlanId === plan.id ? null : plan.id)}
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-gray-50"
              >
                <div>
                  <p className="text-sm font-semibold text-gray-900">{plan.title}</p>
                  <p className="text-xs text-gray-500">
                    {plan.procedureCount} procedures · {plan.completedCount} completed ·{' '}
                    {money(plan.totalEstimatedCost)}
                  </p>
                </div>
                <Badge tone={planTone[plan.status]}>{PLAN_STATUS_LABELS[plan.status]}</Badge>
              </button>
              {openPlanId === plan.id && (
                <PlanDetail planId={plan.id} patientId={patientId} canWrite={canWrite} />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NewPlanForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const createPlan = useCreatePlan(patientId);
  const { data: providers } = useProviders(false);
  const [title, setTitle] = useState('');
  const [providerId, setProviderId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!title.trim()) return setError('Enter a title');
    if (!providerId) return setError('Select a provider');
    setError(null);
    try {
      await createPlan.mutateAsync({ providerId, title: title.trim() });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to create plan');
    }
  };

  return (
    <div className="space-y-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-64 flex-1">
          <label htmlFor="plan-title" className="block text-sm font-medium text-gray-700">
            Title
          </label>
          <input
            id="plan-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Phase I — restorative"
            className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
        </div>
        <div>
          <label htmlFor="plan-provider" className="block text-sm font-medium text-gray-700">
            Provider
          </label>
          <select
            id="plan-provider"
            value={providerId}
            onChange={(e) => setProviderId(e.target.value)}
            className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          >
            <option value="">Select…</option>
            {providers?.content.map((p) => (
              <option key={p.id} value={p.id}>
                {p.lastName}, {p.firstName}
              </option>
            ))}
          </select>
        </div>
        <Button onClick={submit} loading={createPlan.isPending}>
          Create
        </Button>
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </div>
  );
}

function PlanDetail({
  planId,
  patientId,
  canWrite,
}: {
  planId: string;
  patientId: string;
  canWrite: boolean;
}) {
  const { data: plan, isPending } = useTreatmentPlan(planId);
  const { data: estimate } = usePlanEstimate(planId);
  const updateStatus = useUpdatePlanStatus(patientId);
  const addProcedure = useAddPlanProcedure(patientId);
  const removeProcedure = useRemovePlanProcedure(patientId);
  const updateProcedureStatus = useUpdatePlanProcedureStatus(patientId);

  const [codeSearch, setCodeSearch] = useState('');
  const [tooth, setTooth] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  if (isPending || !plan) return <Spinner label="Loading plan…" />;

  const editable = canWrite && (plan.status === 'DRAFT' || plan.status === 'PRESENTED');
  const trackable = canWrite && (plan.status === 'APPROVED' || plan.status === 'IN_PROGRESS');

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed');
    }
  };

  return (
    <div className="space-y-4 border-t border-gray-100 px-4 py-4">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
        <span>
          Provider: {plan.providerLastName}, {plan.providerFirstName}
          {plan.approvedAt &&
            ` · Approved ${new Date(plan.approvedAt).toLocaleDateString()}`}
        </span>
        <span className="font-medium text-gray-900">
          Total {money(plan.totalEstimatedCost)} · Completed {money(plan.completedCost)}
        </span>
      </div>

      {plan.procedures.length > 0 && (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">#</th>
              <th className="py-2 pr-3">Code</th>
              <th className="py-2 pr-3">Description</th>
              <th className="py-2 pr-3">Tooth</th>
              <th className="py-2 pr-3">Est. cost</th>
              <th className="py-2 pr-3">Status</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {plan.procedures.map((procedure) => (
              <tr key={procedure.id}>
                <td className="py-2 pr-3 text-gray-500">{procedure.priority}</td>
                <td className="py-2 pr-3 font-mono">{procedure.code}</td>
                <td className="py-2 pr-3">{procedure.description}</td>
                <td className="py-2 pr-3">
                  {procedure.tooth ?? '—'}
                  {procedure.surface ? ` (${procedure.surface})` : ''}
                </td>
                <td className="py-2 pr-3">{money(procedure.estimatedCost)}</td>
                <td className="py-2 pr-3">
                  <Badge
                    tone={
                      procedure.status === 'COMPLETED'
                        ? 'green'
                        : procedure.status === 'CANCELLED'
                          ? 'red'
                          : procedure.status === 'SCHEDULED'
                            ? 'blue'
                            : 'gray'
                    }
                  >
                    {procedure.status}
                  </Badge>
                </td>
                <td className="py-2 text-right">
                  {editable && (
                    <button
                      onClick={() =>
                        act(() =>
                          removeProcedure.mutateAsync({ planId, procedureId: procedure.id }),
                        )
                      }
                      className="text-xs text-red-600 hover:underline"
                    >
                      Remove
                    </button>
                  )}
                  {trackable &&
                    (procedure.status === 'PLANNED' || procedure.status === 'SCHEDULED') && (
                      <button
                        onClick={() =>
                          act(() =>
                            updateProcedureStatus.mutateAsync({
                              planId,
                              procedureId: procedure.id,
                              status: 'COMPLETED',
                            }),
                          )
                        }
                        className="text-xs text-brand-600 hover:underline"
                      >
                        Mark completed
                      </button>
                    )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {estimate?.hasCoverage && estimate.lines.length > 0 && (
        <div className="rounded-md bg-blue-50/60 p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            Insurance estimate — {estimate.carrierName} · {estimate.planName}
          </p>
          <table className="mt-2 min-w-full text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-500">
                <th className="py-1 pr-3">Code</th>
                <th className="py-1 pr-3 text-right">Fee</th>
                <th className="py-1 pr-3 text-right">Allowed</th>
                <th className="py-1 pr-3 text-right">Ins. pays</th>
                <th className="py-1 text-right">Patient</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-blue-100">
              {estimate.lines.map((line, index) => (
                <tr key={index}>
                  <td className="py-1.5 pr-3 font-mono">{line.code}</td>
                  <td className="py-1.5 pr-3 text-right text-gray-500">
                    {money(line.grossFee)}
                  </td>
                  <td className="py-1.5 pr-3 text-right">{money(line.allowedFee)}</td>
                  <td className="py-1.5 pr-3 text-right text-blue-700">
                    {money(line.insuranceEstimate)}
                    {line.deductibleApplied > 0 && (
                      <span className="ml-1 text-xs text-gray-400">
                        (ded {money(line.deductibleApplied)})
                      </span>
                    )}
                  </td>
                  <td className="py-1.5 text-right font-medium">
                    {money(line.patientPortion)}
                  </td>
                </tr>
              ))}
              <tr className="font-semibold">
                <td className="py-1.5 pr-3">Totals</td>
                <td />
                <td />
                <td className="py-1.5 pr-3 text-right text-blue-700">
                  {money(estimate.totalInsurance)}
                </td>
                <td className="py-1.5 text-right">{money(estimate.totalPatient)}</td>
              </tr>
            </tbody>
          </table>
          <p className="mt-1 text-xs text-gray-500">
            Estimate only — based on fee schedule, coverage rules, deductible, and remaining
            annual max{estimate.benefitsRemaining != null &&
              ` (${money(estimate.benefitsRemaining)} remaining)`}.
          </p>
        </div>
      )}

      {editable && (
        <div className="rounded-md bg-gray-50 p-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-64 flex-1">
              <label htmlFor={`code-search-${planId}`} className="block text-sm font-medium text-gray-700">
                Add procedure
              </label>
              <input
                id={`code-search-${planId}`}
                type="search"
                value={codeSearch}
                onChange={(e) => setCodeSearch(e.target.value)}
                placeholder="Search catalog (e.g. crown, D1110)…"
                className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
              {codeSearch && (
                <ul className="mt-1 max-h-40 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
                  {catalog?.content.slice(0, 8).map((entry) => (
                    <li key={entry.id}>
                      <button
                        type="button"
                        onClick={() =>
                          act(async () => {
                            await addProcedure.mutateAsync({
                              planId,
                              procedureCodeId: entry.id,
                              tooth: tooth || undefined,
                            });
                            setCodeSearch('');
                            setTooth('');
                          })
                        }
                        className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                      >
                        <span className="font-mono">{entry.code}</span> — {entry.description} (
                        {money(entry.standardFee)})
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <label htmlFor={`tooth-${planId}`} className="block text-sm font-medium text-gray-700">
                Tooth #
              </label>
              <input
                id={`tooth-${planId}`}
                value={tooth}
                onChange={(e) => setTooth(e.target.value)}
                className="mt-1 w-20 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
            </div>
          </div>
        </div>
      )}

      {canWrite && PLAN_NEXT_STATUSES[plan.status].length > 0 && (
        <div className="flex flex-wrap gap-2 border-t border-gray-100 pt-3">
          {PLAN_NEXT_STATUSES[plan.status].map((status) => (
            <Button
              key={status}
              variant={status === 'CANCELLED' ? 'danger' : 'secondary'}
              onClick={() => act(() => updateStatus.mutateAsync({ planId, status }))}
            >
              {status === 'DRAFT' ? 'Back to draft' : PLAN_STATUS_LABELS[status]}
            </Button>
          ))}
        </div>
      )}
    </div>
  );
}
