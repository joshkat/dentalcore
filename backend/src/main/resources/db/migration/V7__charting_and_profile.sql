-- DentalCore PMS - V7: tooth charting (odontogram) and patient profile depth

-- ============================================================
-- Tooth conditions (odontogram data)
-- Universal numbering: permanent 1-32, primary A-T (schema-ready)
-- ============================================================
CREATE TABLE tooth_conditions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   UUID NOT NULL REFERENCES patients(id),
    tooth        VARCHAR(2) NOT NULL,
    surfaces     VARCHAR(6),
    condition    VARCHAR(20) NOT NULL
                 CHECK (condition IN ('MISSING', 'CARIES', 'RESTORATION', 'CROWN', 'ROOT_CANAL',
                                      'IMPLANT', 'BRIDGE', 'VENEER', 'SEALANT',
                                      'EXTRACTION_PLANNED', 'FRACTURE', 'WATCH', 'OTHER')),
    status       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RESOLVED')),
    notes        VARCHAR(500),
    recorded_by  UUID REFERENCES users(id),
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tooth_conditions_patient ON tooth_conditions (patient_id, tooth)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_tooth_conditions_history ON tooth_conditions (patient_id, created_at DESC);

-- ============================================================
-- Patient profile depth
-- ============================================================
ALTER TABLE patients
    ADD COLUMN preferred_name           VARCHAR(100),
    ADD COLUMN pronouns                 VARCHAR(30),
    ADD COLUMN employer                 VARCHAR(200),
    ADD COLUMN occupation               VARCHAR(100),
    ADD COLUMN referral_source          VARCHAR(200),
    ADD COLUMN preferred_contact_method VARCHAR(10)
        CHECK (preferred_contact_method IN ('EMAIL', 'SMS', 'PHONE', 'MAIL')),
    ADD COLUMN sms_consent              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_consent            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pharmacy_name            VARCHAR(200),
    ADD COLUMN pharmacy_phone           VARCHAR(30),
    ADD COLUMN primary_provider_id      UUID REFERENCES providers(id),
    ADD COLUMN smoking_status           VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (smoking_status IN ('NEVER', 'FORMER', 'CURRENT', 'UNKNOWN')),
    ADD COLUMN recall_interval_months   INT NOT NULL DEFAULT 6
        CHECK (recall_interval_months BETWEEN 1 AND 36),
    ADD COLUMN next_recall_date         DATE;

CREATE INDEX idx_patients_recall ON patients (next_recall_date)
    WHERE deleted_at IS NULL AND next_recall_date IS NOT NULL;

-- ============================================================
-- Medications join the medical snapshot
-- ============================================================
ALTER TABLE medical_alerts DROP CONSTRAINT medical_alerts_type_check;
ALTER TABLE medical_alerts ADD CONSTRAINT medical_alerts_type_check
    CHECK (type IN ('ALLERGY', 'CONDITION', 'ALERT', 'MEDICATION'));
