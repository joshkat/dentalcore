-- DentalCore PMS - V19: patient merge tombstone
-- When a duplicate patient record is merged away, the source row stays for
-- history (status ARCHIVED) and points at the surviving record.

ALTER TABLE patients
    ADD COLUMN merged_into_patient_id UUID NULL REFERENCES patients(id);

CREATE INDEX idx_patients_merged_into ON patients (merged_into_patient_id)
    WHERE merged_into_patient_id IS NOT NULL;
