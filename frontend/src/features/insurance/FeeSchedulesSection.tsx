import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useProcedureCodes } from '../procedures/api';
import {
  useCreateFeeSchedule,
  useFeeSchedule,
  useFeeSchedules,
  useUpsertScheduleFees,
} from './api';

const money = (n: number | null) => (n == null ? '—' : formatMoney(n));

export function FeeSchedulesSection({ canManage }: { canManage: boolean }) {
  const { t } = useTranslation('insurance');
  const { data: schedules, isPending } = useFeeSchedules();
  const createSchedule = useCreateFeeSchedule();
  const [openId, setOpenId] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (isPending) return <Spinner label={t('feeSchedules.loading')} />;

  return (
    <div className="rounded-lg bg-white p-4 shadow">
      <h2 className="text-lg font-semibold text-gray-900">{t('feeSchedules.title')}</h2>
      <p className="text-xs text-gray-500">{t('feeSchedules.description')}</p>
      {error && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {canManage && (
        <div className="mt-3 flex items-end gap-2">
          <div>
            <label htmlFor="fs-name" className="block text-sm font-medium text-gray-700">
              {t('feeSchedules.newSchedule')}
            </label>
            <input
              id="fs-name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder={t('feeSchedules.newSchedulePlaceholder')}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <Button
            loading={createSchedule.isPending}
            onClick={async () => {
              if (!newName.trim()) return;
              setError(null);
              try {
                await createSchedule.mutateAsync({ name: newName.trim() });
                setNewName('');
              } catch (e) {
                setError(e instanceof ApiError ? e.message : t('feeSchedules.failedToCreate'));
              }
            }}
          >
            {t('common:create')}
          </Button>
        </div>
      )}

      <ul className="mt-3 divide-y divide-gray-100">
        {schedules?.map((schedule) => (
          <li key={schedule.id}>
            <button
              onClick={() => setOpenId(openId === schedule.id ? null : schedule.id)}
              className="flex w-full items-center justify-between py-2 text-left hover:bg-gray-50"
            >
              <span className="text-sm font-medium text-gray-900">{schedule.name}</span>
              <span className="text-xs text-gray-500">
                {t('feeSchedules.feeCount', { count: schedule.feeCount })}
              </span>
            </button>
            {openId === schedule.id && (
              <ScheduleFees scheduleId={schedule.id} canManage={canManage} />
            )}
          </li>
        ))}
        {schedules?.length === 0 && (
          <li className="py-2 text-sm text-gray-500">{t('feeSchedules.none')}</li>
        )}
      </ul>
    </div>
  );
}

function ScheduleFees({ scheduleId, canManage }: { scheduleId: string; canManage: boolean }) {
  const { t } = useTranslation('insurance');
  const { data: detail, isPending } = useFeeSchedule(scheduleId);
  const upsertFees = useUpsertScheduleFees(scheduleId);
  const [codeSearch, setCodeSearch] = useState('');
  const [feeInput, setFeeInput] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  if (isPending || !detail) return <Spinner label={t('feeSchedules.loadingFees')} />;

  return (
    <div className="space-y-3 pb-3 pl-2">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      {detail.fees.length > 0 && (
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-semibold uppercase text-gray-500">
              <th className="py-1 pr-3">{t('feeSchedules.columns.code')}</th>
              <th className="py-1 pr-3">{t('feeSchedules.columns.description')}</th>
              <th className="py-1 pr-3 text-right">{t('feeSchedules.columns.standard')}</th>
              <th className="py-1 text-right">{t('feeSchedules.columns.allowed')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {detail.fees.map((fee) => (
              <tr key={fee.procedureCodeId}>
                <td className="py-1.5 pr-3 font-mono">{fee.code}</td>
                <td className="py-1.5 pr-3 text-gray-600">{fee.description}</td>
                <td className="py-1.5 pr-3 text-right text-gray-400">
                  {money(fee.standardFee)}
                </td>
                <td className="py-1.5 text-right font-medium">{money(fee.fee)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {canManage && (
        <div className="flex flex-wrap items-end gap-2">
          <div className="min-w-64 flex-1">
            <input
              type="search"
              value={codeSearch}
              onChange={(e) => setCodeSearch(e.target.value)}
              placeholder={t('feeSchedules.searchPlaceholder')}
              aria-label={t('feeSchedules.searchLabel')}
              className="block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
            {codeSearch && (
              <ul className="mt-1 max-h-32 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
                {catalog?.content.slice(0, 6).map((entry) => (
                  <li key={entry.id}>
                    <button
                      type="button"
                      onClick={async () => {
                        const fee = Number(feeInput);
                        if (!fee || fee < 0) {
                          setError(t('feeSchedules.enterAllowedFirst'));
                          return;
                        }
                        setError(null);
                        try {
                          await upsertFees.mutateAsync([
                            { procedureCodeId: entry.id, fee },
                          ]);
                          setCodeSearch('');
                          setFeeInput('');
                        } catch (e) {
                          setError(e instanceof ApiError ? e.message : t('feeSchedules.failedToSaveFee'));
                        }
                      }}
                      className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                    >
                      <span className="font-mono">{entry.code}</span> — {entry.description} (
                      {t('feeSchedules.stdFee', { amount: money(entry.standardFee) })})
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <input
            type="number"
            min="0"
            step="0.01"
            value={feeInput}
            onChange={(e) => setFeeInput(e.target.value)}
            placeholder={t('feeSchedules.allowedPlaceholder')}
            aria-label={t('feeSchedules.allowedLabel')}
            className="w-28 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
        </div>
      )}
    </div>
  );
}
