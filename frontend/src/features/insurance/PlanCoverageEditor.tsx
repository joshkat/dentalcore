import { useEffect, useState } from 'react';
import { Button } from '../../components/Button';
import { ApiError } from '../../lib/api';
import { PROCEDURE_CATEGORIES } from '../../types/api';
import {
  useFeeSchedules,
  usePlanCoverageRules,
  useSavePlanCoverage,
  type CoverageRuleEntry,
} from './api';

export function PlanCoverageEditor({
  planId,
  currentFeeScheduleId,
  canManage,
}: {
  planId: string;
  currentFeeScheduleId: string | null;
  canManage: boolean;
}) {
  const { data: schedules } = useFeeSchedules();
  const { data: rules } = usePlanCoverageRules(planId);
  const saveCoverage = useSavePlanCoverage(planId);
  const [scheduleId, setScheduleId] = useState<string>(currentFeeScheduleId ?? '');
  const [percents, setPercents] = useState<Map<string, string>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (rules) {
      setPercents(new Map(rules.map((r) => [r.category, String(r.coveragePercent)])));
    }
  }, [rules]);

  const save = async () => {
    setError(null);
    setSaved(false);
    const entries: CoverageRuleEntry[] = Array.from(percents.entries())
      .filter(([, value]) => value !== '')
      .map(([category, value]) => ({ category, coveragePercent: Number(value) }));
    if (entries.some((e) => e.coveragePercent < 0 || e.coveragePercent > 100)) {
      setError('Percentages must be 0–100');
      return;
    }
    try {
      await saveCoverage.mutateAsync({ feeScheduleId: scheduleId || null, rules: entries });
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to save coverage');
    }
  };

  return (
    <div className="mt-2 space-y-3 rounded-md bg-gray-50 p-3">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      {saved && (
        <p role="status" className="text-sm text-green-700">
          Coverage saved.
        </p>
      )}
      <div>
        <label
          htmlFor={`fee-sched-${planId}`}
          className="block text-sm font-medium text-gray-700"
        >
          Fee schedule
        </label>
        <select
          id={`fee-sched-${planId}`}
          value={scheduleId}
          onChange={(e) => setScheduleId(e.target.value)}
          disabled={!canManage}
          className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
        >
          <option value="">None (use standard fees)</option>
          {schedules?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <span className="block text-sm font-medium text-gray-700">
          Coverage % by category (blank = not covered)
        </span>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {PROCEDURE_CATEGORIES.map((category) => (
            <label key={category} className="text-xs text-gray-600">
              {category.replace('_', ' ')}
              <input
                type="number"
                min="0"
                max="100"
                disabled={!canManage}
                value={percents.get(category) ?? ''}
                onChange={(e) =>
                  setPercents((current) => new Map(current).set(category, e.target.value))
                }
                className="mt-0.5 block w-full rounded-md border-0 px-2 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
            </label>
          ))}
        </div>
      </div>
      {canManage && (
        <Button onClick={save} loading={saveCoverage.isPending}>
          Save coverage
        </Button>
      )}
    </div>
  );
}
