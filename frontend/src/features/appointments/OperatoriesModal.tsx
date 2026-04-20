import { useState } from 'react';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { useCreateOperatory, useOperatories, useUpdateOperatory } from './api';

export function OperatoriesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { data: operatories } = useOperatories(true);
  const createOperatory = useCreateOperatory();
  const updateOperatory = useUpdateOperatory();
  const [name, setName] = useState('');

  const add = async () => {
    if (!name.trim()) return;
    await createOperatory.mutateAsync({ name: name.trim() });
    setName('');
  };

  return (
    <Modal title="Operatories" open={open} onClose={onClose}>
      <div className="space-y-4">
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <label htmlFor="op-name" className="block text-sm font-medium text-gray-700">
              New operatory
            </label>
            <input
              id="op-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Operatory 4"
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-sm shadow-sm ring-1 ring-inset ring-gray-300"
            />
          </div>
          <Button onClick={add} loading={createOperatory.isPending}>
            Add
          </Button>
        </div>

        <ul className="divide-y divide-gray-100">
          {operatories?.map((operatory) => (
            <li key={operatory.id} className="flex items-center justify-between py-2">
              <span
                className={`text-sm ${
                  operatory.active ? 'text-gray-900' : 'text-gray-400 line-through'
                }`}
              >
                {operatory.name}
              </span>
              <Button
                variant="ghost"
                onClick={() =>
                  updateOperatory.mutate({
                    id: operatory.id,
                    name: operatory.name,
                    active: !operatory.active,
                  })
                }
              >
                {operatory.active ? 'Deactivate' : 'Activate'}
              </Button>
            </li>
          ))}
        </ul>
      </div>
    </Modal>
  );
}
