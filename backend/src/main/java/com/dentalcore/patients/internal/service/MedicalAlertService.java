package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.internal.dto.MedicalAlertRequest;
import com.dentalcore.patients.internal.dto.MedicalAlertResponse;
import com.dentalcore.patients.internal.entity.MedicalAlert;
import com.dentalcore.patients.internal.mapper.PatientMapper;
import com.dentalcore.patients.internal.repository.MedicalAlertRepository;
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
public class MedicalAlertService {

    private final MedicalAlertRepository alertRepository;
    private final PatientService patientService;
    private final PatientMapper mapper;
    private final ApplicationEventPublisher events;

    public MedicalAlertService(MedicalAlertRepository alertRepository,
                               PatientService patientService,
                               PatientMapper mapper,
                               ApplicationEventPublisher events) {
        this.alertRepository = alertRepository;
        this.patientService = patientService;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<MedicalAlertResponse> list(UUID patientId) {
        patientService.findPatient(patientId);
        return alertRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(mapper::toAlertResponse)
                .toList();
    }

    public MedicalAlertResponse create(UUID patientId, MedicalAlertRequest request) {
        patientService.findPatient(patientId);
        MedicalAlert alert = new MedicalAlert(
                patientId,
                MedicalAlert.AlertType.valueOf(request.type()),
                request.description(),
                MedicalAlert.Severity.valueOf(request.severity()));
        alert = alertRepository.save(alert);

        publish(patientId, AuditEvent.AuditAction.UPDATE, null,
                Map.of("alertAdded", request.description(), "severity", request.severity()));
        return mapper.toAlertResponse(alert);
    }

    public MedicalAlertResponse update(UUID patientId, UUID alertId, MedicalAlertRequest request) {
        MedicalAlert alert = findAlert(patientId, alertId);
        Map<String, Object> before = Map.of(
                "alert", alert.getDescription(), "severity", alert.getSeverity().name(),
                "active", alert.isActive());
        alert.update(
                MedicalAlert.AlertType.valueOf(request.type()),
                request.description(),
                MedicalAlert.Severity.valueOf(request.severity()),
                request.activeOrDefault());

        publish(patientId, AuditEvent.AuditAction.UPDATE, before,
                Map.of("alert", alert.getDescription(), "severity", alert.getSeverity().name(),
                        "active", alert.isActive()));
        return mapper.toAlertResponse(alert);
    }

    public void delete(UUID patientId, UUID alertId) {
        MedicalAlert alert = findAlert(patientId, alertId);
        alertRepository.delete(alert);
        publish(patientId, AuditEvent.AuditAction.UPDATE,
                Map.of("alertRemoved", alert.getDescription()), null);
    }

    private MedicalAlert findAlert(UUID patientId, UUID alertId) {
        return alertRepository.findById(alertId)
                .filter(alert -> alert.getPatientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Medical alert", alertId));
    }

    private void publish(UUID patientId, AuditEvent.AuditAction action,
                         Map<String, Object> before, Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), PatientService.ENTITY_TYPE, patientId,
                action, before, after));
    }
}
