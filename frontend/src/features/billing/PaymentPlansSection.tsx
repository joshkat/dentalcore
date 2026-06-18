import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  usePaymentPlans,
  useUpdatePaymentPlanStatus,
  type PaymentPlan,
  type PaymentPlanStatus,
} from './api';
import { PaymentPlanModal } from './PaymentPlanModal';

const statusTone: Record<PaymentPlanStatus, 'blue' | 'green' | 'red' | 'gray'> = {
  ACTIVE: 'blue',
  COMPLETED: 'green',
  DEFAULTED: 'red',
  CANCELLED: 'gray',
};

const STATUS_ACTIONS: Array<{
  labelKey: string;
  status: PaymentPlanStatus;
  confirmKey: string;
  variant: 'secondary' | 'danger';
}> = [
  {
    labelKey: 'plans.action.complete',
    status: 'COMPLETED',
    confirmKey: 'plans.confirm.complete',
    variant: 'secondary',
  },
  {
    labelKey: 'plans.action.default',
    status: 'DEFAULTED',
    confirmKey: 'plans.confirm.default',
    variant: 'danger',
  },
  {
    labelKey: 'plans.action.cancel',
    status: 'CANCELLED',
    confirmKey: 'plans.confirm.cancel',
    variant: 'danger',
  },
];

export function PaymentPlansSection({ patientId }: { patientId: string }) {
  const { t } = useTranslation('billing');
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
          {t('plans.heading')}
        </h3>
        {canManage && (
          <Button variant="secondary" onClick={() => setCreating(true)}>
            {t('plans.new')}
          </Button>
        )}
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {isPending ? (
        <Spinner label={t('plans.loading')} />
      ) : !plans || plans.length === 0 ? (
        <p className="text-sm text-gray-500">{t('plans.none')}</p>
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
                    e instanceof ApiError ? e.message : t('plans.failedUpdate'),
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
  const { t } = useTranslation('billing');
  const onTrack = plan.receivedToDate >= plan.expectedToDate;
  const progress =
    plan.totalAmount > 0
      ? Math.min((plan.receivedToDate / plan.totalAmount) * 100, 100)
      : 0;

  return (
    <li className="space-y-2 rounded-md p-4 ring-1 ring-gray-200">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Badge tone={statusTone[plan.status]}>{t(`plans.status.${plan.status}`)}</Badge>
          <span className="text-sm font-semibold text-gray-900">
            {formatMoney(plan.totalAmount)}
          </span>
          <span className="text-xs text-gray-500">
            {t('plans.scheduleFrom', {
              amount: formatMoney(plan.installmentAmount),
              frequency: t(`plans.frequency.${plan.frequency}`),
              date: formatDate(plan.firstDueDate),
            })}
            {plan.downPayment > 0 &&
              t('plans.downSuffix', { amount: formatMoney(plan.downPayment) })}
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
                  if (window.confirm(t(action.confirmKey))) void onStatus(action.status);
                }}
              >
                {t(action.labelKey)}
              </Button>
            ))}
          </div>
        )}
      </div>
      <div>
        <p
          className={`text-sm font-medium ${onTrack ? 'text-green-700' : 'text-red-600'}`}
        >
          {t('plans.progress', {
            received: formatMoney(plan.receivedToDate),
            expected: formatMoney(plan.expectedToDate),
          })}
          {onTrack ? t('plans.onTrack') : t('plans.behind')}
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
