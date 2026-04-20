package com.dentalcore.patients;

import com.dentalcore.patients.internal.dto.FamilyLinkRequest;
import com.dentalcore.patients.internal.entity.FamilyLink;
import com.dentalcore.patients.internal.repository.FamilyLinkRepository;
import com.dentalcore.patients.internal.service.FamilyLinkService;
import com.dentalcore.patients.internal.service.PatientService;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyLinkServiceTest {

    @Mock
    private FamilyLinkRepository linkRepository;
    @Mock
    private PatientService patientService;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private FamilyLinkService service;

    private final UUID patientId = UUID.randomUUID();
    private final UUID relatedId = UUID.randomUUID();

    @Test
    void selfLinkIsRejected() {
        assertThatThrownBy(() -> service.create(patientId,
                new FamilyLinkRequest(patientId, "SPOUSE")))
                .isInstanceOf(InvalidRequestException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void duplicateLinkIsRejected() {
        var patient = patientWithId(patientId);
        var related = patientWithId(relatedId);
        when(patientService.findPatient(patientId)).thenReturn(patient);
        when(patientService.findPatient(relatedId)).thenReturn(related);
        when(linkRepository.existsByPatientIdAndRelatedPatientId(patientId, relatedId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(patientId,
                new FamilyLinkRequest(relatedId, "SPOUSE")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void childLinkCreatesInverseParentLink() {
        var patient = patientWithId(patientId);
        var related = patientWithId(relatedId);
        when(patientService.findPatient(patientId)).thenReturn(patient);
        when(patientService.findPatient(relatedId)).thenReturn(related);
        when(linkRepository.existsByPatientIdAndRelatedPatientId(patientId, relatedId))
                .thenReturn(false);
        when(linkRepository.save(any(FamilyLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(patientId, new FamilyLinkRequest(relatedId, "CHILD"));

        ArgumentCaptor<FamilyLink> captor = ArgumentCaptor.forClass(FamilyLink.class);
        verify(linkRepository, times(2)).save(captor.capture());
        var links = captor.getAllValues();
        assertThat(links.get(0).getRelationship()).isEqualTo(FamilyLink.Relationship.CHILD);
        assertThat(links.get(1).getRelationship()).isEqualTo(FamilyLink.Relationship.PARENT);
        assertThat(links.get(1).getPatientId()).isEqualTo(relatedId);
        assertThat(links.get(1).getRelatedPatientId()).isEqualTo(patientId);
    }

    private com.dentalcore.patients.internal.entity.Patient patientWithId(UUID id) {
        var patient = org.mockito.Mockito.mock(
                com.dentalcore.patients.internal.entity.Patient.class);
        org.mockito.Mockito.lenient().when(patient.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(patient.fullName()).thenReturn("Test, Patient");
        return patient;
    }
}
