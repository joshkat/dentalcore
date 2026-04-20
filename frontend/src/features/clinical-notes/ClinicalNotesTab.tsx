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
  useSignNote,
  useUpdateNote,
} from './api';

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
        <Button onClick={() => setAdding(true)}>New clinical note</Button>
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
