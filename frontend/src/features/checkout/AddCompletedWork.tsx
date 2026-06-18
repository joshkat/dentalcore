import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { formatMoney } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useProcedureCodes } from '../procedures/api';
import { useProviders } from '../providers/api';
import { useCompleteProcedure } from './api';

const inputClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

interface SelectedCode {
  id: string;
  code: string;
  description: string;
  standardFee: number;
}

/** Ad-hoc "Add completed work" form for the patient Chart tab. */
export function AddCompletedWork({
  patientId,
  defaultProviderId,
}: {
  patientId: string;
  defaultProviderId: string | null;
}) {
  const { t } = useTranslation('checkout');
  const completeProcedure = useCompleteProcedure();
  const { data: providers } = useProviders(false);

  const [codeSearch, setCodeSearch] = useState('');
  const [selected, setSelected] = useState<SelectedCode | null>(null);
  const { data: catalog } = useProcedureCodes(codeSearch);

  const [tooth, setTooth] = useState('');
  const [providerOverride, setProviderOverride] = useState<string | null>(null);
  const [fee, setFee] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [lastCompletedCode, setLastCompletedCode] = useState<string | null>(null);

  const providerId = providerOverride ?? defaultProviderId ?? '';

  const submit = async () => {
    if (!selected) return setError(t('errors.pickProcedure'));
    if (!providerId) return setError(t('errors.selectProvider'));
    const feeValue = Number(fee);
    if (fee.trim() === '' || Number.isNaN(feeValue) || feeValue < 0) {
      return setError(t('errors.enterValidFee'));
    }
    setError(null);
    try {
      await completeProcedure.mutateAsync({
        patientId,
        providerId,
        procedureCodeId: selected.id,
        tooth: tooth.trim() || undefined,
        feeOverride: feeValue,
      });
      setLastCompletedCode(selected.code);
      setSelected(null);
      setCodeSearch('');
      setTooth('');
      setFee('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('errors.failedToRecordWork'));
    }
  };

  return (
    <div className="mt-4 rounded-md bg-gray-50 p-4">
      <h3 className="text-sm font-semibold text-gray-900">{t('addCompletedWork')}</h3>
      {error && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {lastCompletedCode && !error && (
        <p className="mt-2 text-sm text-green-700">
          {t('codeCompleted', { code: lastCompletedCode })}
        </p>
      )}
      <div className="mt-2 flex flex-wrap items-end gap-3">
        <div className="relative min-w-56 flex-1">
          <label htmlFor="adhoc-code" className="block text-sm font-medium text-gray-700">
            {t('procedure')}
          </label>
          <input
            id="adhoc-code"
            type="search"
            value={selected ? `${selected.code} — ${selected.description}` : codeSearch}
            onChange={(e) => {
              setSelected(null);
              setCodeSearch(e.target.value);
            }}
            placeholder={t('searchCatalogPlaceholder')}
            className={inputClass}
          />
          {codeSearch && !selected && (
            <ul className="absolute z-10 mt-1 max-h-40 w-72 overflow-y-auto rounded-md bg-white shadow ring-1 ring-gray-200">
              {catalog?.content.slice(0, 6).map((entry) => (
                <li key={entry.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelected({
                        id: entry.id,
                        code: entry.code,
                        description: entry.description,
                        standardFee: entry.standardFee,
                      });
                      setFee(entry.standardFee.toFixed(2));
                      setLastCompletedCode(null);
                    }}
                    className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-50"
                  >
                    <span className="font-mono">{entry.code}</span> — {entry.description} (
                    {formatMoney(entry.standardFee)})
                  </button>
                </li>
              ))}
              {catalog?.content.length === 0 && (
                <li className="px-3 py-2 text-sm text-gray-500">{t('noMatchingProcedures')}</li>
              )}
            </ul>
          )}
        </div>
        <div className="w-20">
          <label htmlFor="adhoc-tooth" className="block text-sm font-medium text-gray-700">
            {t('tooth')}
          </label>
          <input
            id="adhoc-tooth"
            value={tooth}
            onChange={(e) => setTooth(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="adhoc-provider" className="block text-sm font-medium text-gray-700">
            {t('provider')}
          </label>
          <select
            id="adhoc-provider"
            value={providerId}
            onChange={(e) => setProviderOverride(e.target.value)}
            className={inputClass}
          >
            <option value="">{t('select')}</option>
            {providers?.content.map((p) => (
              <option key={p.id} value={p.id}>
                {p.lastName}, {p.firstName}
              </option>
            ))}
          </select>
        </div>
        <div className="w-28">
          <label htmlFor="adhoc-fee" className="block text-sm font-medium text-gray-700">
            {t('feeLabel')}
          </label>
          <input
            id="adhoc-fee"
            type="number"
            min="0"
            step="0.01"
            value={fee}
            onChange={(e) => setFee(e.target.value)}
            className={inputClass}
          />
        </div>
        <Button onClick={submit} loading={completeProcedure.isPending}>
          {t('complete')}
        </Button>
      </div>
    </div>
  );
}
