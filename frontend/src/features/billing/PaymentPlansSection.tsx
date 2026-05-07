import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  usePaymentPlans,
  useUpdatePaymentPlanStatus,
  type PaymentPlan,
  type PaymentPlanStatus,
} from './api';
import { PaymentPlanModal } from './PaymentPlanModal';

const money = (n: number) => `$${n.toFixed(2)}`;

const statusTone: Record<PaymentPlanStatus, 'blue' | 'green' | 'red' | 'gray'> = {
  ACTIVE: 'blue',
  COMPLETED: 'green',
  DEFAULTED: 'red',
  CANCELLED: 'gray',
};

const STATUS_ACTIONS: Array<{
  label: string;
  status: PaymentPlanStatus;
  confirm: string;
  variant: 'secondary' | 'danger';
}> = [
  {
    label: 'Complete',
    status: 'COMPLETED',
    confirm: 'Mark this payment plan as completed?',
    variant: 'secondary',
  },
  {
    label: 'Default',
    status: 'DEFAULTED',
    confirm: 'Mark this payment plan as defaulted?',
    variant: 'danger',
  },
  {
    label: 'Cancel',
    status: 'CANCELLED',
    confirm: 'Cancel this payment plan?',
    variant: 'danger',
  },
];

export function PaymentPlansSection({ patientId }: { patientId: string }) {
  const { data: plans, isPending } = usePaymentPlans(patientId);
  const updateStatus = useUpdatePaymentPlanStatus();
  const { hasRole } = useAuth();
  const canManage = hasRole('ADMIN', 'BILLING');

  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="space-y-3 border-t border-gray-100 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          Payment plans
        </h3>
        {canManage && (
          <Button variant="secondary" onClick={() => setCreating(true)}>
            New payment plan
          </Button>
        )}
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {isPending ? (
        <Spinner label="Loading payment plans…" />
      ) : !plans || plans.length === 0 ? (
        <p className="text-sm text-gray-500">No payment plans.</p>
      ) : (
        <ul className="space-y-3">
          {plans.map((plan) => (
            <PlanRow
              key={plan.id}
              plan={plan}
              canManage={canManage}
              busy={updateStatus.isPending}
              onStatus={async (status) => {
                setError(null);
                try {
                  await updateStatus.mutateAsync({ planId: plan.id, status });
                } catch (e) {
                  setError(
                    e instanceof ApiError ? e.message : 'Failed to update payment plan',
                  );
                }
              }}
            />
          ))}
        </ul>
      )}

      {creating && (
        <PaymentPlanModal patientId={patientId} open onClose={() => setCreating(false)} />
      )}
    </div>
  );
}

function PlanRow({
  plan,
  canManage,
  busy,
  onStatus,
}: {
  plan: PaymentPlan;
  canManage: boolean;
  busy: boolean;
  onStatus: (status: PaymentPlanStatus) => Promise<void>;
}) {
  const onTrack = plan.receivedToDate >= plan.expectedToDate;
  const progress =
    plan.totalAmount > 0
      ? Math.min((plan.receivedToDate / plan.totalAmount) * 100, 100)
      : 0;

  return (
    <li className="space-y-2 rounded-md p-4 ring-1 ring-gray-200">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Badge tone={statusTone[plan.status]}>{plan.status}</Badge>
          <span className="text-sm font-semibold text-gray-900">
            {money(plan.totalAmount)}
          </span>
          <span className="text-xs text-gray-500">
            {money(plan.installmentAmount)}{' '}
            {plan.frequency === 'MONTHLY' ? 'monthly' : 'biweekly'} from {plan.firstDueDate}
            {plan.downPayment > 0 && ` · ${money(plan.downPayment)} down`}
          </span>
        </div>
        {canManage && plan.status === 'ACTIVE' && (
          <div className="flex gap-2">
            {STATUS_ACTIONS.map((action) => (
              <Button
                key={action.status}
                variant={action.variant}
                disabled={busy}
                onClick={() => {
                  if (window.confirm(action.confirm)) void onStatus(action.status);
                }}
              >
                {action.label}
              </Button>
            ))}
          </div>
        )}
      </div>
      <div>
        <p
          className={`text-sm font-medium ${onTrack ? 'text-green-700' : 'text-red-600'}`}
        >
          {money(plan.receivedToDate)} received of {money(plan.expectedToDate)} expected to
          date {onTrack ? '· on track' : '· behind'}
        </p>
        <div className="mt-1 h-2 w-full overflow-hidden rounded bg-gray-100">
          <div
            className={`h-full ${onTrack ? 'bg-green-500' : 'bg-red-400'}`}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
      {plan.notes && <p className="text-xs text-gray-500">{plan.notes}</p>}
    </li>
  );
}
