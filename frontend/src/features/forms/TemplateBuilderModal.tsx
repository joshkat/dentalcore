import { useState } from 'react';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { ApiError } from '../../lib/api';
import type { FormField, FormFieldType, FormTemplate } from '../../types/api';
import { useCreateFormTemplate, useUpdateFormTemplate } from './api';

const FIELD_TYPES: FormFieldType[] = ['TEXT', 'TEXTAREA', 'CHECKBOX', 'DATE', 'SELECT'];

export interface FieldDraft {
  label: string;
  type: FormFieldType;
  required: boolean;
  /** Comma-separated options, only meaningful for SELECT. */
  options: string;
}

/** Stable machine key derived from the human label. */
export function slugifyKey(label: string): string {
  return label
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

export function parseOptions(raw: string): string[] {
  return raw
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean);
}

/** Returns the first validation problem, or null when the draft is saveable. */
export function validateTemplateDraft(name: string, fields: FieldDraft[]): string | null {
  if (!name.trim()) return 'Template name is required';
  if (fields.length === 0) return 'Add at least one field';
  for (const field of fields) {
    if (!field.label.trim()) return 'Every field needs a label';
    if (field.type === 'SELECT' && parseOptions(field.options).length === 0) {
      return `SELECT field “${field.label.trim()}” needs at least one option`;
    }
  }
  const keys = fields.map((f) => slugifyKey(f.label));
  const dup = keys.find((k, i) => keys.indexOf(k) !== i);
  if (dup) return `Duplicate field keys: “${dup}” — labels must produce unique keys`;
  return null;
}

export function toFormFields(fields: FieldDraft[]): FormField[] {
  return fields.map((f) => ({
    key: slugifyKey(f.label),
    label: f.label.trim(),
    type: f.type,
    required: f.required,
    ...(f.type === 'SELECT' ? { options: parseOptions(f.options) } : {}),
  }));
}

const emptyField = (): FieldDraft => ({ label: '', type: 'TEXT', required: false, options: '' });

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300';

export function TemplateBuilderModal({
  template,
  onClose,
}: {
  /** null = create a new template. */
  template: FormTemplate | null;
  onClose: () => void;
}) {
  const [name, setName] = useState(template?.name ?? '');
  const [description, setDescription] = useState(template?.description ?? '');
  const [fields, setFields] = useState<FieldDraft[]>(
    template
      ? template.fields.map((f) => ({
          label: f.label,
          type: f.type,
          required: f.required,
          options: (f.options ?? []).join(', '),
        }))
      : [emptyField()],
  );
  const [error, setError] = useState<string | null>(null);

  const createTemplate = useCreateFormTemplate();
  const updateTemplate = useUpdateFormTemplate();
  const saving = createTemplate.isPending || updateTemplate.isPending;

  const patch = (index: number, changes: Partial<FieldDraft>) =>
    setFields((prev) => prev.map((f, i) => (i === index ? { ...f, ...changes } : f)));

  const move = (index: number, delta: -1 | 1) =>
    setFields((prev) => {
      const target = index + delta;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });

  const save = async () => {
    const problem = validateTemplateDraft(name, fields);
    if (problem) return setError(problem);
    setError(null);
    const payload = {
      name: name.trim(),
      description: description.trim() || undefined,
      fields: toFormFields(fields),
    };
    try {
      if (template) {
        await updateTemplate.mutateAsync({ id: template.id, ...payload });
      } else {
        await createTemplate.mutateAsync(payload);
      }
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to save template');
    }
  };

  return (
    <Modal
      title={template ? 'Edit form template' : 'New form template'}
      open
      onClose={onClose}
      size="lg"
    >
      <div className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <Input
          label="Template name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. New patient intake"
        />
        <Input
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Optional"
        />

        <div className="space-y-3">
          <p className="text-sm font-medium text-gray-700">Fields</p>
          {fields.map((field, index) => (
            <div key={index} className="space-y-2 rounded-md bg-gray-50 p-3">
              <div className="flex flex-wrap items-end gap-3">
                <Input
                  label={`Field ${index + 1} label`}
                  value={field.label}
                  onChange={(e) => patch(index, { label: e.target.value })}
                  className="min-w-40 flex-1"
                />
                <div>
                  <label
                    htmlFor={`field-type-${index}`}
                    className="block text-sm font-medium text-gray-700"
                  >
                    Type
                  </label>
                  <select
                    id={`field-type-${index}`}
                    value={field.type}
                    onChange={(e) => patch(index, { type: e.target.value as FormFieldType })}
                    className={selectClass}
                  >
                    {FIELD_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <label className="flex items-center gap-2 pb-2 text-sm text-gray-700">
                  <input
                    type="checkbox"
                    checked={field.required}
                    onChange={(e) => patch(index, { required: e.target.checked })}
                    className="rounded border-gray-300"
                  />
                  Required
                </label>
                <div className="flex gap-1 pb-1">
                  <Button
                    variant="ghost"
                    aria-label={`Move field ${index + 1} up`}
                    disabled={index === 0}
                    onClick={() => move(index, -1)}
                  >
                    ↑
                  </Button>
                  <Button
                    variant="ghost"
                    aria-label={`Move field ${index + 1} down`}
                    disabled={index === fields.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    ↓
                  </Button>
                  <Button
                    variant="ghost"
                    aria-label={`Remove field ${index + 1}`}
                    onClick={() => setFields((prev) => prev.filter((_, i) => i !== index))}
                  >
                    ✕
                  </Button>
                </div>
              </div>
              {field.type === 'SELECT' && (
                <Input
                  label={`Field ${index + 1} options (comma-separated)`}
                  value={field.options}
                  onChange={(e) => patch(index, { options: e.target.value })}
                  placeholder="e.g. Yes, No, Unsure"
                />
              )}
              {field.label.trim() && (
                <p className="text-xs text-gray-500">
                  Key: <code>{slugifyKey(field.label)}</code>
                </p>
              )}
            </div>
          ))}
          <Button variant="secondary" onClick={() => setFields((prev) => [...prev, emptyField()])}>
            Add field
          </Button>
        </div>

        <div className="flex justify-end gap-2 border-t border-gray-100 pt-4">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} loading={saving}>
            {template ? 'Save template' : 'Create template'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
