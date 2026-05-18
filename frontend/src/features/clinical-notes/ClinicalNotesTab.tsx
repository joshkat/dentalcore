import { useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import type { ClinicalNote } from '../../types/api';
import {
  useClinicalNotes,
  useCreateNote,
  useDeleteNote,
  useNoteTemplates,
  useSignNote,
  useUpdateNote,
} from './api';
import { NoteTemplatesModal } from './NoteTemplatesModal';
import { interpolateTemplate } from './noteTemplates';

const NOTE_TYPES = ['EXAM', 'PROGRESS', 'PROCEDURE', 'PHONE', 'OTHER'] as const;

const selectClass =
  'mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300';

export function ClinicalNotesTab({
  patientId,
  canWriteClinical,
}: {
  patientId: string;
  canWriteClinical: boolean;
}) {
  const { data: notes, isPending } = useClinicalNotes(patientId);
  const createNote = useCreateNote(patientId);
  const [adding, setAdding] = useState(false);
  const [noteType, setNoteType] = useState<string>('PROGRESS');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [managingTemplates, setManagingTemplates] = useState(false);

  if (isPending) return <Spinner label="Loading notes…" />;

  const submit = async () => {
    if (!body.trim()) return setError('Note text is required');
    setError(null);
    try {
      await createNote.mutateAsync({ noteType, body: body.trim() });
      setBody('');
      setAdding(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to save note');
    }
  };

  return (
    <div className="space-y-4">
      {canWriteClinical && !adding && (
        <div className="flex gap-2">
          <Button onClick={() => setAdding(true)}>New clinical note</Button>
          <Button variant="secondary" onClick={() => setManagingTemplates(true)}>
            Manage templates
          </Button>
        </div>
      )}
      {adding && (
        <div className="space-y-3 rounded-md bg-gray-50 p-4">
          {error && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}
          <div>
            <label htmlFor="note-type" className="block text-sm font-medium text-gray-700">
              Type
            </label>
            <select
              id="note-type"
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
          <NoteTemplatePicker
            noteType={noteType}
            onInsert={(text) => setBody((prev) => (prev.trim() ? `${prev}\n${text}` : text))}
          />
          <div>
            <label htmlFor="note-body" className="block text-sm font-medium text-gray-700">
              Note
            </label>
            <textarea
              id="note-body"
              rows={4}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <div className="flex gap-2">
            <Button onClick={submit} loading={createNote.isPending}>
              Save note
            </Button>
            <Button variant="secondary" onClick={() => setAdding(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      {notes && notes.content.length === 0 ? (
        <p className="text-sm text-gray-500">No clinical notes on record.</p>
      ) : (
        <ul className="space-y-3">
          {notes?.content.map((note) => (
            <NoteCard key={note.id} note={note} canWriteClinical={canWriteClinical} />
          ))}
        </ul>
      )}

      {managingTemplates && <NoteTemplatesModal onClose={() => setManagingTemplates(false)} />}
    </div>
  );
}

/**
 * "Use template" picker for the composer: select a template, fill its
 * {{placeholder}} prompts, then insert the interpolated text into the body.
 */
function NoteTemplatePicker({
  noteType,
  onInsert,
}: {
  noteType: string;
  onInsert: (text: string) => void;
}) {
  const { data: templates } = useNoteTemplates();
  const [templateId, setTemplateId] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});

  if (!templates || templates.length === 0) return null;

  const matching = templates.filter((t) => t.noteType === noteType);
  const others = templates.filter((t) => t.noteType !== noteType);
  const selected = templates.find((t) => t.id === templateId) ?? null;

  return (
    <div className="space-y-2 rounded-md ring-1 ring-gray-200 p-3">
      <div>
        <label htmlFor="note-template" className="block text-sm font-medium text-gray-700">
          Use template
        </label>
        <select
          id="note-template"
          value={templateId}
          onChange={(e) => {
            setTemplateId(e.target.value);
            setValues({});
          }}
          className={selectClass}
        >
          <option value="">No template</option>
          {matching.length > 0 && (
            <optgroup label={`${noteType} templates`}>
              {matching.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </optgroup>
          )}
          {others.length > 0 && (
            <optgroup label="Other types">
              {others.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.noteType})
                </option>
              ))}
            </optgroup>
          )}
        </select>
      </div>

      {selected && selected.prompts.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {selected.prompts.map((prompt) => (
            <div key={prompt}>
              <label
                htmlFor={`prompt-${prompt}`}
                className="block text-xs font-medium text-gray-600"
              >
                {prompt}
              </label>
              <input
                id={`prompt-${prompt}`}
                value={values[prompt] ?? ''}
                onChange={(e) =>
                  setValues((prev) => ({ ...prev, [prompt]: e.target.value }))
                }
                className="mt-0.5 w-32 rounded-md border-0 px-2 py-1 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
              />
            </div>
          ))}
        </div>
      )}

      {selected && (
        <Button
          variant="secondary"
          onClick={() => {
            onInsert(interpolateTemplate(selected.body, values));
            setTemplateId('');
            setValues({});
          }}
        >
          Insert
        </Button>
      )}
    </div>
  );
}

function NoteCard({
  note,
  canWriteClinical,
}: {
  note: ClinicalNote;
  canWriteClinical: boolean;
}) {
  const signNote = useSignNote();
  const deleteNote = useDeleteNote();
  const updateNote = useUpdateNote();
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(note.body);
  const [error, setError] = useState<string | null>(null);

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed');
    }
  };

  return (
    <li className="rounded-md p-4 ring-1 ring-gray-200">
      {error && (
        <p role="alert" className="mb-2 text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Badge tone="blue">{note.noteType}</Badge>
          {note.signedAt ? (
            <Badge tone="green">SIGNED {new Date(note.signedAt).toLocaleDateString()}</Badge>
          ) : (
            <Badge tone="yellow">UNSIGNED</Badge>
          )}
        </div>
        <span className="text-xs text-gray-500">
          {new Date(note.createdAt).toLocaleString()}
        </span>
      </div>

      {editing ? (
        <div className="mt-3 space-y-2">
          <textarea
            rows={4}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            aria-label="Edit note"
            className="block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
          />
          <div className="flex gap-2">
            <Button
              onClick={() =>
                act(async () => {
                  await updateNote.mutateAsync({ id: note.id, noteType: note.noteType, body });
                  setEditing(false);
                })
              }
            >
              Save
            </Button>
            <Button variant="secondary" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <p className="mt-3 whitespace-pre-wrap text-sm text-gray-800">{note.body}</p>
      )}

      {canWriteClinical && !note.signedAt && !editing && (
        <div className="mt-3 flex gap-2 border-t border-gray-100 pt-3">
          <Button variant="secondary" onClick={() => act(() => signNote.mutateAsync(note.id))}>
            Sign (locks note)
          </Button>
          <Button variant="ghost" onClick={() => setEditing(true)}>
            Edit
          </Button>
          <Button variant="ghost" onClick={() => act(() => deleteNote.mutateAsync(note.id))}>
            Delete
          </Button>
        </div>
      )}
    </li>
  );
}
