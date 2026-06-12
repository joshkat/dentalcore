import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { formatDate, formatDateTime } from '../../i18n/format';
import { ApiError } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import type {
  FormAnswerValue,
  FormField,
  FormInstance,
  FormInstanceStatus,
} from '../../types/api';
import {
  downloadFormPdf,
  useCreateFormInstance,
  useFormInstance,
  useFormInstances,
  useFormTemplates,
  useSaveFormAnswers,
  useSignFormInstance,
} from './api';
import { SignaturePanel, type SignaturePayload } from './SignaturePanel';

const statusTone: Record<FormInstanceStatus, 'yellow' | 'blue' | 'green'> = {
  DRAFT: 'yellow',
  COMPLETED: 'blue',
  SIGNED: 'green',
};

const inputClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-brand-600';

export function PatientFormsTab({ patientId }: { patientId: string }) {
  const { t } = useTranslation('forms');
  const { hasRole } = useAuth();
  const canWrite = hasRole('ADMIN', 'FRONT_DESK', 'DENTIST', 'HYGIENIST');
  const { data: instances, isPending } = useFormInstances(patientId);
  const [openId, setOpenId] = useState<string | null>(null);
  const [picking, setPicking] = useState(false);

  if (isPending) return <Spinner label={t('loadingForms')} />;

  if (openId) {
    return <FormFillView instanceId={openId} canWrite={canWrite} onBack={() => setOpenId(null)} />;
  }

  return (
    <div className="space-y-4">
      {canWrite && <Button onClick={() => setPicking(true)}>{t('newForm')}</Button>}

      {instances && instances.length === 0 ? (
        <p className="text-sm text-gray-500">{t('noForms')}</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
          {instances?.map((instance) => (
            <li key={instance.id}>
              <button
                onClick={() => setOpenId(instance.id)}
                className="flex w-full flex-wrap items-center justify-between gap-3 px-4 py-3 text-left hover:bg-gray-50"
              >
                <div>
                  <p className="text-sm font-medium text-gray-900">{instance.templateName}</p>
                  <p className="text-xs text-gray-500">
                    {t('startedOn', { date: formatDate(instance.createdAt) })}
                    {instance.signedAt &&
                      t('signedByOn', {
                        date: formatDate(instance.signedAt),
                        name: instance.signedByName ?? t('dash'),
                      })}
                  </p>
                </div>
                <Badge tone={statusTone[instance.status]}>
                  {t(`status.${instance.status}`)}
                </Badge>
              </button>
            </li>
          ))}
        </ul>
      )}

      {picking && (
        <TemplatePickerModal
          patientId={patientId}
          onClose={() => setPicking(false)}
          onCreated={(instance) => {
            setPicking(false);
            setOpenId(instance.id);
          }}
        />
      )}
    </div>
  );
}

