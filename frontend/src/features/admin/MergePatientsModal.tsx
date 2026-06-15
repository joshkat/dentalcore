import { useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import { formatDate } from '../../i18n/format';
import type { DuplicatePair, MergeResult } from '../../types/api';
import { useMergePatients } from './api';

const countLabel = (counts: Record<string, number>): string =>
  Object.entries(counts)
    .map(([table, n]) => `${table}: ${n}`)
    .join(', ');

export function MergePatientsModal({
  pair,
  onClose,
}: {
  pair: DuplicatePair;
  onClose: () => void;
}) {
  const { t } = useTranslation('admin');
  const merge = useMergePatients();
  const [keep, setKeep] = useState<'first' | 'second'>('first');
  const [confirmText, setConfirmText] = useState('');
  const [result, setResult] = useState<MergeResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const target = keep === 'first' ? pair.first : pair.second;
  const source = keep === 'first' ? pair.second : pair.first;

  const onConfirm = () => {
    setError(null);
    merge.mutate(
      { targetId: target.patientId, sourceId: source.patientId },
      {
        onSuccess: setResult,
        onError: (e) => setError(e instanceof ApiError ? e.message : t('mergeFailed')),
      },
    );
  };

  return (
    <Modal title={t('mergeTitle')} open onClose={onClose}>
      {result ? (
        <div className="space-y-4">
          <p className="rounded-md bg-green-50 p-3 text-sm text-green-800">
            {t('mergeComplete')}{' '}
            <Trans
              i18nKey="admin:mergedInto"
              values={{ source: source.name, target: target.name }}
              components={[<strong key="source" />, <strong key="target" />]}
            />
          </p>
          <div className="text-sm text-gray-700">
            <p>
              <span className="font-medium">{t('recordsMoved')}</span> —{' '}
              {Object.keys(result.repointed).length === 0
                ? t('none')
                : countLabel(result.repointed)}
            </p>
            {result.skipped && Object.keys(result.skipped).length > 0 && (
              <p className="mt-1">
                <span className="font-medium">{t('skipped')}</span> — {countLabel(result.skipped)}
              </p>
            )}
          </div>
          <div className="flex justify-end">
            <Button onClick={onClose}>{t('close')}</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <fieldset>
            <legend className="text-sm font-medium text-gray-700">
              {t('whichRecordKept')}
            </legend>
            <div className="mt-2 space-y-2">
              {(
                [
                  ['first', pair.first],
                  ['second', pair.second],
                ] as const
              ).map(([key, candidate]) => (
                <label key={key} className="flex items-center gap-2 text-sm text-gray-900">
                  <input
                    type="radio"
                    name="merge-keep"
                    className="h-4 w-4 border-gray-300 text-brand-600 focus:ring-brand-600"
                    checked={keep === key}
                    onChange={() => setKeep(key)}
                  />
                  {t('keepCandidate', {
                    name: candidate.name,
                    dob: formatDate(candidate.dateOfBirth),
                  })}
                </label>
              ))}
            </div>
          </fieldset>

          <p className="rounded-md bg-yellow-50 p-3 text-sm text-yellow-800">
            <Trans
              i18nKey="admin:willArchiveWarning"
              values={{ source: source.name, target: target.name, name: source.name }}
              components={[<strong key="source" />, <strong key="target" />]}
            />
          </p>

          <Input
            label={t('typeMergeToConfirm')}
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="MERGE"
            autoComplete="off"
          />

          {error && (
            <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              {t('cancel')}
            </Button>
            <Button
              variant="danger"
              disabled={confirmText !== 'MERGE'}
              loading={merge.isPending}
              onClick={onConfirm}
            >
              Merge patients
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
