import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { formatDate } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import type {
  ChartProcedure,
  ToothCondition,
  ToothConditionType,
} from '../../types/api';
import { AddCompletedWork } from '../checkout/AddCompletedWork';
import { CompleteProcedureButton } from '../checkout/CompleteProcedureButton';
import {
  useAddToothCondition,
  useDeleteToothCondition,
  usePatient,
  useResolveToothCondition,
  useToothChart,
} from './api';
import { LOWER_TEETH, UPPER_TEETH } from './teeth';

// Display labels live in the chart:condition.* catalog entries (per i18n/GUIDE.md)
export const CONDITION_COLORS: Record<ToothConditionType, string> = {
  MISSING: '#9ca3af',
  CARIES: '#dc2626',
  RESTORATION: '#2563eb',
  CROWN: '#7c3aed',
  ROOT_CANAL: '#ea580c',
  IMPLANT: '#0d9488',
  BRIDGE: '#4f46e5',
  VENEER: '#db2777',
  SEALANT: '#65a30d',
  EXTRACTION_PLANNED: '#b91c1c',
  FRACTURE: '#d97706',
  WATCH: '#ca8a04',
  OTHER: '#6b7280',
};

// Display priority when a tooth has several active conditions
const CONDITION_PRIORITY: ToothConditionType[] = [
  'MISSING',
  'EXTRACTION_PLANNED',
  'CARIES',
  'FRACTURE',
  'ROOT_CANAL',
  'CROWN',
  'BRIDGE',
  'IMPLANT',
  'RESTORATION',
  'VENEER',
  'SEALANT',
  'WATCH',
  'OTHER',
];

const SURFACES = ['M', 'O', 'D', 'B', 'L'] as const;

interface ToothState {
  active: ToothCondition[];
  resolved: ToothCondition[];
  procedures: ChartProcedure[];
}

export function ChartTab({ patientId, canChart }: { patientId: string; canChart: boolean }) {
  const { t } = useTranslation('chart');
  const { data: chart, isPending } = useToothChart(patientId);
  const { data: patient } = usePatient(patientId);
  const [selectedTooth, setSelectedTooth] = useState<string | null>(null);
  const defaultProviderId = patient?.primaryProviderId ?? null;

  const byTooth = useMemo(() => {
    const map = new Map<string, ToothState>();
    const get = (tooth: string): ToothState => {
      if (!map.has(tooth)) map.set(tooth, { active: [], resolved: [], procedures: [] });
      return map.get(tooth)!;
    };
    chart?.conditions.forEach((c) =>
      c.status === 'ACTIVE' ? get(c.tooth).active.push(c) : get(c.tooth).resolved.push(c),
    );
    chart?.procedures.forEach((p) => get(p.tooth).procedures.push(p));
    return map;
  }, [chart]);

  if (isPending) return <Spinner label={t('loading')} />;

  const usedConditions = new Set(
    chart?.conditions.filter((c) => c.status === 'ACTIVE').map((c) => c.condition),
  );

  return (
    <div className="flex flex-col gap-6 lg:flex-row">
      <div className="flex-1">
        <svg viewBox="0 0 980 280" role="img" aria-label={t('chartAria')} className="w-full">
          {UPPER_TEETH.map((tooth, i) => (
            <Tooth
              key={tooth}
              tooth={tooth}
              x={20 + i * 60}
              y={20}
              state={byTooth.get(tooth)}
              selected={selectedTooth === tooth}
              onClick={() => setSelectedTooth(selectedTooth === tooth ? null : tooth)}
            />
          ))}
          {LOWER_TEETH.map((tooth, i) => (
            <Tooth
              key={tooth}
              tooth={tooth}
              x={20 + i * 60}
              y={160}
              state={byTooth.get(tooth)}
              selected={selectedTooth === tooth}
              onClick={() => setSelectedTooth(selectedTooth === tooth ? null : tooth)}
            />
          ))}
        </svg>

        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600">
          {CONDITION_PRIORITY.filter((c) => usedConditions.has(c)).map((c) => (
            <span key={c} className="flex items-center gap-1">
              <span
                className="h-3 w-3 rounded-sm"
                style={{ backgroundColor: CONDITION_COLORS[c] }}
              />
              {t(`condition.${c}`)}
            </span>
          ))}
          <span className="flex items-center gap-1">
            <span className="h-3 w-3 rounded-sm border-2 border-dashed border-brand-500" />
            {t('plannedWork')}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-3 w-3 rounded-full bg-green-500" />
            {t('completedWork')}
          </span>
        </div>

        {canChart && (
          <AddCompletedWork patientId={patientId} defaultProviderId={defaultProviderId} />
        )}
      </div>

      <div className="w-full lg:w-96">
        {selectedTooth ? (
          <ToothPanel
            patientId={patientId}
            tooth={selectedTooth}
            state={byTooth.get(selectedTooth) ?? { active: [], resolved: [], procedures: [] }}
            canChart={canChart}
            defaultProviderId={defaultProviderId}
          />
        ) : (
          <p className="rounded-md bg-gray-50 p-4 text-sm text-gray-500">{t('selectTooth')}</p>
        )}
      </div>
    </div>
  );
}

