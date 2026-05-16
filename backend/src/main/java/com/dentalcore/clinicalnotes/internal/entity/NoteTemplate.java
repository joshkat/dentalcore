package com.dentalcore.clinicalnotes.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "note_templates")
public class NoteTemplate extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private ClinicalNote.NoteType noteType = ClinicalNote.NoteType.PROGRESS;

    /** Note body with {{placeholder}} prompts, e.g. {{tooth}} or {{material}}. */
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected NoteTemplate() {
    }

    public NoteTemplate(UUID clinicId, String name, ClinicalNote.NoteType noteType, String body) {
        this.clinicId = clinicId;
        this.name = name;
        this.noteType = noteType;
        this.body = body;
    }

    public void edit(String name, ClinicalNote.NoteType noteType, String body) {
        this.name = name;
        this.noteType = noteType;
        this.body = body;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getName() {
        return name;
    }

    public ClinicalNote.NoteType getNoteType() {
        return noteType;
    }

    public String getBody() {
        return body;
    }

    public boolean isActive() {
        return active;
    }
}
