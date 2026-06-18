import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatDate } from '../../i18n/format';
import type { DuplicateCandidate, DuplicatePair } from '../../types/api';
import { useDuplicatePatients } from './api';
import { MergePatientsModal } from './MergePatientsModal';

/** Tolerate either a 0–1 ratio or an already-percentage score from the backend. */
export function scorePercent(score: number): number {
  return Math.round(score <= 1 ? score * 100 : score);
}

function Candidate({ candidate }: { candidate: DuplicateCandidate }) {
  const { t } = useTranslation('admin');
  return (
    <div>
      <p className="text-sm font-medium text-gray-900">{candidate.name}</p>
      <p className="text-xs text-gray-500">
        {t('dob', { date: formatDate(candidate.dateOfBirth) })} · {candidate.status}
      </p>
    </div>
  );
}

export function DuplicatePatientsSection() {
  const { t } = useTranslation('admin');
  const { data, isPending, isError } = useDuplicatePatients();
  const [merging, setMerging] = useState<DuplicatePair | null>(null);

  return (
    <section aria-label={t('duplicatesSectionLabel')} className="rounded-lg bg-white p-6 shadow">
      <h2 className="text-lg font-semibold text-gray-900">{t('duplicatesTitle')}</h2>
      <p className="mt-1 text-sm text-gray-500">{t('duplicatesDescription')}</p>

      <div className="mt-4">
        {isPending ? (
          <Spinner label={t('scanningDuplicates')} />
        ) : isError || !data ? (
          <p role="alert" className="text-sm text-red-600">
            {t('duplicatesLoadFailed')}
          </p>
        ) : data.length === 0 ? (
          <p className="text-sm text-gray-500">{t('noDuplicates')}</p>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
            {data.map((pair) => (
              <li
                key={`${pair.first.patientId}-${pair.second.patientId}`}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div className="flex flex-wrap items-center gap-6">
                  <Candidate candidate={pair.first} />
                  <span className="text-gray-400" aria-hidden>
                    ↔
                  </span>
                  <Candidate candidate={pair.second} />
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge tone="blue">{t('percentMatch', { percent: scorePercent(pair.score) })}</Badge>
                    {pair.reasons.map((reason) => (
                      <Badge key={reason}>{reason}</Badge>
                    ))}
                  </div>
                </div>
                <Button variant="secondary" onClick={() => setMerging(pair)}>
                  {t('mergeEllipsis')}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {merging && <MergePatientsModal pair={merging} onClose={() => setMerging(null)} />}
    </section>
  );
}
