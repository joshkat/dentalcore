-- DentalCore PMS - V15: family billing & revenue cycle
-- A patient's account rolls up to a guarantor (one level only; NULL means the
-- patient guarantees their own account). Payment plans spread a balance over
-- scheduled installments.

ALTER TABLE patients ADD COLUMN guarantor_id UUID NULL REFERENCES patients(id);

CREATE INDEX idx_patients_guarantor ON patients (guarantor_id)
    WHERE guarantor_id IS NOT NULL;

CREATE TABLE payment_plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id           UUID NOT NULL REFERENCES clinics(id),
    patient_id          UUID NOT NULL REFERENCES patients(id),
    total_amount        NUMERIC(10,2) NOT NULL CHECK (total_amount > 0),
    down_payment        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (down_payment >= 0),
    installment_amount  NUMERIC(10,2) NOT NULL CHECK (installment_amount > 0),
    frequency           VARCHAR(10) NOT NULL CHECK (frequency IN ('MONTHLY', 'BIWEEKLY')),
    first_due_date      DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'COMPLETED', 'DEFAULTED', 'CANCELLED')),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_plans_patient ON payment_plans (patient_id, status);

CREATE TABLE payment_plan_installments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_plan_id  UUID NOT NULL REFERENCES payment_plans(id),
    due_date         DATE NOT NULL,
    amount           NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (payment_plan_id, due_date)
);
