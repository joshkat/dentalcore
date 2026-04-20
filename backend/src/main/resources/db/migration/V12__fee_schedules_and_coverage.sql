-- DentalCore PMS - V12: fee schedules and coverage rules (insurance estimates)

CREATE TABLE fee_schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fee_schedule_fees (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_schedule_id    UUID NOT NULL REFERENCES fee_schedules(id) ON DELETE CASCADE,
    procedure_code_id  UUID NOT NULL REFERENCES procedure_codes(id),
    fee                NUMERIC(10,2) NOT NULL CHECK (fee >= 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schedule_code UNIQUE (fee_schedule_id, procedure_code_id)
);

CREATE INDEX idx_fee_schedule_fees ON fee_schedule_fees (fee_schedule_id);

ALTER TABLE insurance_plans
    ADD COLUMN fee_schedule_id UUID REFERENCES fee_schedules(id);

-- Coverage percentage per procedure category, e.g. PREVENTIVE 100, RESTORATIVE 80
CREATE TABLE coverage_rules (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id           UUID NOT NULL REFERENCES insurance_plans(id) ON DELETE CASCADE,
    category          VARCHAR(50) NOT NULL
                      CHECK (category IN ('DIAGNOSTIC', 'PREVENTIVE', 'RESTORATIVE',
                                          'ENDODONTICS', 'PERIODONTICS', 'PROSTHODONTICS',
                                          'ORAL_SURGERY', 'ORTHODONTICS', 'ADJUNCTIVE', 'OTHER')),
    coverage_percent  INT NOT NULL CHECK (coverage_percent BETWEEN 0 AND 100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_coverage_rule UNIQUE (plan_id, category)
);

CREATE INDEX idx_coverage_rules_plan ON coverage_rules (plan_id);
