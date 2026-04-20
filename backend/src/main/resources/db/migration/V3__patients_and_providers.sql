-- DentalCore PMS - V3: patients, phones, medical alerts, family links, providers

-- ============================================================
-- Patients
-- ============================================================
CREATE TABLE patients (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id                       UUID NOT NULL REFERENCES clinics(id),
    first_name                      VARCHAR(100) NOT NULL,
    middle_name                     VARCHAR(100),
    last_name                       VARCHAR(100) NOT NULL,
    date_of_birth                   DATE NOT NULL,
    sex                             VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
                                    CHECK (sex IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    email                           VARCHAR(320),
    address_line1                   VARCHAR(255),
    address_line2                   VARCHAR(255),
    city                            VARCHAR(100),
    state                           VARCHAR(50),
    postal_code                     VARCHAR(20),
    preferred_language              VARCHAR(50),
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    emergency_contact_name          VARCHAR(200),
    emergency_contact_phone         VARCHAR(30),
    emergency_contact_relationship  VARCHAR(50),
    notes                           TEXT,
    deleted_at                      TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_name_trgm ON patients
    USING gin ((last_name || ' ' || first_name) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_patients_dob ON patients (date_of_birth) WHERE deleted_at IS NULL;
CREATE INDEX idx_patients_clinic ON patients (clinic_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_patients_email ON patients (lower(email)) WHERE deleted_at IS NULL;

-- ============================================================
-- Patient phones
-- ============================================================
CREATE TABLE patient_phones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id  UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    type        VARCHAR(10) NOT NULL CHECK (type IN ('HOME', 'MOBILE', 'WORK')),
    number      VARCHAR(30) NOT NULL,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_phones_patient ON patient_phones (patient_id);
CREATE INDEX idx_patient_phones_number ON patient_phones (number);

-- ============================================================
-- Medical alerts
-- ============================================================
CREATE TABLE medical_alerts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id  UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('ALLERGY', 'CONDITION', 'ALERT')),
    description VARCHAR(500) NOT NULL,
    severity    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
                CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_medical_alerts_patient ON medical_alerts (patient_id) WHERE active;

-- ============================================================
-- Family links (directional; both directions stored explicitly)
-- ============================================================
CREATE TABLE family_links (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    related_patient_id  UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    relationship        VARCHAR(20) NOT NULL
                        CHECK (relationship IN ('GUARANTOR', 'SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_family_link UNIQUE (patient_id, related_patient_id),
    CONSTRAINT no_self_link CHECK (patient_id <> related_patient_id)
);

CREATE INDEX idx_family_links_related ON family_links (related_patient_id);

-- ============================================================
-- Providers
-- ============================================================
CREATE TABLE providers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    user_id         UUID REFERENCES users(id),
    type            VARCHAR(20) NOT NULL CHECK (type IN ('DENTIST', 'HYGIENIST', 'ASSISTANT')),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    npi             VARCHAR(10),
    specialty       VARCHAR(100),
    license_number  VARCHAR(50),
    license_state   VARCHAR(50),
    email           VARCHAR(320),
    phone           VARCHAR(30),
    color           VARCHAR(7) NOT NULL DEFAULT '#3b82f6',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_providers_npi ON providers (npi) WHERE npi IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_providers_clinic ON providers (clinic_id) WHERE deleted_at IS NULL;
