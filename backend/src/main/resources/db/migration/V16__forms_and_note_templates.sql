-- DentalCore PMS - V16: patient forms, e-signature instances and note templates
-- form_templates.fields is an ordered JSONB array of
--   {key, label, type: TEXT|TEXTAREA|CHECKBOX|DATE|SELECT, required, options?}
-- form_instances captures a patient's answers; once signed the answers are
-- frozen and the rendered PDF lives in the documents module (document_id).

CREATE TABLE form_templates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    UUID NOT NULL REFERENCES clinics(id),
    name         VARCHAR(120) NOT NULL,
    description  TEXT,
    fields       JSONB NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_form_templates_clinic_name UNIQUE (clinic_id, name)
);

CREATE TABLE form_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       UUID NOT NULL REFERENCES clinics(id),
    template_id     UUID NOT NULL REFERENCES form_templates(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    answers         JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'COMPLETED', 'SIGNED')),
    signed_at       TIMESTAMPTZ,
    signed_by_name  VARCHAR(160),
    document_id     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_form_instances_patient ON form_instances (patient_id, created_at);

CREATE TABLE note_templates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    UUID NOT NULL REFERENCES clinics(id),
    name         VARCHAR(120) NOT NULL,
    note_type    VARCHAR(20) NOT NULL DEFAULT 'PROGRESS'
                 CHECK (note_type IN ('EXAM', 'PROGRESS', 'PROCEDURE', 'PHONE', 'OTHER')),
    body         TEXT NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_note_templates_clinic_name UNIQUE (clinic_id, name)
);
