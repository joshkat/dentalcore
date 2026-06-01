-- DentalCore PMS - V21: per-user language preferences
-- ui_language drives the app UI, export_language drives generated PDFs.
-- NULL means "inherit the instance default" (dentalcore.i18n.default-language).

ALTER TABLE users
    ADD COLUMN ui_language     VARCHAR(5) NULL CHECK (ui_language IN ('en', 'es')),
    ADD COLUMN export_language VARCHAR(5) NULL CHECK (export_language IN ('en', 'es'));
