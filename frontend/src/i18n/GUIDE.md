# Translation Guide (wave-2 agents)

How to internationalize a feature directory. Wave 1 set up the infrastructure;
follow these conventions exactly so all features stay consistent.

## Namespaces & file locations

- **One namespace per feature dir**, named after the dir: `src/features/billing/` → namespace `billing`.
- Catalogs are JSON, one file per language:
  - `src/i18n/locales/en/<namespace>.json`
  - `src/i18n/locales/es/<namespace>.json`
- **Registry: there is none to edit.** `src/i18n/catalog.ts` discovers
  `locales/*/*.json` via `import.meta.glob` at build time. Adding your two JSON
  files is all that's required — zero shared-file edits, zero merge conflicts.
- Existing wave-1 namespaces: `common`, `nav`, `auth`, `dashboard`, `settings`.
  Reuse `common` for generic verbs/labels (Save, Cancel, Close, Loading…,
  Status, Date, Actions, error fallbacks) instead of duplicating them.

## Usage

```tsx
import { useTranslation } from 'react-i18next';

function LedgerTab() {
  const { t } = useTranslation('billing');     // your feature namespace
  return (
    <>
      <h2>{t('ledgerTitle')}</h2>              // billing:ledgerTitle
      <Button>{t('common:save')}</Button>      // cross-namespace via prefix
    </>
  );
}
```

- Key style: **camelCase**, flat or shallow-nested (`validation.emailRequired`).
- Outside components (module scope, non-React code): `import i18n from '../i18n'`
  then `i18n.t('billing:key')` — but prefer hooks; only hook subscribers
  re-render on language change.

## Rules

1. **Never concatenate translated fragments.** Word order differs by language.
   - Wrong: `t('deleted') + ' ' + name`
   - Right: `t('deletedName', { name })` with `"deletedName": "Deleted {{name}}"` / `"Se eliminó {{name}}"`
2. **Plurals** use i18next suffixes — one key, two entries per language:
   ```json
   "procedureCount_one": "{{count}} procedure",
   "procedureCount_other": "{{count}} procedures"
   ```
   Call: `t('procedureCount', { count: rows.length })`.
3. **Do NOT translate values stored in or sent to the API**: CDT codes
   (D1110…), enum statuses (`SCHEDULED`, `PAID`, `ACTIVE`…), tooth numbers,
   role names in payloads, query params. Translate only their **display**
   via a per-namespace map, e.g.:
   ```json
   "status": { "SCHEDULED": "Programada", "COMPLETED": "Completada" }
   ```
   ```ts
   t(`status.${appointment.status}`)   // value stays English in the payload
   ```
4. **Dates & money** go through `src/i18n/format.ts` — `formatDate`,
   `formatDateTime`, `formatMoney`. They use the active locale (`en-US` /
   `es-DO`; the product is deployed in the Dominican Republic, and money is
   Dominican pesos — es-DO renders `RD$1,234.50`). Do not call
   `toLocaleDateString`/`toFixed` for user-facing values.
5. **Zod schemas** bake messages at creation: convert to factories taking `t`
   and build with `useMemo(() => makeXSchema(t), [t])` — see
   `src/features/auth/schemas.ts` for the pattern.
   (`features/users/UserFormModal.tsx` still imports the deprecated static
   `passwordRules`; migrate it to `makePasswordRules(t)` when translating users.)
6. **aria-labels, titles/tooltips, placeholders, empty states** are user-visible:
   translate them all. The `MAIN` pane badge stays literal.
7. Keep en and es files key-identical (same shape); `en` is the fallback
   language, so a missing es key silently shows English — don't rely on that.
8. PDF/export endpoints accept `?lang=en|es` — pass the user's
   `effectiveExportLanguage` (from `usePreferences()` in
   `src/features/settings/api.ts`), NOT the UI language.

## Glossary (pinned terminology — use exactly these)

| English | Spanish |
|---|---|
| patient | paciente |
| appointment | cita |
| schedule | agenda |
| provider | proveedor |
| claim | reclamación |
| insurance | seguro |
| coverage | cobertura |
| ledger (display) | estado de cuenta |
| charge | cargo |
| payment | pago |
| balance | saldo |
| treatment plan | plan de tratamiento |
| procedure | procedimiento |
| tooth | diente |
| chart | odontograma |
| recall | control periódico |
| checkout | cierre de visita |
| walk-out | resumen de visita |
| day sheet | hoja del día |
| worklist | lista de trabajo |
| form | formulario |
| signature | firma |
| settings | configuración |
| users | usuarios |
| reports | informes |
| statement | estado de cuenta |
| guarantor | garante |
| payment plan | plan de pago |
| due | vence |
| overdue | vencido |

## Language resolution (already wired — don't re-implement)

1. First paint: cached effective language from `localStorage('dentalcore.lang')`.
2. No cache: instance default from public `GET /api/v1/config` (`main.tsx`).
3. After login: user's `effectiveUiLanguage` from preferences
   (`LanguageSync` in `src/features/settings/`), persisted back to the cache.
4. Logout keeps the language (it isn't sensitive).
