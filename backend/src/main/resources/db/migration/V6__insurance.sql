-- DentalCore PMS - V6: insurance carriers, plans, patient coverage, claims

-- ============================================================
-- Carriers
-- ============================================================
CREATE TABLE insurance_carriers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(200) NOT NULL,
    payer_id       VARCHAR(20),
    phone          VARCHAR(30),
    address_line1  VARCHAR(255),
    address_line2  VARCHAR(255),
    city           VARCHAR(100),
    state          VARCHAR(50),
    postal_code    VARCHAR(20),
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_carriers_name ON insurance_carriers (lower(name)) WHERE deleted_at IS NULL;

-- ============================================================
-- Plans offered by carriers
-- ============================================================
CREATE TABLE insurance_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    carrier_id      UUID NOT NULL REFERENCES insurance_carriers(id),
    plan_name       VARCHAR(200) NOT NULL,
    group_number    VARCHAR(50),
    plan_type       VARCHAR(20) NOT NULL DEFAULT 'PPO'
                    CHECK (plan_type IN ('PPO', 'HMO', 'INDEMNITY', 'MEDICAID', 'DISCOUNT', 'OTHER')),
    annual_max      NUMERIC(10,2),
    deductible      NUMERIC(10,2),
    coverage_notes  VARCHAR(2000),
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_insurance_plans_carrier ON insurance_plans (carrier_id) WHERE deleted_at IS NULL;

-- ============================================================
-- Patient coverage (subscriber is itself a patient)
-- ============================================================
CREATE TABLE patient_insurance (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id                  UUID NOT NULL REFERENCES patients(id),
    plan_id                     UUID NOT NULL REFERENCES insurance_plans(id),
    subscriber_patient_id       UUID NOT NULL REFERENCES patients(id),
    relationship_to_subscriber  VARCHAR(20) NOT NULL DEFAULT 'SELF'
                                CHECK (relationship_to_subscriber IN ('SELF', 'SPOUSE', 'CHILD', 'OTHER')),
    member_id                   VARCHAR(50) NOT NULL,
    priority                    VARCHAR(10) NOT NULL DEFAULT 'PRIMARY'
                                CHECK (priority IN ('PRIMARY', 'SECONDARY')),
    effective_date              DATE,
    termination_date            DATE,
    deleted_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT coverage_dates_valid CHECK (
        termination_date IS NULL OR effective_date IS NULL OR termination_date >= effective_date)
);

CREATE INDEX idx_patient_insurance_patient ON patient_insurance (patient_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_patient_insurance_subscriber ON patient_insurance (subscriber_patient_id)
    WHERE deleted_at IS NULL;

-- ============================================================
-- Claims
-- ============================================================
CREATE TABLE claims (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_insurance_id  UUID NOT NULL REFERENCES patient_insurance(id),
    patient_id            UUID NOT NULL REFERENCES patients(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'DENIED', 'PAID', 'CLOSED')),
    submitted_at          TIMESTAMPTZ,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_claims_status ON claims (status, created_at DESC);
CREATE INDEX idx_claims_patient ON claims (patient_id, created_at DESC);

-- ============================================================
-- Claim line items
-- (links to procedure codes now; ledger entries arrive in Phase 6)
-- ============================================================
CREATE TABLE claim_procedures (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id           UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    procedure_code_id  UUID NOT NULL REFERENCES procedure_codes(id),
    billed_amount      NUMERIC(10,2) NOT NULL DEFAULT 0,
    paid_amount        NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_claim_procedures_claim ON claim_procedures (claim_id);