function TemplatePickerModal({
  patientId,
  onClose,
  onCreated,
}: {
  patientId: string;
  onClose: () => void;
  onCreated: (instance: FormInstance) => void;
}) {
  const { t } = useTranslation('forms');
  const { data: templates, isPending } = useFormTemplates();
  const createInstance = useCreateFormInstance(patientId);
  const [templateId, setTemplateId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const active = (templates ?? []).filter((tpl) => tpl.active);

  const start = async () => {
    if (!templateId) return setError(t('chooseTemplate'));
    setError(null);
    try {
      onCreated(await createInstance.mutateAsync(templateId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('createFormFailed'));
    }
  };

  return (
    <Modal title={t('newForm')} open onClose={onClose}>
      {isPending ? (
        <Spinner label={t('loadingTemplates')} />
      ) : (
        <div className="space-y-4">
          {error && (
            <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
              {error}
            </p>
          )}
          <div>
            <label htmlFor="form-template" className="block text-sm font-medium text-gray-700">
              {t('template')}
            </label>
            <select
              id="form-template"
              value={templateId}
              onChange={(e) => setTemplateId(e.target.value)}
              className={inputClass}
            >
              <option value="">{t('chooseTemplatePlaceholder')}</option>
              {active.map((tpl) => (
                <option key={tpl.id} value={tpl.id}>
                  {tpl.name}
                </option>
              ))}
            </select>
            {active.length === 0 && (
              <p className="mt-1 text-xs text-gray-500">{t('noActiveTemplates')}</p>
            )}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              {t('common:cancel')}
            </Button>
            <Button onClick={start} loading={createInstance.isPending}>
              {t('startForm')}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function FormFillView({
  instanceId,
  canWrite,
  onBack,
}: {
  instanceId: string;
  canWrite: boolean;
  onBack: () => void;
}) {
  const { t } = useTranslation('forms');
  const { data: instance, isPending } = useFormInstance(instanceId);
  const { data: templates } = useFormTemplates();
  const saveAnswers = useSaveFormAnswers();
  const signInstance = useSignFormInstance();
  const [answers, setAnswers] = useState<Record<string, FormAnswerValue>>({});
  const [error, setError] = useState<string | null>(null);

  // hydrate local answers once the instance arrives (and after server flips status)
  useEffect(() => {
    if (instance) setAnswers((prev) => ({ ...instance.answers, ...prev }));
  }, [instance]);

  if (isPending) return <Spinner label={t('loadingForm')} />;
  if (!instance) return <p className="text-sm text-red-600">{t('formNotFound')}</p>;

  const template = templates?.find((tpl) => tpl.id === instance.templateId);
  const fields: FormField[] = template?.fields ?? [];
  const readOnly = instance.status === 'SIGNED' || !canWrite;

  const persist = (next: Record<string, FormAnswerValue>) => {
    setError(null);
    saveAnswers.mutate(
      { id: instance.id, answers: next },
      {
        onError: (e) =>
          setError(e instanceof ApiError ? e.message : t('saveAnswersFailed')),
      },
    );
  };

  const setAnswer = (key: string, value: FormAnswerValue) =>
    setAnswers((prev) => ({ ...prev, [key]: value }));

  const sign = (payload: SignaturePayload) => {
    setError(null);
    signInstance.mutate(
      { id: instance.id, ...payload },
      {
        onError: (e) => setError(e instanceof ApiError ? e.message : t('signFormFailed')),
      },
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={onBack}>
            {t('allForms')}
          </Button>
          <h3 className="text-base font-semibold text-gray-900">{instance.templateName}</h3>
          <Badge tone={statusTone[instance.status]}>{t(`status.${instance.status}`)}</Badge>
        </div>
        {instance.status === 'SIGNED' && instance.documentId && (
          <Button
            variant="secondary"
            onClick={async () => {
              setError(null);
              try {
                await downloadFormPdf(instance.documentId!, `${instance.templateName}.pdf`);
              } catch {
                setError(t('downloadFailed'));
              }
            }}
          >
            {t('viewPdf')}
          </Button>
        )}
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {fields.length === 0 ? (
        <SignedAnswersFallback answers={instance.answers} />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {fields.map((field) => (
            <FieldInput
              key={field.key}
              field={field}
              value={answers[field.key]}
              readOnly={readOnly}
              onChange={(value) => setAnswer(field.key, value)}
              onCommit={(value) => persist({ ...answers, [field.key]: value })}
            />
          ))}
        </div>
      )}

      {saveAnswers.isPending && <p className="text-xs text-gray-400">{t('saving')}</p>}

      {instance.status === 'COMPLETED' && canWrite && (
        <SignaturePanel onSign={sign} signing={signInstance.isPending} />
      )}
      {instance.status === 'DRAFT' && (
        <p className="text-xs text-gray-500">{t('draftHint')}</p>
      )}
      {instance.status === 'SIGNED' && (
        <p className="text-sm text-gray-600">
          {t('signedReadOnly', {
            date: instance.signedAt ? formatDateTime(instance.signedAt) : '',
            name: instance.signedByName ?? t('dash'),
          })}
        </p>
      )}
    </div>
  );
}

function FieldInput({
  field,
  value,
  readOnly,
  onChange,
  onCommit,
}: {
  field: FormField;
  value: FormAnswerValue | undefined;
  readOnly: boolean;
  onChange: (value: FormAnswerValue) => void;
  onCommit: (value: FormAnswerValue) => void;
}) {
  const id = `form-field-${field.key}`;
  const label = (
    <label htmlFor={id} className="block text-sm font-medium text-gray-700">
      {field.label}
      {field.required && <span className="text-red-500"> *</span>}
    </label>
  );
  const text = typeof value === 'string' ? value : '';

  if (field.type === 'CHECKBOX') {
    return (
      <div className="flex items-center gap-2 pt-5">
        <input
          id={id}
          type="checkbox"
          checked={value === true}
          disabled={readOnly}
          onChange={(e) => {
            onChange(e.target.checked);
            onCommit(e.target.checked);
          }}
          className="rounded border-gray-300"
        />
        <label htmlFor={id} className="text-sm font-medium text-gray-700">
          {field.label}
          {field.required && <span className="text-red-500"> *</span>}
        </label>
      </div>
    );
  }

  if (field.type === 'TEXTAREA') {
    return (
      <div className="sm:col-span-2">
        {label}
        <textarea
          id={id}
          rows={3}
          value={text}
          disabled={readOnly}
          onChange={(e) => onChange(e.target.value)}
          onBlur={(e) => onCommit(e.target.value)}
          className={inputClass}
        />
      </div>
    );
  }

  if (field.type === 'SELECT') {
    return (
      <div>
        {label}
        <select
          id={id}
          value={text}
          disabled={readOnly}
          onChange={(e) => {
            onChange(e.target.value);
            onCommit(e.target.value);
          }}
          className={inputClass}
        >
          <option value="">—</option>
          {(field.options ?? []).map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>
    );
  }

  return (
    <div>
      {label}
      <input
        id={id}
        type={field.type === 'DATE' ? 'date' : 'text'}
        value={text}
        disabled={readOnly}
        onChange={(e) => onChange(e.target.value)}
        onBlur={(e) => onCommit(e.target.value)}
        className={inputClass}
      />
    </div>
  );
}

/** Read-only dump when the template (and its field labels) is no longer available. */
function SignedAnswersFallback({ answers }: { answers: Record<string, FormAnswerValue> }) {
  const { t } = useTranslation('forms');
  const entries = Object.entries(answers);
  if (entries.length === 0) return <p className="text-sm text-gray-500">{t('noAnswers')}</p>;
  return (
    <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      {entries.map(([key, value]) => (
        <div key={key}>
          <dt className="text-xs font-semibold uppercase tracking-wide text-gray-500">{key}</dt>
          <dd className="mt-1 text-sm text-gray-900">{String(value)}</dd>
        </div>
      ))}
    </dl>
  );
}
