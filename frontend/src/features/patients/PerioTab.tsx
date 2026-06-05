import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import {
  useCreatePerioExam,
  usePerioExam,
  usePerioExams,
  useSavePerio,
  useToothChart,
} from './api';

const UPPER = Array.from({ length: 16 }, (_, i) => String(i + 1)); // 1..16
const LOWER = Array.from({ length: 16 }, (_, i) => String(32 - i)); // 32..17
const FACIAL_SITES = [1, 2, 3]; // DB B MB
const LINGUAL_SITES = [4, 5, 6]; // DL L ML

type CellMap = Map<string, { pd: string; bleeding: boolean }>;

const key = (tooth: string, site: number) => `${tooth}:${site}`;

function depthClass(pd: string): string {
  const value = Number(pd);
  if (!pd || Number.isNaN(value)) return 'bg-white';
  if (value >= 6) return 'bg-red-200';
  if (value >= 4) return 'bg-amber-200';
  return 'bg-green-50';
}

export function PerioTab({ patientId, canChart }: { patientId: string; canChart: boolean }) {
  const { t } = useTranslation('chart');
  const { data: exams, isPending } = usePerioExams(patientId);
  const createExam = useCreatePerioExam(patientId);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [compareId, setCompareId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // auto-select the newest exam
  useEffect(() => {
    if (!selectedId && exams && exams.length > 0) {
      setSelectedId(exams[0].id);
    }
  }, [exams, selectedId]);

  if (isPending) return <Spinner label={t('perio.loadingHistory')} />;

  const selectedSummary = exams?.find((e) => e.id === selectedId);
  const priorExams = exams?.filter((e) => e.id !== selectedId) ?? [];

  return (
    <div className="space-y-4">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-3">
        {canChart && (
          <Button
            loading={createExam.isPending}
            onClick={async () => {
              setError(null);
              try {
                const exam = await createExam.mutateAsync();
                setSelectedId(exam.id);
              } catch (e) {
                setError(e instanceof ApiError ? e.message : t('perio.createFailed'));
              }
            }}
          >
            {t('perio.newExam')}
          </Button>
        )}
        {exams && exams.length > 0 && (
          <>
            <label htmlFor="perio-exam" className="text-sm text-gray-700">
              {t('perio.exam')}
            </label>
            <select
              id="perio-exam"
              value={selectedId ?? ''}
              onChange={(e) => {
                setSelectedId(e.target.value);
                setCompareId(null);
              }}
              className="rounded-md border-0 px-3 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              {exams.map((exam) => (
                <option key={exam.id} value={exam.id}>
                  {exam.examDate}
                </option>
              ))}
            </select>
            {priorExams.length > 0 && (
              <>
                <label htmlFor="perio-compare" className="text-sm text-gray-700">
                  {t('perio.compareWith')}
                </label>
                <select
                  id="perio-compare"
                  value={compareId ?? ''}
                  onChange={(e) => setCompareId(e.target.value || null)}
                  className="rounded-md border-0 px-3 py-1.5 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
                >
                  <option value="">—</option>
                  {priorExams.map((exam) => (
                    <option key={exam.id} value={exam.id}>
                      {exam.examDate}
                    </option>
                  ))}
                </select>
              </>
            )}
          </>
        )}
        {selectedSummary && selectedSummary.sitesRecorded > 0 && (
          <span className="flex gap-2">
            <Badge tone="red">
              {t('perio.bleedingCount', { count: selectedSummary.bleedingSites })}
            </Badge>
            <Badge tone="yellow">
              {t('perio.sites4mm', { count: selectedSummary.sites4mmPlus })}
            </Badge>
            <Badge tone="gray">
              {t('perio.sites6mm', { count: selectedSummary.sites6mmPlus })}
            </Badge>
          </span>
        )}
      </div>

      {!selectedId ? (
        <p className="text-sm text-gray-500">
          {canChart ? t('perio.noExamsStart') : t('perio.noExams')}
        </p>
      ) : (
        <PerioGrid
          patientId={patientId}
          examId={selectedId}
          compareId={compareId}
          canChart={canChart}
          onError={setError}
        />
      )}
    </div>
  );
}

