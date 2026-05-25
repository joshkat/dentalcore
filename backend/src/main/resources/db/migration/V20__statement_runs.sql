-- DentalCore PMS - V20: batch statement runs
-- A run renders the family-statement PDF for every guarantor account whose
-- current family balance clears the minimum, files each PDF in the
-- guarantor's Documents, and records the per-account result.

-- Generated statements are filed under their own document category.
ALTER TABLE documents DROP CONSTRAINT documents_category_check;
ALTER TABLE documents ADD CONSTRAINT documents_category_check
    CHECK (category IN ('XRAY', 'PHOTO', 'CONSENT', 'INSURANCE', 'REFERRAL',
                        'STATEMENT', 'OTHER'));

CREATE TABLE statement_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    from_date       DATE NOT NULL,
    to_date         DATE NOT NULL,
    min_balance     NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (min_balance >= 0),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    total_accounts  INT NOT NULL DEFAULT 0,
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_statement_runs_created ON statement_runs (created_at DESC);

CREATE TABLE statement_run_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                UUID NOT NULL REFERENCES statement_runs(id) ON DELETE CASCADE,
    guarantor_patient_id  UUID NOT NULL REFERENCES patients(id),
    balance               NUMERIC(10,2) NOT NULL,
    document_id           UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, guarantor_patient_id)
);
