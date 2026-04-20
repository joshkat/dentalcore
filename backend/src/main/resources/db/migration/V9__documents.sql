-- DentalCore PMS - V9: document storage metadata
-- Binary content lives behind the StoragePort (local filesystem now,
-- S3-compatible later); the database stores metadata + the storage key.

CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id     UUID NOT NULL REFERENCES clinics(id),
    patient_id    UUID REFERENCES patients(id),
    category      VARCHAR(20) NOT NULL DEFAULT 'OTHER'
                  CHECK (category IN ('XRAY', 'PHOTO', 'CONSENT', 'INSURANCE', 'REFERRAL', 'OTHER')),
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0),
    storage_key   VARCHAR(255) NOT NULL UNIQUE,
    uploaded_by   UUID REFERENCES users(id),
    notes         VARCHAR(500),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_patient ON documents (patient_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_category ON documents (category) WHERE deleted_at IS NULL;
