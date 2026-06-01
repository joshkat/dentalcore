-- DentalCore PMS - V18: granular permissions
--
-- Replaces coarse role checks with a role -> permission matrix. The seeding
-- below reproduces the effective access of the pre-V18 hasAnyRole(...) gates
-- EXACTLY; *_READ codes that have no enforcing endpoint yet (reads that are
-- authenticated-only by design) are granted to every role so future
-- enforcement can be turned on without behavior change.

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    category    VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id        UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id  UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- Permission catalog
-- ============================================================
INSERT INTO permissions (code, description, category) VALUES
    -- PATIENTS
    ('PATIENTS_READ',            'View and search patient records',                                 'PATIENTS'),
    ('PATIENTS_WRITE',           'Create and update patients, alerts, family links, and recall',   'PATIENTS'),
    ('PATIENTS_DELETE',          'Soft-delete a patient record',                                   'PATIENTS'),
    ('PATIENT_GUARANTOR_MANAGE', 'Set or clear the account guarantor for family billing',          'PATIENTS'),
    -- CLINICAL
    ('CHART_READ',               'View the tooth chart',                                           'CLINICAL'),
    ('CHART_WRITE',              'Edit tooth chart conditions and treatments',                     'CLINICAL'),
    ('PERIO_WRITE',              'Record periodontal exams',                                       'CLINICAL'),
    ('NOTES_READ',               'Read clinical notes',                                            'CLINICAL'),
    ('NOTES_WRITE',              'Create, amend, and sign clinical notes',                         'CLINICAL'),
    ('NOTE_TEMPLATES_MANAGE',    'Manage clinical note templates',                                 'CLINICAL'),
    ('PLANS_READ',               'View treatment plans',                                           'CLINICAL'),
    ('PLANS_WRITE',              'Create and edit treatment plans',                                'CLINICAL'),
    ('PROCEDURES_COMPLETE',      'Mark planned procedures completed',                              'CLINICAL'),
    ('CATALOG_MANAGE',           'Manage the procedure code catalog',                              'CLINICAL'),
    -- SCHEDULE
    ('APPOINTMENTS_READ',        'View the schedule and appointment details',                      'SCHEDULE'),
    ('APPOINTMENTS_WRITE',       'Book, edit, and change the status of appointments',              'SCHEDULE'),
    ('PROVIDERS_MANAGE',         'Manage providers, operatories, and provider availability',       'SCHEDULE'),
    ('REMINDERS_RUN',            'Run the appointment reminder job',                               'SCHEDULE'),
    -- BILLING
    ('BILLING_READ',             'View ledgers, balances, and payment plans',                      'BILLING'),
    ('BILLING_POST',             'Post manual charges and adjustments',                            'BILLING'),
    ('BILLING_REVERSE',          'Void ledger entries with a negating reversal',                   'BILLING'),
    ('PAYMENTS_TAKE',            'Record patient payments',                                        'BILLING'),
    ('STATEMENTS_GENERATE',      'Generate statements, family statements, and walkouts',           'BILLING'),
    ('STATEMENT_RUNS_MANAGE',    'Run batch statement generation',                                 'BILLING'),
    ('PAYMENT_PLANS_MANAGE',     'Create and close out payment plans',                             'BILLING'),
    -- INSURANCE
    ('INSURANCE_READ',           'View carriers, plans, coverages, and estimates',                 'INSURANCE'),
    ('INSURANCE_MANAGE',         'Manage insurance carriers and plans',                            'INSURANCE'),
    ('COVERAGE_MANAGE',          'Manage patient insurance coverages',                             'INSURANCE'),
    ('CLAIMS_MANAGE',            'Create, submit, and resolve insurance claims',                   'INSURANCE'),
    ('FEES_MANAGE',              'Manage fee schedules and plan coverage rules',                   'INSURANCE'),
    -- DOCUMENTS
    ('DOCS_READ',                'View and download patient documents',                            'DOCUMENTS'),
    ('DOCS_WRITE',               'Upload and manage patient documents',                            'DOCUMENTS'),
    ('FORMS_TEMPLATES_MANAGE',   'Manage form templates',                                          'DOCUMENTS'),
    ('FORMS_FILL',               'Assign and fill out patient forms',                              'DOCUMENTS'),
    -- REPORTS
    ('REPORTS_VIEW',             'View operational reports',                                       'REPORTS'),
    ('REPORTS_FINANCIAL',        'View financial reports (production, A/R aging)',                 'REPORTS'),
    ('REPORTS_DAY_SHEET',        'View the day sheet and collections list',                        'REPORTS'),
    -- ADMIN
    ('USERS_MANAGE',             'Create and manage user accounts',                                'ADMIN'),
    ('PERMISSIONS_MANAGE',       'Edit the role permission matrix',                                'ADMIN'),
    ('AUDIT_VIEW',               'Search the audit trail',                                         'ADMIN'),
    ('PATIENTS_MERGE',           'Merge duplicate patient records',                                'ADMIN')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- Role grants (parity with the pre-V18 hasAnyRole gates)