function Tooth({
  tooth,
  x,
  y,
  state,
  selected,
  onClick,
}: {
  tooth: string;
  x: number;
  y: number;
  state: ToothState | undefined;
  selected: boolean;
  onClick: () => void;
}) {
  const { t } = useTranslation('chart');
  const active = state?.active ?? [];
  const top = CONDITION_PRIORITY.find((c) => active.some((a) => a.condition === c));
  const fill = top ? CONDITION_COLORS[top] : '#ffffff';
  const missing = top === 'MISSING';
  const hasPlanned = state?.procedures.some(
    (p) => p.procedureStatus === 'PLANNED' || p.procedureStatus === 'SCHEDULED',
  );
  const hasCompleted = state?.procedures.some((p) => p.procedureStatus === 'COMPLETED');

  return (
    <g
      onClick={onClick}
      role="button"
      aria-label={t('toothAria', { tooth })}
      className="cursor-pointer"
    >
      <rect
        x={x}
        y={y}
        width={44}
        height={64}
        rx={14}
        fill={missing ? '#f3f4f6' : fill}
        fillOpacity={top && !missing ? 0.85 : 1}
        stroke={selected ? '#1d4ed8' : hasPlanned ? '#3b82f6' : '#d1d5db'}
        strokeWidth={selected ? 3 : hasPlanned ? 2.5 : 1.5}
        strokeDasharray={hasPlanned && !selected ? '5 3' : undefined}
      />
      {missing && (
        <>
          <line x1={x + 8} y1={y + 10} x2={x + 36} y2={y + 54} stroke="#6b7280" strokeWidth={2.5} />
          <line x1={x + 36} y1={y + 10} x2={x + 8} y2={y + 54} stroke="#6b7280" strokeWidth={2.5} />
        </>
      )}
      {hasCompleted && <circle cx={x + 36} cy={y + 8} r={5} fill="#22c55e" />}
      <text
        x={x + 22}
        y={y + 84}
        textAnchor="middle"
        fontSize={13}
        fill={selected ? '#1d4ed8' : '#374151'}
        fontWeight={selected ? 700 : 500}
      >
        {tooth}
      </text>
    </g>
  );
}

