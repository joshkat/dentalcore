-- DentalCore PMS - V10: provider availability and patient reminders

-- ============================================================
-- Weekly working hours (multiple blocks per day allowed)
-- ============================================================
CREATE TABLE provider_hours (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id  UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    day_of_week  INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO: 1=Mon .. 7=Sun
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT hours_valid CHECK (end_time > start_time)
);

CREATE INDEX idx_provider_hours ON provider_hours (provider_id, day_of_week);

-- ============================================================
-- Time off / blocked time
-- ============================================================
CREATE TABLE provider_time_off (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id  UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    starts_at    TIMESTAMPTZ NOT NULL,
    ends_at      TIMESTAMPTZ NOT NULL,
    reason       VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT time_off_valid CHECK (ends_at > starts_at)
);

CREATE INDEX idx_provider_time_off ON provider_time_off (provider_id, starts_at);

-- ============================================================
-- Reminder log (one APPOINTMENT reminder per appointment;
-- RECALL resend throttling handled in the service via sent_at)
-- ============================================================
CREATE TABLE reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    appointment_id  UUID REFERENCES appointments(id),
    type            VARCHAR(15) NOT NULL CHECK (type IN ('APPOINTMENT', 'RECALL')),
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'NONE')),
    status          VARCHAR(10) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    detail          VARCHAR(300),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_reminders_appointment ON reminders (appointment_id)
    WHERE type = 'APPOINTMENT';
CREATE INDEX idx_reminders_patient ON reminders (patient_id, type, sent_at DESC);
