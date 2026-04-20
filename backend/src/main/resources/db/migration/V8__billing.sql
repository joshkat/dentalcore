-- DentalCore PMS - V8: append-only patient ledger
-- Corrections are reversing entries (reversal_of); rows are never updated or
-- deleted. Balance = SUM(amount): charges positive, payments negative.

CREATE TABLE ledger_entries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id          UUID NOT NULL REFERENCES clinics(id),
    patient_id         UUID NOT NULL REFERENCES patients(id),
    type               VARCHAR(20) NOT NULL
                       CHECK (type IN ('CHARGE', 'PAYMENT', 'ADJUSTMENT', 'INSURANCE_PAYMENT')),
    amount             NUMERIC(10,2) NOT NULL CHECK (amount <> 0),
    description        VARCHAR(500) NOT NULL,
    method             VARCHAR(10)
                       CHECK (method IN ('CASH', 'CARD', 'CHECK', 'OTHER')),
    procedure_code_id  UUID REFERENCES procedure_codes(id),
    appointment_id     UUID REFERENCES appointments(id),
    claim_id           UUID REFERENCES claims(id),
    entry_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    reversal_of        UUID UNIQUE REFERENCES ledger_entries(id),
    created_by         UUID REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- sign conventions enforced at the database level
    CONSTRAINT ledger_sign_convention CHECK (
        (type = 'CHARGE' AND amount > 0 AND reversal_of IS NULL)
        OR (type IN ('PAYMENT', 'INSURANCE_PAYMENT') AND amount < 0 AND reversal_of IS NULL)
        OR (type = 'ADJUSTMENT'))
);

CREATE INDEX idx_ledger_patient ON ledger_entries (patient_id, entry_date DESC, created_at DESC);
CREATE INDEX idx_ledger_appointment ON ledger_entries (appointment_id)
    WHERE appointment_id IS NOT NULL;
CREATE INDEX idx_ledger_clinic_date ON ledger_entries (clinic_id, entry_date);