function ToothPanel({
  patientId,
  tooth,
  state,
  canChart,
  defaultProviderId,
}: {
  patientId: string;
  tooth: string;
  state: ToothState;
  canChart: boolean;
  defaultProviderId: string | null;
}) {
  const { t } = useTranslation('chart');
  const addCondition = useAddToothCondition(patientId);
  const resolveCondition = useResolveToothCondition(patientId);
  const deleteCondition = useDeleteToothCondition(patientId);

  const [conditionType, setConditionType] = useState<ToothConditionType>('CARIES');
  const [surfaces, setSurfaces] = useState<string[]>([]);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('actionFailed'));
    }
  };

  const add = () =>
    act(async () => {
      await addCondition.mutateAsync({
        tooth,
        condition: conditionType,
        surfaces: surfaces.join('') || undefined,
        notes: notes.trim() || undefined,
      });
      setSurfaces([]);
      setNotes('');
    });

  return (
    <div className="space-y-4 rounded-md p-4 ring-1 ring-gray-200">
      <h3 className="text-sm font-semibold text-gray-900">{t('tooth', { tooth })}</h3>
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {state.active.length === 0 && state.procedures.length === 0 && (
        <p className="text-sm text-gray-500">{t('nothingCharted')}</p>
      )}

      {state.active.map((condition) => (
        <div key={condition.id} className="flex items-start justify-between gap-2">
          <div>
            <span className="flex items-center gap-2">
              <span
                className="h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: CONDITION_COLORS[condition.condition] }}
              />
              <span className="text-sm font-medium text-gray-900">
                {t(`condition.${condition.condition}`)}
                {condition.surfaces ? ` (${condition.surfaces})` : ''}
              </span>
            </span>
            {condition.notes && <p className="ml-4 text-xs text-gray-500">{condition.notes}</p>}
            <p className="ml-4 text-xs text-gray-400">{formatDate(condition.createdAt)}</p>
          </div>
          {canChart && (
            <span className="flex shrink-0 gap-2">
              <button
                onClick={() => act(() => resolveCondition.mutateAsync(condition.id))}
                className="text-xs text-brand-600 hover:underline"
              >
                {t('resolve')}
              </button>
              <button
                onClick={() => act(() => deleteCondition.mutateAsync(condition.id))}
                className="text-xs text-red-600 hover:underline"
              >
                {t('common:delete')}
              </button>
            </span>
          )}
        </div>
      ))}

      {state.procedures.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
            {t('plannedCompletedWork')}
          </h4>
          <ul className="mt-1 space-y-1">
            {state.procedures.map((procedure) => (
              <li key={procedure.plannedProcedureId} className="flex items-center gap-2 text-sm">
                <Badge
                  tone={
                    procedure.procedureStatus === 'COMPLETED'
                      ? 'green'
                      : procedure.procedureStatus === 'CANCELLED'
                        ? 'gray'
                        : 'blue'
                  }
                >
                  {t(`procStatus.${procedure.procedureStatus}`)}
                </Badge>
                <span className="font-mono text-xs">{procedure.code}</span>
                <span className="truncate text-xs text-gray-600">
                  {procedure.description} — {procedure.planTitle}
                </span>
                {canChart && procedure.procedureStatus === 'PLANNED' && (
                  <CompleteProcedureButton
                    patientId={patientId}
                    planId={procedure.planId}
                    plannedProcedureId={procedure.plannedProcedureId}
                    tooth={procedure.tooth}
                    surfaces={procedure.surface}
                    defaultProviderId={defaultProviderId}
                  />
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {state.resolved.length > 0 && (
        <details className="text-sm">
          <summary className="cursor-pointer text-xs text-gray-500">
            {t('history', { count: state.resolved.length })}
          </summary>
          <ul className="mt-1 space-y-1">
            {state.resolved.map((condition) => (
              <li key={condition.id} className="text-xs text-gray-500 line-through">
                {t('resolvedEntry', {
                  label: `${t(`condition.${condition.condition}`)}${
                    condition.surfaces ? ` (${condition.surfaces})` : ''
                  }`,
                  date: condition.resolvedAt ? formatDate(condition.resolvedAt) : '',
                })}
              </li>
            ))}
          </ul>
        </details>
      )}

      {canChart && (
        <div className="space-y-3 border-t border-gray-100 pt-3">
          <div>
            <label htmlFor="tooth-condition" className="block text-sm font-medium text-gray-700">
              {t('addCondition')}
            </label>
            <select
              id="tooth-condition"
              value={conditionType}
              onChange={(e) => setConditionType(e.target.value as ToothConditionType)}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              {CONDITION_PRIORITY.map((c) => (
                <option key={c} value={c}>
                  {t(`condition.${c}`)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <span className="block text-sm font-medium text-gray-700">{t('surfaces')}</span>
            <div className="mt-1 flex gap-1">
              {SURFACES.map((surface) => (
                <button
                  key={surface}
                  type="button"
                  onClick={() =>
                    setSurfaces((current) =>
                      current.includes(surface)
                        ? current.filter((s) => s !== surface)
                        : [...current, surface],
                    )
                  }
                  className={`h-9 w-9 rounded-md text-sm font-semibold ring-1 ring-inset ${
                    surfaces.includes(surface)
                      ? 'bg-brand-600 text-white ring-brand-600'
                      : 'bg-white text-gray-700 ring-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {surface}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label htmlFor="tooth-notes" className="block text-sm font-medium text-gray-700">
              {t('notes')}
            </label>
            <input
              id="tooth-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <Button onClick={add} loading={addCondition.isPending}>
            {t('chartCondition')}
          </Button>
        </div>
      )}
    </div>
  );
}
