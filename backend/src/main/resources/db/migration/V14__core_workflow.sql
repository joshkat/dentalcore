-- DentalCore PMS - V14: core clinical & front-desk workflow
-- Completed procedures are the clinical source of truth for performed work.
-- Each row may link to the ledger charge it produced (ledger_entry_id) and to
-- the planned procedure it fulfils. Appointments gain an ASAP flag for the
-- short-notice fill-in list.

CREATE TABLE completed_procedures (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id             UUID NOT NULL REFERENCES clinics(id),
    patient_id            UUID NOT NULL REFERENCES patients(id),
    provider_id           UUID NOT NULL REFERENCES providers(id),
    procedure_code_id     UUID NOT NULL REFERENCES procedure_codes(id),
    appointment_id        UUID REFERENCES appointments(id),
    planned_procedure_id  UUID REFERENCES planned_procedures(id),
    tooth                 VARCHAR(4),
    surfaces              VARCHAR(16),
    fee                   NUMERIC(10,2) NOT NULL CHECK (fee >= 0),
    ledger_entry_id       UUID REFERENCES ledger_entries(id),
    notes                 TEXT,
    completed_at          TIMESTAMPTZ NOT NULL,
    entry_date            DATE NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_completed_procedures_patient_date
    ON completed_procedures (patient_id, entry_date);
CREATE INDEX idx_completed_procedures_date
    ON completed_procedures (entry_date);
CREATE INDEX idx_completed_procedures_appointment
    ON completed_procedures (appointment_id) WHERE appointment_id IS NOT NULL;

-- Short-notice list: patients who want an earlier slot if one frees up
ALTER TABLE appointments ADD COLUMN asap BOOLEAN NOT NULL DEFAULT FALSE;
