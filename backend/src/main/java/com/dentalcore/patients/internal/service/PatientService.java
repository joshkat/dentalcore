package com.dentalcore.patients.internal.service;

import com.dentalcore.audit.api.AuditApi;
import com.dentalcore.patients.internal.dto.PatientRequest;
import com.dentalcore.patients.internal.dto.PatientResponse;
import com.dentalcore.patients.internal.dto.PatientSummaryResponse;
import com.dentalcore.patients.internal.dto.TimelineEventResponse;
import com.dentalcore.patients.internal.entity.Patient;
import com.dentalcore.patients.internal.entity.PatientStatus;
import com.dentalcore.patients.internal.entity.Sex;
import com.dentalcore.patients.internal.mapper.PatientMapper;
import com.dentalcore.patients.internal.repository.PatientRepository;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PatientService {

    static final String ENTITY_TYPE = "Patient";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PatientRepository patientRepository;
    private final PatientMapper mapper;
    private final ApplicationEventPublisher events;
    private final AuditApi auditApi;
    private final ProviderApi providerApi;

    public PatientService(PatientRepository patientRepository,
                          PatientMapper mapper,
                          ApplicationEventPublisher events,
                          AuditApi auditApi,
                          ProviderApi providerApi) {
        this.patientRepository = patientRepository;
        this.mapper = mapper;
        this.events = events;
        this.auditApi = auditApi;
        this.providerApi = providerApi;
    }

    @Transactional(readOnly = true)
    public Page<PatientSummaryResponse> search(String q, String status, Pageable pageable) {
        Page<Patient> page;
        if (q != null && !q.isBlank()) {
            page = patientRepository.search(q.trim(), pageable);
        } else if (status != null && !status.isBlank()) {
            page = patientRepository.findByStatus(PatientStatus.valueOf(status), pageable);
        } else {
            page = patientRepository.findAll(pageable);
        }
        return page.map(mapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public PatientResponse get(UUID id) {
        return mapper.toResponse(findPatient(id));
    }

    public PatientResponse create(PatientRequest request) {
        Patient patient = new Patient(
                DEFAULT_CLINIC_ID,
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                Sex.valueOf(request.sex()));
        applyDemographics(patient, request);
        patient = patientRepository.save(patient);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, patient.getId(),
                AuditEvent.AuditAction.CREATE,
                null,
                Map.of("name", patient.fullName(), "dateOfBirth", patient.getDateOfBirth().toString())));
        return mapper.toResponse(patient);
    }

    public PatientResponse update(UUID id, PatientRequest request) {
        Patient patient = findPatient(id);
        Map<String, Object> before = snapshot(patient);
        applyDemographics(patient, request);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, patient.getId(),
                AuditEvent.AuditAction.UPDATE,
                before,
                snapshot(patient)));
        return mapper.toResponse(patient);
    }

    public PatientResponse updateStatus(UUID id, String status) {
        Patient patient = findPatient(id);
        String previous = patient.getStatus().name();
        patient.setStatus(PatientStatus.valueOf(status));

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, patient.getId(),
                AuditEvent.AuditAction.STATUS_CHANGE,
                Map.of("status", previous),
                Map.of("status", status)));
        return mapper.toResponse(patient);
    }

    public void delete(UUID id) {
        Patient patient = findPatient(id);
        patientRepository.delete(patient); // soft delete via @SQLDelete

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.DELETE,
                Map.of("name", patient.fullName()),
                null));
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> timeline(UUID id) {
        findPatient(id); // 404 when unknown
        return auditApi.findByEntity(ENTITY_TYPE, id, 100).stream()
                .map(entry -> new TimelineEventResponse(
                        entry.id(), entry.action(), entry.userId(),
                        entry.previousValue(), entry.newValue(), entry.occurredAt()))
                .toList();
    }

    public Patient findPatient(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private void applyDemographics(Patient patient, PatientRequest request) {
        if (request.primaryProviderId() != null
                && !providerApi.existsAndActive(request.primaryProviderId())) {
            throw new InvalidRequestException("Unknown or inactive primary provider");
        }
        patient.updateDemographics(
                request.firstName(), request.middleName(), request.lastName(),
                request.dateOfBirth(), Sex.valueOf(request.sex()), request.email(),
                request.addressLine1(), request.addressLine2(), request.city(),
                request.state(), request.postalCode(), request.preferredLanguage(),
                request.emergencyContactName(), request.emergencyContactPhone(),
                request.emergencyContactRelationship(), request.notes());
        patient.updateProfileExtras(
                request.preferredName(), request.pronouns(), request.employer(),
                request.occupation(), request.referralSource(),
                request.preferredContactMethod() == null
                        ? null : Patient.ContactMethod.valueOf(request.preferredContactMethod()),
                request.smsConsentOrDefault(), request.emailConsentOrDefault(),
                request.pharmacyName(), request.pharmacyPhone(),
                request.primaryProviderId(),
                Patient.SmokingStatus.valueOf(request.smokingStatusOrDefault()));
        patient.replacePhones(mapper.toPhoneEntities(request.phonesOrEmpty()));
    }

    public PatientResponse updateRecall(UUID id, int intervalMonths,
                                        java.time.LocalDate nextRecallDate) {
        Patient patient = findPatient(id);
        patient.updateRecall(intervalMonths, nextRecallDate);
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.UPDATE,
                null,
                Map.of("recallIntervalMonths", intervalMonths,
                        "nextRecallDate", String.valueOf(nextRecallDate))));
        return mapper.toResponse(patient);
    }

    private Map<String, Object> snapshot(Patient patient) {
        var map = new java.util.HashMap<String, Object>();
        map.put("name", patient.fullName());
        map.put("dateOfBirth", patient.getDateOfBirth().toString());
        map.put("sex", patient.getSex().name());
        map.put("email", patient.getEmail());
        map.put("status", patient.getStatus().name());
        return map;
    }
}
