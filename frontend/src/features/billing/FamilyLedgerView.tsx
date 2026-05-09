import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { downloadFamilyStatement, type FamilyLedgerResponse, type LedgerEntryType } from './api';

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

const money = (n: number) => `${n < 0 ? '−' : ''}$${Math.abs(n).toFixed(2)}`;

export function FamilyLedgerView({
  ledger,
  currentPatientId,
}: {
  ledger: FamilyLedgerResponse;
  currentPatientId: string;
}) {
  const [error, setError] = useState<string | null>(null);
  const owes = ledger.totalBalance > 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-gray-50 p-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            Family balance — guarantor {ledger.guarantorName}
          </p>
          <p
            data-testid="family-total-balance"
            className={`text-2xl font-bold ${
              owes ? 'text-red-600' : ledger.totalBalance < 0 ? 'text-green-600' : 'text-gray-900'
            }`}
          >
            {money(ledger.totalBalance)}
          </p>
        </div>
        <Button
          variant="secondary"
          onClick={async () => {
            setError(null);
            try {
              await downloadFamilyStatement(ledger.guarantorId);
            } catch {
              setError('Family statement download failed');
            }
          }}
        >
          Family statement (PDF)
        </Button>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-2" data-testid="family-members">
        {ledger.members.map((member) => (
          <Link
            key={member.patientId}
            to={`/patients/${member.patientId}`}
            className="hover:opacity-80"
            aria-label={`${member.patientName} balance ${money(member.balance)}`}
          >
            <Badge tone={member.balance > 0 ? 'yellow' : 'green'}>
              {member.patientName}
              {member.patientId === currentPatientId ? ' (this patient)' : ''} ·{' '}
              {money(member.balance)}
            </Badge>
          </Link>
        ))}
      </div>

      {ledger.entries.length === 0 ? (
        <p className="text-sm text-gray-500">No family ledger activity yet.</p>
      ) : (
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-2 pr-3">Date</th>
              <th className="py-2 pr-3">Patient</th>
              <th className="py-2 pr-3">Type</th>
              <th className="py-2 pr-3">Description</th>
              <th className="py-2 text-right">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {ledger.entries.map((entry) => (
              <tr key={entry.id} className={entry.reversed ? 'opacity-50' : ''}>
                <td className="py-2 pr-3 whitespace-nowrap text-gray-600">{entry.entryDate}</td>
                <td className="py-2 pr-3">
                  <Link
                    to={`/patients/${entry.patientId}`}
                    className="font-medium text-brand-700 hover:underline"
                  >
                    {entry.patientName}
                  </Link>
                </td>
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
                  className={`py-2 text-right font-medium ${
                    entry.amount < 0 ? 'text-green-700' : 'text-gray-900'
                  }`}
                >
                  {money(entry.amount)}
                </td>
              </tr>
            ))}
            <tr className="font-semibold">
              <td className="py-2 pr-3" colSpan={4}>
                Family total
              </td>
              <td className="py-2 text-right">{money(ledger.totalBalance)}</td>
            </tr>
          </tbody>
        </table>
      )}
    </div>
  );
}
