package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.internal.dto.FamilyLinkRequest;
import com.dentalcore.patients.internal.dto.FamilyLinkResponse;
import com.dentalcore.patients.internal.entity.FamilyLink;
import com.dentalcore.patients.internal.entity.Patient;
import com.dentalcore.patients.internal.repository.FamilyLinkRepository;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class FamilyLinkService {

    private static final Map<FamilyLink.Relationship, FamilyLink.Relationship> INVERSE = Map.of(
            FamilyLink.Relationship.SPOUSE, FamilyLink.Relationship.SPOUSE,
            FamilyLink.Relationship.SIBLING, FamilyLink.Relationship.SIBLING,
            FamilyLink.Relationship.CHILD, FamilyLink.Relationship.PARENT,
            FamilyLink.Relationship.PARENT, FamilyLink.Relationship.CHILD,
            FamilyLink.Relationship.GUARANTOR, FamilyLink.Relationship.OTHER,
            FamilyLink.Relationship.OTHER, FamilyLink.Relationship.OTHER);

    private final FamilyLinkRepository linkRepository;
    private final PatientService patientService;
    private final ApplicationEventPublisher events;

    public FamilyLinkService(FamilyLinkRepository linkRepository,
                             PatientService patientService,
                             ApplicationEventPublisher events) {
        this.linkRepository = linkRepository;
        this.patientService = patientService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<FamilyLinkResponse> list(UUID patientId) {
        patientService.findPatient(patientId);
        return linkRepository.findByPatientId(patientId).stream()
                .map(this::toResponse)
                .toList();
    }

    public FamilyLinkResponse create(UUID patientId, FamilyLinkRequest request) {
        if (patientId.equals(request.relatedPatientId())) {
            throw new InvalidRequestException("A patient cannot be linked to themselves");
        }
        patientService.findPatient(patientId);
        Patient related = patientService.findPatient(request.relatedPatientId());
        if (linkRepository.existsByPatientIdAndRelatedPatientId(patientId, related.getId())) {
            throw new ConflictException("These patients are already linked");
        }

        FamilyLink.Relationship relationship = FamilyLink.Relationship.valueOf(request.relationship());
        FamilyLink link = linkRepository.save(new FamilyLink(patientId, related.getId(), relationship));
        // Maintain the reverse direction so both patients see the relationship.
        linkRepository.save(new FamilyLink(related.getId(), patientId, INVERSE.get(relationship)));

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), PatientService.ENTITY_TYPE, patientId,
                AuditEvent.AuditAction.UPDATE,
                null,
                Map.of("familyLinkAdded", related.fullName(), "relationship", request.relationship())));
        return toResponse(link);
    }

    public void delete(UUID patientId, UUID linkId) {
        FamilyLink link = linkRepository.findById(linkId)
                .filter(l -> l.getPatientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Family link", linkId));
        linkRepository.delete(link);
        linkRepository.findByPatientIdAndRelatedPatientId(link.getRelatedPatientId(), patientId)
                .ifPresent(linkRepository::delete);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), PatientService.ENTITY_TYPE, patientId,
                AuditEvent.AuditAction.UPDATE,
                Map.of("familyLinkRemoved", link.getRelatedPatientId().toString()),
                null));
    }

    private FamilyLinkResponse toResponse(FamilyLink link) {
        Patient related = patientService.findPatient(link.getRelatedPatientId());
        return new FamilyLinkResponse(
                link.getId(),
                related.getId(),
                related.getFirstName(),
                related.getLastName(),
                link.getRelationship().name());
    }
}
