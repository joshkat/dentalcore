-- DentalCore PMS - V5: procedure catalog, treatment plans, clinical notes,
-- appointment procedures

-- ============================================================
-- Procedure catalog (CDT-ready)
-- ============================================================
CREATE TABLE procedure_codes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(20) NOT NULL UNIQUE,
    description   VARCHAR(500) NOT NULL,
    category      VARCHAR(50) NOT NULL
                  CHECK (category IN ('DIAGNOSTIC', 'PREVENTIVE', 'RESTORATIVE', 'ENDODONTICS',
                                      'PERIODONTICS', 'PROSTHODONTICS', 'ORAL_SURGERY',
                                      'ORTHODONTICS', 'ADJUNCTIVE', 'OTHER')),
    standard_fee  NUMERIC(10,2) NOT NULL DEFAULT 0,
    cdt_code      VARCHAR(10),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_codes_category ON procedure_codes (category) WHERE active;
CREATE INDEX idx_procedure_codes_desc_trgm ON procedure_codes
    USING gin (description gin_trgm_ops) WHERE active;

-- ============================================================
-- Treatment plans
-- ============================================================
CREATE TABLE treatment_plans (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    UUID NOT NULL REFERENCES clinics(id),
    patient_id   UUID NOT NULL REFERENCES patients(id),
    provider_id  UUID NOT NULL REFERENCES providers(id),
    title        VARCHAR(200) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT', 'PRESENTED', 'APPROVED', 'IN_PROGRESS',
                                   'COMPLETED', 'CANCELLED')),
    approved_at  TIMESTAMPTZ,
    approved_by  UUID,
    notes        VARCHAR(2000),
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_treatment_plans_patient ON treatment_plans (patient_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- Planned procedures
-- ============================================================
CREATE TABLE planned_procedures (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treatment_plan_id  UUID NOT NULL REFERENCES treatment_plans(id) ON DELETE CASCADE,
    procedure_code_id  UUID NOT NULL REFERENCES procedure_codes(id),
    tooth              VARCHAR(5),
    surface            VARCHAR(10),
    priority           INT NOT NULL DEFAULT 1 CHECK (priority BETWEEN 1 AND 10),
    status             VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
                       CHECK (status IN ('PLANNED', 'SCHEDULED', 'COMPLETED', 'CANCELLED')),
    estimated_cost     NUMERIC(10,2) NOT NULL DEFAULT 0,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_planned_procedures_plan ON planned_procedures (treatment_plan_id, priority);

-- ============================================================
-- Clinical notes (immutable once signed)
-- ============================================================
CREATE TABLE clinical_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    provider_id     UUID REFERENCES providers(id),
    appointment_id  UUID REFERENCES appointments(id),
    author_user_id  UUID NOT NULL REFERENCES users(id),
    note_type       VARCHAR(20) NOT NULL DEFAULT 'PROGRESS'
                    CHECK (note_type IN ('EXAM', 'PROGRESS', 'PROCEDURE', 'PHONE', 'OTHER')),
    body            TEXT NOT NULL,
    signed_at       TIMESTAMPTZ,
    signed_by       UUID REFERENCES users(id),
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinical_notes_patient ON clinical_notes (patient_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- Appointment procedures (planned work for a visit)
-- ============================================================
CREATE TABLE appointment_procedures (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id     UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    procedure_code_id  UUID NOT NULL REFERENCES procedure_codes(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_appointment_procedure UNIQUE (appointment_id, procedure_code_id)
);

-- ============================================================
-- Seed: common CDT procedure codes
-- ============================================================
INSERT INTO procedure_codes (code, description, category, standard_fee, cdt_code) VALUES
    ('D0120', 'Periodic oral evaluation - established patient', 'DIAGNOSTIC',   65.00,  'D0120'),
    ('D0150', 'Comprehensive oral evaluation - new patient',    'DIAGNOSTIC',   110.00, 'D0150'),
    ('D0210', 'Intraoral - complete series of radiographic images', 'DIAGNOSTIC', 150.00, 'D0210'),
    ('D0274', 'Bitewings - four radiographic images',           'DIAGNOSTIC',   75.00,  'D0274'),
    ('D1110', 'Prophylaxis - adult',                            'PREVENTIVE',   120.00, 'D1110'),
    ('D1120', 'Prophylaxis - child',                            'PREVENTIVE',   90.00,  'D1120'),
    ('D1206', 'Topical application of fluoride varnish',        'PREVENTIVE',   45.00,  'D1206'),
    ('D1351', 'Sealant - per tooth',                            'PREVENTIVE',   60.00,  'D1351'),
    ('D2330', 'Resin-based composite - one surface, anterior',  'RESTORATIVE',  185.00, 'D2330'),
    ('D2391', 'Resin-based composite - one surface, posterior', 'RESTORATIVE',  205.00, 'D2391'),
    ('D2392', 'Resin-based composite - two surfaces, posterior','RESTORATIVE',  260.00, 'D2392'),
    ('D2740', 'Crown - porcelain/ceramic',                      'RESTORATIVE',  1250.00,'D2740'),
    ('D2950', 'Core buildup, including any pins when required', 'RESTORATIVE',  320.00, 'D2950'),
    ('D3310', 'Endodontic therapy, anterior tooth',             'ENDODONTICS',  850.00, 'D3310'),
    ('D3330', 'Endodontic therapy, molar tooth',                'ENDODONTICS',  1250.00,'D3330'),
    ('D4341', 'Periodontal scaling and root planing - per quadrant', 'PERIODONTICS', 285.00, 'D4341'),
    ('D5110', 'Complete denture - maxillary',                   'PROSTHODONTICS', 1900.00, 'D5110'),
    ('D6010', 'Surgical placement of implant body',             'ORAL_SURGERY', 2100.00,'D6010'),
    ('D7140', 'Extraction, erupted tooth or exposed root',      'ORAL_SURGERY', 210.00, 'D7140'),
    ('D7210', 'Extraction, erupted tooth requiring removal of bone', 'ORAL_SURGERY', 340.00, 'D7210'),
    ('D8080', 'Comprehensive orthodontic treatment of the adolescent dentition', 'ORTHODONTICS', 5500.00, 'D8080'),
    ('D9110', 'Palliative treatment of dental pain',            'ADJUNCTIVE',   125.00, 'D9110');
