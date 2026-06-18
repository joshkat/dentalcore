-- DentalCore PMS - V22: scheduling power tools
-- Operatory blockouts, recurring-appointment series linkage, confirmation tracking.

-- ============================================================
-- Operatory blockouts (chair unavailable: maintenance, lunch, closed day).
-- Provider unavailability is already modeled by provider_time_off (V10);
-- blockouts cover the operatory/room dimension.
-- ============================================================
CREATE TABLE schedule_blockouts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    UUID NOT NULL REFERENCES clinics(id),
    operatory_id UUID NOT NULL REFERENCES operatories(id),
    starts_at    TIMESTAMPTZ NOT NULL,
    ends_at      TIMESTAMPTZ NOT NULL,
    reason       VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT blockout_time_valid CHECK (ends_at > starts_at)
);

CREATE INDEX idx_blockout_operatory_time ON schedule_blockouts (operatory_id, starts_at);
CREATE INDEX idx_blockout_clinic_time ON schedule_blockouts (clinic_id, starts_at);

-- ============================================================
-- Recurring series linkage + two-way confirmation tracking on appointments.
-- ============================================================
-- All appointments generated from one recurrence share a series_id (null for
-- one-off bookings); lets us cancel/list a whole series.
ALTER TABLE appointments ADD COLUMN series_id UUID;
-- When a confirmation request was last sent to the patient (null = none sent).
-- The patient "confirms" by moving the appointment to the CONFIRMED status.
ALTER TABLE appointments ADD COLUMN confirmation_sent_at TIMESTAMPTZ;

CREATE INDEX idx_appt_series ON appointments (series_id) WHERE series_id IS NOT NULL;
