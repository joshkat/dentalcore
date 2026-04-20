import { useRef, useState } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ApiError } from '../../lib/api';
import {
  DOCUMENT_CATEGORIES,
  downloadDocument,
  useDeleteDocument,
  useDocuments,
  useUploadDocument,
  type DocumentCategory,
} from './api';

const categoryTone: Record<DocumentCategory, 'blue' | 'green' | 'yellow' | 'gray' | 'red'> = {
  XRAY: 'blue',
  PHOTO: 'green',
  CONSENT: 'yellow',
  INSURANCE: 'red',
  REFERRAL: 'gray',
  OTHER: 'gray',
};

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function DocumentsTab({
  patientId,
  canWrite,
}: {
  patientId: string;
  canWrite: boolean;
}) {
  const { data: documents, isPending } = useDocuments(patientId);
  const uploadDocument = useUploadDocument(patientId);
  const deleteDocument = useDeleteDocument();
  const fileInput = useRef<HTMLInputElement>(null);
  const [category, setCategory] = useState<string>('OTHER');
  const [error, setError] = useState<string | null>(null);

  const onFileChosen = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    try {
      await uploadDocument.mutateAsync({ file, category });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Upload failed');
    } finally {
      if (fileInput.current) fileInput.current.value = '';
    }
  };

  if (isPending) return <Spinner label="Loading documents…" />;

  return (
    <div className="space-y-4">
      {canWrite && (
        <div className="flex flex-wrap items-end gap-3 rounded-md bg-gray-50 p-4">
          <div>
            <label htmlFor="doc-category" className="block text-sm font-medium text-gray-700">
              Category
            </label>
            <select
              id="doc-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="mt-1 rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            >
              {DOCUMENT_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="application/pdf,image/*,text/plain"
            className="hidden"
            onChange={(e) => onFileChosen(e.target.files?.[0])}
          />
          <Button
            onClick={() => fileInput.current?.click()}
            loading={uploadDocument.isPending}
          >
            Upload file
          </Button>
          <p className="text-xs text-gray-500">PDF, images, or text · max 25 MB</p>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {documents && documents.content.length === 0 ? (
        <p className="text-sm text-gray-500">No documents on file.</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-md ring-1 ring-gray-100">
          {documents?.content.map((doc) => (
            <li
              key={doc.id}
              className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
            >
              <div className="flex items-center gap-3">
                <Badge tone={categoryTone[doc.category]}>{doc.category}</Badge>
                <div>
                  <p className="text-sm font-medium text-gray-900">{doc.filename}</p>
                  <p className="text-xs text-gray-500">
                    {formatSize(doc.sizeBytes)} · {new Date(doc.createdAt).toLocaleString()}
                    {doc.notes ? ` · ${doc.notes}` : ''}
                  </p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  onClick={async () => {
                    setError(null);
                    try {
                      await downloadDocument(doc);
                    } catch {
                      setError('Download failed');
                    }
                  }}
                >
                  Download
                </Button>
                {canWrite && (
                  <Button
                    variant="ghost"
                    onClick={() => deleteDocument.mutate(doc.id)}
                    disabled={deleteDocument.isPending}
                  >
                    Delete
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
