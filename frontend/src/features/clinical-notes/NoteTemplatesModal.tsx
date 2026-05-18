import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import type { NoteTemplate } from '../../types/api';
import {
  useCreateNoteTemplate,
  useDeleteNoteTemplate,
  useNoteTemplates,
  useUpdateNoteTemplate,
} from './api';
import { extractPrompts } from './noteTemplates';

const NOTE_TYPES = ['EXAM', 'PROGRESS', 'PROCEDURE', 'PHONE', 'OTHER'] as const;

const selectClass =
  'mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300';

export function NoteTemplatesModal({ onClose }: { onClose: () => void }) {
  const { data: templates, isPending } = useNoteTemplates();
  const createTemplate = useCreateNoteTemplate();
  const updateTemplate = useUpdateNoteTemplate();
  const deleteTemplate = useDeleteNoteTemplate();

  // null = list view, 'new' = create, otherwise editing that template
  const [editing, setEditing] = useState<NoteTemplate | 'new' | null>(null);
  const [name, setName] = useState('');
  const [noteType, setNoteType] = useState<string>('PROGRESS');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  const startEdit = (template: NoteTemplate | 'new') => {
    setEditing(template);
    setError(null);
    if (template === 'new') {
      setName('');
      setNoteType('PROGRESS');
      setBody('');
    } else {
      setName(template.name);
      setNoteType(template.noteType);
      setBody(template.body);
    }
  };

  const save = async () => {
    if (!name.trim()) return setError('Name is required');
    if (!body.trim()) return setError('Template body is required');
    setError(null);
    const payload = { name: name.trim(), noteType, body };
    try {
      if (editing === 'new') {
        await createTemplate.mutateAsync(payload);
      } else if (editing) {
        await updateTemplate.mutateAsync({ id: editing.id, ...payload });
      }
      setEditing(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to save template');
    }
  };

  const prompts = extractPrompts(body);

  return (
    <Modal title="Note templates" open onClose={onClose} size="lg">
      {isPending ? (
        <Spinner label="Loading templates…" />
      ) : editing ? (
        <div className="space-y-3">
          {error && (
            <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
              {error}
            </p>
          )}
          <Input
            label="Template name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Composite restoration"
          />
          <div>
            <label htmlFor="tmpl-note-type" className="block text-sm font-medium text-gray-700">
              Note type
            </label>
            <select
              id="tmpl-note-type"
              value={noteType}
              onChange={(e) => setNoteType(e.target.value)}
              className={selectClass}
            >
              {NOTE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="tmpl-body" className="block text-sm font-medium text-gray-700">
              Body — use {'{{placeholder}}'} markers for prompts
            </label>
            <textarea
              id="tmpl-body"
              rows={5}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
            {prompts.length > 0 && (
              <p className="mt-1 text-xs text-gray-500">Prompts: {prompts.join(', ')}</p>
            )}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Back
            </Button>
            <Button
              onClick={save}
              loading={createTemplate.isPending || updateTemplate.isPending}
            >
              {editing === 'new' ? 'Create template' : 'Save template'}
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <Button onClick={() => startEdit('new')}>New note template</Button>
          {templates && templates.length === 0 ? (
            <p className="text-sm text-gray-500">No note templates yet.</p>
          ) : (
            <ul className="divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
              {templates?.map((template) => (
                <li
                  key={template.id}
                  className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="flex items-center gap-2">
                    <Badge tone="blue">{template.noteType}</Badge>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{template.name}</p>
                      {template.prompts.length > 0 && (
                        <p className="text-xs text-gray-500">
                          Prompts: {template.prompts.join(', ')}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="ghost" onClick={() => startEdit(template)}>
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => deleteTemplate.mutate(template.id)}
                      disabled={deleteTemplate.isPending}
                    >
                      Delete
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Modal>
  );
}