function PerioGrid({
  patientId,
  examId,
  compareId,
  canChart,
  onError,
}: {
  patientId: string;
  examId: string;
  compareId: string | null;
  canChart: boolean;
  onError: (message: string | null) => void;
}) {
  const { t } = useTranslation('chart');
  const { data: exam, isPending } = usePerioExam(patientId, examId);
  const { data: compareExam } = usePerioExam(patientId, compareId);
  const { data: chart } = useToothChart(patientId);
  const savePerio = useSavePerio(patientId);

  const [cells, setCells] = useState<CellMap>(new Map());
  const [mobility, setMobility] = useState<Map<string, string>>(new Map());
  const [dirty, setDirty] = useState(false);
  const gridRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!exam) return;
    const next: CellMap = new Map();
    exam.measurements.forEach((m) => {
      next.set(key(m.tooth, m.site), {
        pd: m.pocketDepth == null ? '' : String(m.pocketDepth),
        bleeding: m.bleeding,
      });
    });
    const nextMobility = new Map<string, string>();
    exam.toothFindings.forEach((f) => {
      if (f.mobility != null) nextMobility.set(f.tooth, String(f.mobility));
    });
    setCells(next);
    setMobility(nextMobility);
    setDirty(false);
  }, [exam]);

  const compareMap = useMemo(() => {
    const map = new Map<string, number>();
    compareExam?.measurements.forEach((m) => {
      if (m.pocketDepth != null) map.set(key(m.tooth, m.site), m.pocketDepth);
    });
    return map;
  }, [compareExam]);

  const missingTeeth = useMemo(
    () =>
      new Set(
        chart?.conditions
          .filter((c) => c.condition === 'MISSING' && c.status === 'ACTIVE')
          .map((c) => c.tooth),
      ),
    [chart],
  );

  if (isPending || !exam) return <Spinner label={t('perio.loadingExam')} />;

  const setCell = (tooth: string, site: number, value: Partial<{ pd: string; bleeding: boolean }>) => {
    setCells((current) => {
      const next = new Map(current);
      const existing = next.get(key(tooth, site)) ?? { pd: '', bleeding: false };
      next.set(key(tooth, site), { ...existing, ...value });
      return next;
    });
    setDirty(true);
  };

  const focusNext = (input: HTMLInputElement) => {
    const inputs = gridRef.current?.querySelectorAll<HTMLInputElement>('input[data-perio]');
    if (!inputs) return;
    const list = Array.from(inputs);
    const index = list.indexOf(input);
    if (index >= 0 && index < list.length - 1) list[index + 1].focus();
  };

  const save = async () => {
    onError(null);
    const measurements = Array.from(cells.entries())
      .map(([cellKey, value]) => {
        const [tooth, site] = cellKey.split(':');
        return {
          tooth,
          site: Number(site),
          pocketDepth: value.pd === '' ? null : Number(value.pd),
          bleeding: value.bleeding,
        };
      })
      .filter((m) => m.pocketDepth != null || m.bleeding);
    const toothFindings = Array.from(mobility.entries())
      .filter(([, value]) => value !== '')
      .map(([tooth, value]) => ({ tooth, mobility: Number(value) }));
    try {
      await savePerio.mutateAsync({ examId, measurements, toothFindings });
      setDirty(false);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : t('perio.saveFailed'));
    }
  };

  const renderSiteRow = (teeth: string[], sites: number[], label: string) => (
    <tr>
      <td className="whitespace-nowrap pr-2 text-xs font-medium text-gray-500">{label}</td>
      {teeth.map((tooth) => {
        const missing = missingTeeth.has(tooth);
        return (
          <td key={tooth} className={`px-0.5 py-0.5 ${missing ? 'opacity-30' : ''}`}>
            <div className="flex gap-px">
              {sites.map((site) => {
                const cell = cells.get(key(tooth, site)) ?? { pd: '', bleeding: false };
                const compareValue = compareMap.get(key(tooth, site));
                return (
                  <div key={site} className="flex flex-col items-center">
                    <input
                      data-perio
                      disabled={!canChart || missing}
                      value={cell.pd}
                      onChange={(e) => {
                        const value = e.target.value.replace(/[^0-9]/g, '').slice(0, 2);
                        setCell(tooth, site, { pd: value });
                        if (value.length === 1 && value !== '1') focusNext(e.target);
                        if (value.length === 2) focusNext(e.target);
                      }}
                      aria-label={t('perio.pdAria', { tooth, site })}
                      className={`h-6 w-6 rounded-sm border border-gray-200 text-center text-xs ${depthClass(cell.pd)}`}
                    />
                    <button
                      type="button"
                      tabIndex={-1}
                      disabled={!canChart || missing}
                      onClick={() => setCell(tooth, site, { bleeding: !cell.bleeding })}
                      aria-label={t('perio.bleedingAria', { tooth, site })}
                      className={`mt-px h-1.5 w-1.5 rounded-full ${
                        cell.bleeding ? 'bg-red-500' : 'bg-gray-200 hover:bg-gray-300'
                      }`}
                    />
                    {compareId && (
                      <span className="text-[9px] leading-tight text-gray-400">
                        {compareValue ?? '·'}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </td>
        );
      })}
    </tr>
  );

  const renderMobilityRow = (teeth: string[]) => (
    <tr>
      <td className="pr-2 text-xs font-medium text-gray-500">{t('perio.mobility')}</td>
      {teeth.map((tooth) => (
        <td key={tooth} className="px-0.5 text-center">
          <input
            disabled={!canChart || missingTeeth.has(tooth)}
            value={mobility.get(tooth) ?? ''}
            onChange={(e) => {
              const value = e.target.value.replace(/[^0-3]/g, '').slice(0, 1);
              setMobility((current) => new Map(current).set(tooth, value));
              setDirty(true);
            }}
            aria-label={t('perio.mobilityAria', { tooth })}
            className="h-5 w-6 rounded-sm border border-gray-200 text-center text-xs"
          />
        </td>
      ))}
    </tr>
  );

  const renderArch = (teeth: string[], title: string) => (
    <table className="border-separate border-spacing-0">
      <thead>
        <tr>
          <td className="pr-2 text-xs font-semibold text-gray-700">{title}</td>
          {teeth.map((tooth) => (
            <th key={tooth} className="px-0.5 text-center text-xs font-semibold text-gray-700">
              {tooth}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {renderSiteRow(teeth, FACIAL_SITES, t('perio.facial'))}
        {renderSiteRow(teeth, LINGUAL_SITES, t('perio.lingual'))}
        {renderMobilityRow(teeth)}
      </tbody>
    </table>
  );

  return (
    <div className="space-y-4">
      <div ref={gridRef} className="space-y-6 overflow-x-auto pb-2">
        {renderArch(UPPER, t('perio.upper'))}
        {renderArch(LOWER, t('perio.lower'))}
      </div>
      <p className="text-xs text-gray-500">
        {t('perio.help')} <span className="rounded bg-amber-200 px-1">4–5 mm</span>{' '}
        <span className="rounded bg-red-200 px-1">≥6 mm</span>
        {compareId && ` · ${t('perio.helpCompare')}`}
      </p>
      {canChart && (
        <Button onClick={save} loading={savePerio.isPending} disabled={!dirty}>
          {t('perio.saveExam')}
        </Button>
      )}
    </div>
  );
}