-- ============================================================

-- ADMIN holds every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PATIENTS_READ', 'PATIENTS_WRITE',
    'CHART_READ', 'CHART_WRITE', 'PERIO_WRITE',
    'NOTES_READ', 'NOTES_WRITE', 'NOTE_TEMPLATES_MANAGE',
    'PLANS_READ', 'PLANS_WRITE', 'PROCEDURES_COMPLETE',
    'APPOINTMENTS_READ', 'APPOINTMENTS_WRITE',
    'BILLING_READ',
    'INSURANCE_READ',
    'DOCS_READ', 'DOCS_WRITE', 'FORMS_FILL',
    'REPORTS_VIEW'
)
WHERE r.name = 'DENTIST'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PATIENTS_READ', 'PATIENTS_WRITE',
    'CHART_READ', 'CHART_WRITE', 'PERIO_WRITE',
    'NOTES_READ', 'NOTES_WRITE', 'NOTE_TEMPLATES_MANAGE',
    'PLANS_READ', 'PLANS_WRITE', 'PROCEDURES_COMPLETE',
    'APPOINTMENTS_READ', 'APPOINTMENTS_WRITE',
    'INSURANCE_READ',
    'DOCS_READ', 'DOCS_WRITE', 'FORMS_FILL',
    'REPORTS_VIEW'
)
WHERE r.name = 'HYGIENIST'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PATIENTS_READ', 'PATIENTS_WRITE', 'PATIENT_GUARANTOR_MANAGE',
    'CHART_READ',
    'PLANS_READ', 'PROCEDURES_COMPLETE',
    'APPOINTMENTS_READ', 'APPOINTMENTS_WRITE', 'REMINDERS_RUN',
    'BILLING_READ', 'PAYMENTS_TAKE', 'STATEMENTS_GENERATE',
    'INSURANCE_READ', 'COVERAGE_MANAGE',
    'DOCS_READ', 'DOCS_WRITE', 'FORMS_FILL',
    'REPORTS_VIEW', 'REPORTS_DAY_SHEET'
)
WHERE r.name = 'FRONT_DESK'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PATIENTS_READ', 'PATIENT_GUARANTOR_MANAGE',
    'CHART_READ', 'PLANS_READ',
    'APPOINTMENTS_READ',
    'BILLING_READ', 'BILLING_POST', 'BILLING_REVERSE',
    'PAYMENTS_TAKE', 'STATEMENTS_GENERATE', 'STATEMENT_RUNS_MANAGE', 'PAYMENT_PLANS_MANAGE',
    'INSURANCE_READ', 'INSURANCE_MANAGE', 'COVERAGE_MANAGE', 'CLAIMS_MANAGE', 'FEES_MANAGE',
    'DOCS_READ',
    'REPORTS_VIEW', 'REPORTS_FINANCIAL', 'REPORTS_DAY_SHEET'
)
WHERE r.name = 'BILLING'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PATIENTS_READ', 'CHART_READ', 'NOTES_READ', 'PLANS_READ',
    'APPOINTMENTS_READ', 'INSURANCE_READ', 'DOCS_READ'
)
WHERE r.name = 'READ_ONLY'
ON CONFLICT DO NOTHING;
