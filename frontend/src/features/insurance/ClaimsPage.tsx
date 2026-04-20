import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import type { Claim, ClaimStatus } from '../../types/api';
import { useProcedureCodes } from '../procedures/api';
import {
  CLAIM_NEXT_STATUSES,
  useAddClaimLine,
  useClaims,
  useRecordClaimPayment,
  useRemoveClaimLine,
  useUpdateClaimStatus,
} from './api';

const STATUSES: Array<ClaimStatus | ''> = ['', 'DRAFT', 'SUBMITTED', 'ACCEPTED', 'DENIED', 'PAID', 'CLOSED'];

const statusTone: Record<ClaimStatus, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  DRAFT: 'gray',
  SUBMITTED: 'blue',
  ACCEPTED: 'yellow',
  DENIED: 'red',
  PAID: 'green',
  CLOSED: 'gray',
};

const money = (n: number) => `$${n.toFixed(2)}`;

export function ClaimsPage() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [openClaimId, setOpenClaimId] = useState<string | null>(null);
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'BILLING');

  const { data, isPending, isError } = useClaims(status, page);

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-gray-900">Claims</h1>
      <p className="mt-1 text-sm text-gray-500">
        Claims are opened from a patient's Insurance tab coverage.
      </p>

      <div className="mt-4 flex gap-1">
        {STATUSES.map((s) => (
          <button
            key={s || 'ALL'}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              status === s ? 'bg-brand-600 text-white' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {s || 'All'}
          </button>
        ))}
      </div>

      <div className="mt-4 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label="Loading claims…" />
        ) : isError ? (
          <p className="p-8 text-sm text-red-600">Failed to load claims.</p>
        ) : data.content.length === 0 ? (
          <p className="p-8 text-center text-sm text-gray-500">No claims found.</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {data.content.map((claim) => (
              <li key={claim.id}>
                <button
                  onClick={() => setOpenClaimId(openClaimId === claim.id ? null : claim.id)}
                  className="flex w-full flex-wrap items-center justify-between gap-3 px-4 py-3 text-left hover:bg-gray-50"
                >
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {claim.patientLastName}, {claim.patientFirstName}
                      <span className="ml-2 text-xs text-gray-500">
                        {claim.carrierName} · {claim.planName} · #{claim.memberId}
                      </span>
                    </p>
                    <p className="text-xs text-gray-500">
                      Billed {money(claim.totalBilled)} · Paid {money(claim.totalPaid)}
                      {claim.submittedAt &&
                        ` · Submitted ${new Date(claim.submittedAt).toLocaleDateString()}`}
                    </p>
                  </div>
                  <Badge tone={statusTone[claim.status]}>{claim.status}</Badge>
                </button>
                {openClaimId === claim.id && <ClaimDetail claim={claim} canWrite={canWrite} />}
              </li>
            ))}
          </ul>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={data.page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function ClaimDetail({ claim, canWrite }: { claim: Claim; canWrite: boolean }) {
  const addLine = useAddClaimLine();
  const removeLine = useRemoveClaimLine();
  const recordPayment = useRecordClaimPayment();
  const updateStatus = useUpdateClaimStatus();
  const [codeSearch, setCodeSearch] = useState('');
  const [payments, setPayments] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  const isDraft = claim.status === 'DRAFT';
  const isAccepted = claim.status === 'ACCEPTED';

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed');
    }
  };

  return (
    <div className="space-y-3 border-t border-gray-100 bg-gray-50/50 px-4 py-3">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <p className="text-xs text-gray-500">
        Patient:{' '}
        <Link to={`/patients/${claim.patientId}`} className="text-brand-600 hover:underline">
          {claim.patientLastName}, {claim.patientFirstName}
        </Link>
        {claim.notes && ` · ${claim.notes}`}
      </p>

      {claim.procedures.length > 0 && (
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-1 pr-3">Code</th>
              <th className="py-1 pr-3">Description</th>
              <th className="py-1 pr-3">Billed</th>
              <th className="py-1 pr-3">Paid</th>
              <th className="py-1" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {claim.procedures.map((line) => (
              <tr key={line.id}>
                <td className="py-2 pr-3 font-mono">{line.code}</td>
                <td className="py-2 pr-3">{line.description}</td>
                <td className="py-2 pr-3">{money(line.billedAmount)}</td>
                <td className="py-2 pr-3">
                  {isAccepted && canWrite ? (
                    <span className="flex items-center gap-1">
                      <input
                        type="number"
                        min={0}
                        max={line.billedAmount}
                        step="0.01"
                        value={payments[line.id] ?? String(line.paidAmount)}
                        onChange={(e) =>
                          setPayments((p) => ({ ...p, [line.id]: e.target.value }))
                        }
                        aria-label={`Paid amount for ${line.code}`}
                        className="w-24 rounded-md border-0 px-2 py-1 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
                      />
                      <Button
                        variant="ghost"
                        onClick={() =>
                          act(() =>
                            recordPayment.mutateAsync({
                              claimId: claim.id,
                              lineId: line.id,
                              paidAmount: Number(payments[line.id] ?? line.paidAmount),
                            }),
                          )
                        }
                      >
                        Save
                      </Button>
                    </span>
                  ) : (
                    money(line.paidAmount)
                  )}
                </td>
                <td className="py-2 text-right">
                  {isDraft && canWrite && (
                    <button
                      onClick={() =>
                        act(() =>
                          removeLine.mutateAsync({ claimId: claim.id, lineId: line.id }),
                        )
                      }
                      className="text-xs text-red-600 hover:underline"
                    >
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {isDraft && canWrite && (
        <div className="max-w-md">
          <input
            type="search"
            value={codeSearch}
            onChange={(e) => setCodeSearch(e.target.value)}
            placeholder="Add line item (search catalog)…"
            aria-label="Add claim line"
            className="block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
          {codeSearch && (
            <ul className="mt-1 max-h-32 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
              {catalog?.content.slice(0, 6).map((entry) => (
                <li key={entry.id}>
                  <button
                    type="button"
                    onClick={() =>
                      act(async () => {
                        await addLine.mutateAsync({
                          claimId: claim.id,
                          procedureCodeId: entry.id,
                        });
                        setCodeSearch('');
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
      )}

      {canWrite && CLAIM_NEXT_STATUSES[claim.status].length > 0 && (
        <div className="flex flex-wrap gap-2 pt-1">
          {CLAIM_NEXT_STATUSES[claim.status].map((next) => (
            <Button
              key={next}
              variant={next === 'DENIED' || next === 'CLOSED' ? 'danger' : 'secondary'}
              onClick={() => act(() => updateStatus.mutateAsync({ claimId: claim.id, status: next }))}
            >
              {next === 'SUBMITTED' && claim.status === 'DENIED' ? 'Resubmit' : next}
            </Button>
          ))}
        </div>
      )}
    </div>
  );
}
