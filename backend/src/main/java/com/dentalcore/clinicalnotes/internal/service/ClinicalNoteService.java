package com.dentalcore.clinicalnotes.internal.service;

import com.dentalcore.clinicalnotes.internal.dto.ClinicalNoteDtos.NoteRequest;
import com.dentalcore.clinicalnotes.internal.dto.ClinicalNoteDtos.NoteResponse;
import com.dentalcore.clinicalnotes.internal.entity.ClinicalNote;
import com.dentalcore.clinicalnotes.internal.repository.ClinicalNoteRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.shared.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ClinicalNoteService {

    private static final String ENTITY_TYPE = "ClinicalNote";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ClinicalNoteRepository noteRepository;
    private final PatientApi patientApi;
    private final ApplicationEventPublisher events;

    public ClinicalNoteService(ClinicalNoteRepository noteRepository,
                               PatientApi patientApi,
                               ApplicationEventPublisher events) {
        this.noteRepository = noteRepository;
        this.patientApi = patientApi;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteResponse> listForPatient(UUID patientId, Pageable pageable) {
        return PageResponse.from(
                noteRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable),
                this::toResponse);
    }

    public NoteResponse create(UUID patientId, NoteRequest request) {
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }
        UUID author = CurrentUser.id()
                .orElseThrow(() -> new InvalidRequestException("No authenticated user"));
        ClinicalNote note = new ClinicalNote(
                DEFAULT_CLINIC_ID, patientId, request.providerId(), request.appointmentId(),
                author, ClinicalNote.NoteType.valueOf(request.noteType()), request.body());
        note = noteRepository.save(note);

        publishAudit(note, AuditEvent.AuditAction.CREATE, null,
                Map.of("noteType", note.getNoteType().name()));
        return toResponse(note);
    }

    public NoteResponse update(UUID noteId, NoteRequest request) {
        ClinicalNote note = findNote(noteId);
        note.edit(ClinicalNote.NoteType.valueOf(request.noteType()), request.body());
        publishAudit(note, AuditEvent.AuditAction.UPDATE, null,
                Map.of("noteType", note.getNoteType().name()));
        return toResponse(note);
    }

    public NoteResponse sign(UUID noteId) {
        ClinicalNote note = findNote(noteId);
        UUID signer = CurrentUser.id()
                .orElseThrow(() -> new InvalidRequestException("No authenticated user"));
        note.sign(signer);
        publishAudit(note, AuditEvent.AuditAction.UPDATE, null,
                Map.of("signed", true, "signedBy", signer.toString()));
        return toResponse(note);
    }

    public void delete(UUID noteId) {
        ClinicalNote note = findNote(noteId);
        note.requireUnsigned(); // signed notes are permanent clinical record
        noteRepository.delete(note); // soft delete
        publishAudit(note, AuditEvent.AuditAction.DELETE, null, null);
    }

    private ClinicalNote findNote(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note", id));
    }

    private void publishAudit(ClinicalNote note, AuditEvent.AuditAction action,
                              Map<String, Object> before, Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, note.getId(), action, before, after));
    }

    private NoteResponse toResponse(ClinicalNote note) {
        return new NoteResponse(
                note.getId(), note.getPatientId(), note.getProviderId(),
                note.getAppointmentId(), note.getAuthorUserId(), note.getNoteType().name(),
                note.getBody(), note.getSignedAt(), note.getSignedBy(),
                note.getCreatedAt(), note.getUpdatedAt());
    }
}
