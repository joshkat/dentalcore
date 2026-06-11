import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { usePatients } from '../patients/api';
import {
  useAddCoverage,
  useBenefits,
  useCarrierPlans,
  useCarriers,
  useCreateClaim,
  usePatientCoverage,
  useRemoveCoverage,
} from './api';

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300';

export function InsuranceTab({
  patientId,
  canWrite,
}: {
  patientId: string;
  canWrite: boolean;
}) {
  const { t } = useTranslation('insurance');
  const { data: coverages, isPending } = usePatientCoverage(patientId);
  const removeCoverage = useRemoveCoverage();
  const createClaim = useCreateClaim();
  const { data: benefits } = useBenefits(patientId);
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canBill = hasRole('ADMIN', 'BILLING');
  const [adding, setAdding] = useState(false);

  if (isPending) return <Spinner label={t('tab.loading')} />;

  return (
    <div className="space-y-4">
      {canWrite && !adding && (
        <Button onClick={() => setAdding(true)}>{t('tab.addCoverage')}</Button>
      )}
      {adding && <AddCoverageForm patientId={patientId} onDone={() => setAdding(false)} />}

      {benefits?.hasCoverage && (
        <div className="grid grid-cols-2 gap-3 rounded-md bg-gray-50 p-4 sm:grid-cols-4">
          {[
            [t('tab.metrics.deductible'), formatMoney(benefits.deductible)],
            [t('tab.metrics.deductibleRemaining'), formatMoney(benefits.deductibleRemaining)],
            [t('tab.metrics.benefitsUsed'), formatMoney(benefits.benefitsUsed)],
            [
              t('tab.metrics.benefitsRemaining'),
              benefits.benefitsRemaining == null
                ? t('tab.noMax')
                : formatMoney(benefits.benefitsRemaining),
            ],
          ].map(([label, value]) => (
            <div key={label}>
              <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                {label}
              </p>
              <p className="text-lg font-bold text-gray-900">{value}</p>
            </div>
          ))}
        </div>
      )}

      {benefits?.secondary && (
        <div className="rounded-md bg-blue-50/60 p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            {t('tab.secondaryHeading', {
              carrier: benefits.secondary.carrierName,
              plan: benefits.secondary.planName,
            })}
          </p>
          <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              [t('tab.metrics.deductible'), formatMoney(benefits.secondary.deductible)],
              [
                t('tab.metrics.deductibleRemaining'),
                formatMoney(benefits.secondary.deductibleRemaining),
              ],
              [t('tab.metrics.benefitsUsed'), formatMoney(benefits.secondary.benefitsUsed)],
              [
                t('tab.metrics.benefitsRemaining'),
                benefits.secondary.benefitsRemaining == null
                  ? t('tab.noMax')
                  : formatMoney(benefits.secondary.benefitsRemaining),
              ],
            ].map(([label, value]) => (
              <div key={label}>
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                  {label}
                </p>
                <p className="text-sm font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {coverages && coverages.length === 0 ? (
        <p className="text-sm text-gray-500">{t('tab.noInsurance')}</p>
      ) : (
        <ul className="space-y-3">
          {coverages?.map((coverage) => (
            <li
              key={coverage.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-md p-4 ring-1 ring-gray-200"
            >
              <div>
                <div className="flex items-center gap-2">
                  <Badge tone={coverage.priority === 'PRIMARY' ? 'green' : 'blue'}>
                    {t(`priority.${coverage.priority}`)}
                  </Badge>
                  <p className="text-sm font-semibold text-gray-900">
                    {coverage.carrierName} — {coverage.planName}
                  </p>
                  <Badge tone="gray">{coverage.planType}</Badge>
                </div>
                <p className="mt-1 text-xs text-gray-500">
                  {t('tab.memberInfo', {
                    memberId: coverage.memberId,
                    subscriber:
                      coverage.relationshipToSubscriber === 'SELF'
                        ? t('tab.subscriberSelf')
                        : t('tab.subscriberOther', {
                            name: `${coverage.subscriberLastName}, ${coverage.subscriberFirstName}`,
                            relationship: t(`relationship.${coverage.relationshipToSubscriber}`),
                          }),
                  })}
                  {coverage.effectiveDate &&
                    t('tab.effectiveOn', { date: formatDate(coverage.effectiveDate) })}
                  {coverage.terminationDate &&
                    t('tab.terminatesOn', { date: formatDate(coverage.terminationDate) })}
                </p>
              </div>
              <div className="flex gap-2">
                {canBill && (
                  <Button
                    variant="secondary"
                    loading={createClaim.isPending}
                    onClick={async () => {
                      await createClaim.mutateAsync({ patientInsuranceId: coverage.id });
                      navigate('/claims');
                    }}
                  >
                    {t('tab.openClaim')}
                  </Button>
                )}
                {canWrite && (
                  <Button
                    variant="ghost"
                    onClick={() => removeCoverage.mutate(coverage.id)}
                    disabled={removeCoverage.isPending}
                  >
                    {t('tab.remove')}
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function AddCoverageForm({ patientId, onDone }: { patientId: string; onDone: () => void }) {
  const { t } = useTranslation('insurance');
  const addCoverage = useAddCoverage();
  const { data: carriers } = useCarriers();
  const [carrierId, setCarrierId] = useState('');
  const { data: plans } = useCarrierPlans(carrierId || null);

  const [planId, setPlanId] = useState('');
  const [memberId, setMemberId] = useState('');
  const [priority, setPriority] = useState('PRIMARY');
  const [relationship, setRelationship] = useState('SELF');
  const [subscriberSearch, setSubscriberSearch] = useState('');
  const [subscriberId, setSubscriberId] = useState('');
  const { data: candidates } = usePatients(subscriberSearch, 0);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!planId) return setError(t('coverageForm.selectPlan'));
    if (!memberId.trim()) return setError(t('coverageForm.memberIdRequired'));
    const subscriber = relationship === 'SELF' ? patientId : subscriberId;
    if (!subscriber) return setError(t('coverageForm.selectSubscriber'));
    setError(null);
    try {
      await addCoverage.mutateAsync({
        patientId,
        planId,
        subscriberPatientId: subscriber,
        relationshipToSubscriber: relationship,
        memberId: memberId.trim(),
        priority,
      });
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('coverageForm.failedToAdd'));
    }
  };

  return (
    <div className="space-y-3 rounded-md bg-gray-50 p-4">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label htmlFor="cov-carrier" className="block text-sm font-medium text-gray-700">
            {t('coverageForm.carrier')}
          </label>
          <select
            id="cov-carrier"
            value={carrierId}
            onChange={(e) => {
              setCarrierId(e.target.value);
              setPlanId('');
            }}
            className={selectClass}
          >
            <option value="">{t('coverageForm.selectEllipsis')}</option>
            {carriers?.content.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="cov-plan" className="block text-sm font-medium text-gray-700">
            {t('coverageForm.plan')}
          </label>
          <select
            id="cov-plan"
            value={planId}
            onChange={(e) => setPlanId(e.target.value)}
            disabled={!carrierId}
            className={selectClass}
          >
            <option value="">{t('coverageForm.selectEllipsis')}</option>
            {plans?.map((p) => (
              <option key={p.id} value={p.id}>
                {t('coverageForm.planOption', { name: p.planName, type: p.planType })}
              </option>
            ))}
          </select>
        </div>
        <Input
          label={t('coverageForm.memberId')}
          value={memberId}
          onChange={(e) => setMemberId(e.target.value)}
        />
        <div>
          <label htmlFor="cov-priority" className="block text-sm font-medium text-gray-700">
            {t('coverageForm.priority')}
          </label>
          <select
            id="cov-priority"
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            className={selectClass}
          >
            <option value="PRIMARY">{t('priority.PRIMARY')}</option>
            <option value="SECONDARY">{t('priority.SECONDARY')}</option>
          </select>
        </div>
        <div>
          <label htmlFor="cov-rel" className="block text-sm font-medium text-gray-700">
            {t('coverageForm.relationship')}
          </label>
          <select
            id="cov-rel"
            value={relationship}
            onChange={(e) => {
              setRelationship(e.target.value);
              setSubscriberId('');
              setSubscriberSearch('');
            }}
            className={selectClass}
          >
            <option value="SELF">{t('relationship.SELF')}</option>
            <option value="SPOUSE">{t('relationship.SPOUSE')}</option>
            <option value="CHILD">{t('relationship.CHILD')}</option>
            <option value="OTHER">{t('relationship.OTHER')}</option>
          </select>
        </div>
        {relationship !== 'SELF' && (
          <div>
            <label htmlFor="cov-subscriber" className="block text-sm font-medium text-gray-700">
              {t('coverageForm.subscriber')}
            </label>
            <input
              id="cov-subscriber"
              type="search"
              value={subscriberSearch}
              onChange={(e) => {
                setSubscriberSearch(e.target.value);
                setSubscriberId('');
              }}
              placeholder={t('coverageForm.searchPatientsPlaceholder')}
              className={selectClass}
            />
            {subscriberSearch && !subscriberId && (
              <ul className="mt-1 max-h-32 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
                {candidates?.content
                  .filter((p) => p.id !== patientId)
                  .slice(0, 6)
                  .map((p) => (
                    <li key={p.id}>
                      <button
                        type="button"
                        onClick={() => {
                          setSubscriberId(p.id);
                          setSubscriberSearch(`${p.lastName}, ${p.firstName}`);
                        }}
                        className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                      >
                        {p.lastName}, {p.firstName} ({formatDate(p.dateOfBirth)})
                      </button>
                    </li>
                  ))}
              </ul>
            )}
          </div>
        )}
      </div>
      <div className="flex gap-2">
        <Button onClick={submit} loading={addCoverage.isPending}>
          {t('coverageForm.addCoverage')}
        </Button>
        <Button variant="secondary" onClick={onDone}>
          {t('common:cancel')}
        </Button>
      </div>
    </div>
  );
}
