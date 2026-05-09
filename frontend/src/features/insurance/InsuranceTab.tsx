import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Spinner } from '../../components/Spinner';
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
  const { data: coverages, isPending } = usePatientCoverage(patientId);
  const removeCoverage = useRemoveCoverage();
  const createClaim = useCreateClaim();
  const { data: benefits } = useBenefits(patientId);
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canBill = hasRole('ADMIN', 'BILLING');
  const [adding, setAdding] = useState(false);

  if (isPending) return <Spinner label="Loading insurance…" />;

  return (
    <div className="space-y-4">
      {canWrite && !adding && <Button onClick={() => setAdding(true)}>Add coverage</Button>}
      {adding && <AddCoverageForm patientId={patientId} onDone={() => setAdding(false)} />}

      {benefits?.hasCoverage && (
        <div className="grid grid-cols-2 gap-3 rounded-md bg-gray-50 p-4 sm:grid-cols-4">
          {[
            ['Deductible', `$${benefits.deductible.toFixed(2)}`],
            ['Deductible remaining', `$${benefits.deductibleRemaining.toFixed(2)}`],
            ['Benefits used (year)', `$${benefits.benefitsUsed.toFixed(2)}`],
            [
              'Benefits remaining',
              benefits.benefitsRemaining == null
                ? 'No max'
                : `$${benefits.benefitsRemaining.toFixed(2)}`,
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
            Secondary — {benefits.secondary.carrierName} · {benefits.secondary.planName}
          </p>
          <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              ['Deductible', `$${benefits.secondary.deductible.toFixed(2)}`],
              [
                'Deductible remaining',
                `$${benefits.secondary.deductibleRemaining.toFixed(2)}`,
              ],
              ['Benefits used (year)', `$${benefits.secondary.benefitsUsed.toFixed(2)}`],
              [
                'Benefits remaining',
                benefits.secondary.benefitsRemaining == null
                  ? 'No max'
                  : `$${benefits.secondary.benefitsRemaining.toFixed(2)}`,
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
        <p className="text-sm text-gray-500">No insurance on file.</p>
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
                    {coverage.priority}
                  </Badge>
                  <p className="text-sm font-semibold text-gray-900">
                    {coverage.carrierName} — {coverage.planName}
                  </p>
                  <Badge tone="gray">{coverage.planType}</Badge>
                </div>
                <p className="mt-1 text-xs text-gray-500">
                  Member ID {coverage.memberId} · Subscriber{' '}
                  {coverage.relationshipToSubscriber === 'SELF'
                    ? 'self'
                    : `${coverage.subscriberLastName}, ${coverage.subscriberFirstName} (${coverage.relationshipToSubscriber})`}
                  {coverage.effectiveDate && ` · Effective ${coverage.effectiveDate}`}
                  {coverage.terminationDate && ` · Terminates ${coverage.terminationDate}`}
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
                    Open claim
                  </Button>
                )}
                {canWrite && (
                  <Button
                    variant="ghost"
                    onClick={() => removeCoverage.mutate(coverage.id)}
                    disabled={removeCoverage.isPending}
                  >
                    Remove
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
    if (!planId) return setError('Select a plan');
    if (!memberId.trim()) return setError('Member ID is required');
    const subscriber = relationship === 'SELF' ? patientId : subscriberId;
    if (!subscriber) return setError('Select the subscriber');
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
      setError(e instanceof ApiError ? e.message : 'Failed to add coverage');
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
            Carrier
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
            <option value="">Select…</option>
            {carriers?.content.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="cov-plan" className="block text-sm font-medium text-gray-700">
            Plan
          </label>
          <select
            id="cov-plan"
            value={planId}
            onChange={(e) => setPlanId(e.target.value)}
            disabled={!carrierId}
            className={selectClass}
          >
            <option value="">Select…</option>
            {plans?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.planName} ({p.planType})
              </option>
            ))}
          </select>
        </div>
        <Input
          label="Member ID"
          value={memberId}
          onChange={(e) => setMemberId(e.target.value)}
        />
        <div>
          <label htmlFor="cov-priority" className="block text-sm font-medium text-gray-700">
            Priority
          </label>
          <select
            id="cov-priority"
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            className={selectClass}
          >
            <option value="PRIMARY">Primary</option>
            <option value="SECONDARY">Secondary</option>
          </select>
        </div>
        <div>
          <label htmlFor="cov-rel" className="block text-sm font-medium text-gray-700">
            Patient's relationship to subscriber
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
            <option value="SELF">Self</option>
            <option value="SPOUSE">Spouse</option>
            <option value="CHILD">Child</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        {relationship !== 'SELF' && (
          <div>
            <label htmlFor="cov-subscriber" className="block text-sm font-medium text-gray-700">
              Subscriber (patient)
            </label>
            <input
              id="cov-subscriber"
              type="search"
              value={subscriberSearch}
              onChange={(e) => {
                setSubscriberSearch(e.target.value);
                setSubscriberId('');
              }}
              placeholder="Search patients…"
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
                        {p.lastName}, {p.firstName} ({p.dateOfBirth})
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
          Add coverage
        </Button>
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
