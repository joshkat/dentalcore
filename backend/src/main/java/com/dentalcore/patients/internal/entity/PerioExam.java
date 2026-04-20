package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "perio_exams")
public class PerioExam extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate = LocalDate.now();

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PerioMeasurement> measurements = new ArrayList<>();

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PerioToothFinding> toothFindings = new ArrayList<>();

    protected PerioExam() {
    }

    public PerioExam(UUID clinicId, UUID patientId, UUID providerId, LocalDate examDate,
                     String notes) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.providerId = providerId;
        if (examDate != null) {
            this.examDate = examDate;
        }
        this.notes = notes;
    }

    public PerioMeasurement upsertMeasurement(String tooth, int site, Integer pocketDepth,
                                              Integer recession, boolean bleeding,
                                              boolean suppuration) {
        PerioMeasurement existing = measurements.stream()
                .filter(m -> m.getTooth().equals(tooth) && m.getSite() == site)
                .findFirst().orElse(null);
        if (existing != null) {
            existing.update(pocketDepth, recession, bleeding, suppuration);
            return existing;
        }
        PerioMeasurement created = new PerioMeasurement(
                this, tooth, site, pocketDepth, recession, bleeding, suppuration);
        measurements.add(created);
        return created;
    }

    public PerioToothFinding upsertToothFinding(String tooth, Integer mobility,
                                                Integer furcation) {
        PerioToothFinding existing = toothFindings.stream()
                .filter(f -> f.getTooth().equals(tooth))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.update(mobility, furcation);
            return existing;
        }
        PerioToothFinding created = new PerioToothFinding(this, tooth, mobility, furcation);
        toothFindings.add(created);
        return created;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public String getNotes() {
        return notes;
    }

    public List<PerioMeasurement> getMeasurements() {
        return measurements;
    }

    public List<PerioToothFinding> getToothFindings() {
        return toothFindings;
    }
}
