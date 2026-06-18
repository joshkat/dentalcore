import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import type { FormTemplate } from '../../types/api';
import { useDeleteFormTemplate, useFormTemplates } from './api';
import { TemplateBuilderModal } from './TemplateBuilderModal';

export function FormsPage() {
  const { t } = useTranslation('forms');
  const { hasRole } = useAuth();
  const canView = hasRole('ADMIN', 'DENTIST', 'HYGIENIST', 'FRONT_DESK');
  const isAdmin = hasRole('ADMIN');

  const { data: templates, isPending, isError } = useFormTemplates();
  const deleteTemplate = useDeleteFormTemplate();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<FormTemplate | null>(null);

  if (!canView) {
    return (
      <div className="p-8 text-sm text-gray-600">{t('noPermission')}</div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">{t('title')}</h1>
        {isAdmin && <Button onClick={() => setCreating(true)}>{t('newTemplate')}</Button>}
      </div>
      <p className="mt-1 text-sm text-gray-500">{t('subtitle')}</p>

      <div className="mt-6 overflow-hidden rounded-lg bg-white shadow">
        {isPending ? (
          <Spinner label={t('loadingTemplates')} />
        ) : isError ? (
          <p className="p-8 text-sm text-red-600">{t('loadFailed')}</p>
        ) : templates && templates.length === 0 ? (
          <p className="p-8 text-sm text-gray-500">{t('noTemplates')}</p>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                {[t('col.name'), t('col.fields'), t('col.status'), ''].map((h, i) => (
                  <th
                    key={i}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase text-gray-500"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {templates?.map((template) => (
                <tr key={template.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <p className="text-sm font-medium text-gray-900">{template.name}</p>
                    {template.description && (
                      <p className="text-xs text-gray-500">{template.description}</p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {t('fieldCount', { count: template.fields.length })}
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={template.active ? 'green' : 'gray'}>
                      {template.active ? t('active') : t('inactive')}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {isAdmin && (
                      <div className="flex justify-end gap-2">
                        <Button variant="ghost" onClick={() => setEditing(template)}>
                          {t('edit')}
                        </Button>
                        {template.active && (
                          <Button
                            variant="ghost"
                            onClick={() => deleteTemplate.mutate(template.id)}
                            disabled={deleteTemplate.isPending}
                          >
                            {t('deactivate')}
                          </Button>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {creating && <TemplateBuilderModal template={null} onClose={() => setCreating(false)} />}
      {editing && <TemplateBuilderModal template={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}
