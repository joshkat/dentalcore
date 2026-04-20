package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Entity
@Table(name = "tooth_conditions")
public class ToothCondition extends BaseEntity {

    public enum Condition {
        MISSING, CARIES, RESTORATION, CROWN, ROOT_CANAL, IMPLANT, BRIDGE,
        VENEER, SEALANT, EXTRACTION_PLANNED, FRACTURE, WATCH, OTHER
    }

    public enum Status {
        ACTIVE, RESOLVED
    }

    /** Universal numbering: permanent 1-32 or primary A-T. */
    private static final Pattern PERMANENT = Pattern.compile("^([1-9]|[12][0-9]|3[0-2])$");
    private static final Pattern PRIMARY = Pattern.compile("^[A-T]$");
    private static final Set<Character> SURFACE_LETTERS = Set.of('M', 'O', 'D', 'B', 'L', 'I');

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "tooth", nullable = false, length = 2)
    private String tooth;

    @Column(name = "surfaces", length = 6)
    private String surfaces;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false, length = 20)
    private Condition condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ToothCondition() {
    }

    public ToothCondition(UUID patientId, String tooth, String surfaces, Condition condition,
                          String notes, UUID recordedBy) {
        this.patientId = patientId;
        this.tooth = normalizeTooth(tooth);
        this.surfaces = normalizeSurfaces(surfaces);
        this.condition = condition;
        this.notes = notes;
        this.recordedBy = recordedBy;
    }

    public static String normalizeTooth(String tooth) {
        String trimmed = tooth == null ? "" : tooth.trim().toUpperCase();
        if (!PERMANENT.matcher(trimmed).matches() && !PRIMARY.matcher(trimmed).matches()) {
            throw new InvalidRequestException(
                    "Tooth must be 1-32 (permanent) or A-T (primary), got '%s'".formatted(tooth));
        }
        return trimmed;
    }

    public static String normalizeSurfaces(String surfaces) {
        if (surfaces == null || surfaces.isBlank()) {
            return null;
        }
        String upper = surfaces.trim().toUpperCase();
        for (char c : upper.toCharArray()) {
            if (!SURFACE_LETTERS.contains(c)) {
                throw new InvalidRequestException(
                        "Surfaces may only contain M, O, D, B, L, I — got '%s'".formatted(surfaces));
            }
        }
        // dedupe, keep canonical order
        return "MODBLI".chars()
                .mapToObj(c -> (char) c)
                .filter(c -> upper.indexOf(c) >= 0)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    public void edit(String surfaces, Condition condition, String notes) {
        requireActive();
        this.surfaces = normalizeSurfaces(surfaces);
        this.condition = condition;
        this.notes = notes;
    }

    public void resolve() {
        requireActive();
        this.status = Status.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new InvalidRequestException("A resolved condition cannot be modified");
        }
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getTooth() {
        return tooth;
    }

    public String getSurfaces() {
        return surfaces;
    }

    public Condition getCondition() {
        return condition;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
