-- DentalCore PMS - V11: periodontal charting
-- Sites per tooth (universal numbering, permanent dentition):
-- 1=DB 2=B 3=MB (facial side), 4=DL 5=L 6=ML (lingual side)

CREATE TABLE perio_exams (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    UUID NOT NULL REFERENCES clinics(id),
    patient_id   UUID NOT NULL REFERENCES patients(id),
    provider_id  UUID REFERENCES providers(id),
    exam_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    notes        VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_perio_exams_patient ON perio_exams (patient_id, exam_date DESC);

CREATE TABLE perio_measurements (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id       UUID NOT NULL REFERENCES perio_exams(id) ON DELETE CASCADE,
    tooth         VARCHAR(2) NOT NULL,
    site          INT NOT NULL CHECK (site BETWEEN 1 AND 6),
    pocket_depth  INT CHECK (pocket_depth BETWEEN 0 AND 20),
    recession     INT CHECK (recession BETWEEN -10 AND 20),
    bleeding      BOOLEAN NOT NULL DEFAULT FALSE,
    suppuration   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_perio_site UNIQUE (exam_id, tooth, site)
);

CREATE INDEX idx_perio_measurements_exam ON perio_measurements (exam_id);

CREATE TABLE perio_tooth_findings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id    UUID NOT NULL REFERENCES perio_exams(id) ON DELETE CASCADE,
    tooth      VARCHAR(2) NOT NULL,
    mobility   INT CHECK (mobility BETWEEN 0 AND 3),
    furcation  INT CHECK (furcation BETWEEN 0 AND 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_perio_tooth UNIQUE (exam_id, tooth)
);
