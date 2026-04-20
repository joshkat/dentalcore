-- DentalCore PMS - V4: operatories and appointments
-- (appointment_procedures arrives with the procedure catalog in V5)

-- ============================================================
-- Operatories (chairs / rooms)
-- ============================================================
CREATE TABLE operatories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id   UUID NOT NULL REFERENCES clinics(id),
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_operatory_name UNIQUE (clinic_id, name)
);

-- ============================================================
-- Appointments
-- ============================================================
CREATE TABLE appointments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id           UUID NOT NULL REFERENCES clinics(id),
    patient_id          UUID NOT NULL REFERENCES patients(id),
    provider_id         UUID NOT NULL REFERENCES providers(id),
    operatory_id        UUID NOT NULL REFERENCES operatories(id),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'CHECKED_IN',
                                          'IN_PROGRESS', 'COMPLETED', 'NO_SHOW', 'CANCELLED')),
    notes               VARCHAR(2000),
    color_override      VARCHAR(7),
    cancelled_reason    VARCHAR(500),
    version             BIGINT NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT appointment_time_valid CHECK (ends_at > starts_at)
);

-- Calendar range queries
CREATE INDEX idx_appt_provider_time ON appointments (provider_id, starts_at);
CREATE INDEX idx_appt_operatory_time ON appointments (operatory_id, starts_at);
CREATE INDEX idx_appt_patient ON appointments (patient_id, starts_at DESC);
CREATE INDEX idx_appt_clinic_time ON appointments (clinic_id, starts_at);

-- Double-booking is impossible at the database level, even under concurrent
-- requests: GiST exclusion constraints reject overlapping time ranges for the
-- same provider or operatory (cancelled/no-show appointments don't block).
ALTER TABLE appointments ADD CONSTRAINT no_provider_overlap
    EXCLUDE USING gist (provider_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (status NOT IN ('CANCELLED', 'NO_SHOW') AND deleted_at IS NULL);

ALTER TABLE appointments ADD CONSTRAINT no_operatory_overlap
    EXCLUDE USING gist (operatory_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (status NOT IN ('CANCELLED', 'NO_SHOW') AND deleted_at IS NULL);

-- Seed a few operatories for the default clinic
INSERT INTO operatories (clinic_id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Operatory 1'),
    ('00000000-0000-0000-0000-000000000001', 'Operatory 2'),
    ('00000000-0000-0000-0000-000000000001', 'Hygiene 1');
