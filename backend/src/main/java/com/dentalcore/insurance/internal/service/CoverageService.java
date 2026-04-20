package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.CoverageRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CoverageResponse;
import com.dentalcore.insurance.internal.entity.InsuranceCarrier;
import com.dentalcore.insurance.internal.entity.InsurancePlan;
import com.dentalcore.insurance.internal.entity.PatientInsurance;
import com.dentalcore.insurance.internal.repository.PatientInsuranceRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
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
public class CoverageService {

    private final PatientInsuranceRepository coverageRepository;
    private final InsuranceAdminService adminService;
    private final PatientApi patientApi;
    private final ApplicationEventPublisher events;

    public CoverageService(PatientInsuranceRepository coverageRepository,
                           InsuranceAdminService adminService,
                           PatientApi patientApi,
                           ApplicationEventPublisher events) {
        this.coverageRepository = coverageRepository;
        this.adminService = adminService;
        this.patientApi = patientApi;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CoverageResponse> listForPatient(UUID patientId) {
        return coverageRepository.findByPatientIdOrderByPriorityAsc(patientId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CoverageResponse create(CoverageRequest request) {
        validate(request);
        PatientInsurance coverage = new PatientInsurance(
                request.patientId(), request.planId(), request.subscriberPatientId(),
                PatientInsurance.Relationship.valueOf(request.relationshipToSubscriber()),
                request.memberId(),
                PatientInsurance.Priority.valueOf(request.priority()));
        coverage.setDates(request.effectiveDate(), request.terminationDate());
        coverage = coverageRepository.save(coverage);

        publish(request.patientId(), coverage.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("memberId", coverage.getMemberId(), "priority", request.priority()));
        return toResponse(coverage);
    }

    public CoverageResponse update(UUID id, CoverageRequest request) {
        PatientInsurance coverage = findCoverage(id);
        if (!coverage.getPatientId().equals(request.patientId())) {
            throw new InvalidRequestException("Coverage cannot move between patients");
        }
        validate(request);
        coverage.update(request.planId(), request.subscriberPatientId(),
                PatientInsurance.Relationship.valueOf(request.relationshipToSubscriber()),
                request.memberId(),
                PatientInsurance.Priority.valueOf(request.priority()),
                request.effectiveDate(), request.terminationDate());

        publish(coverage.getPatientId(), id, AuditEvent.AuditAction.UPDATE,
                Map.of("memberId", coverage.getMemberId(), "priority", request.priority()));
        return toResponse(coverage);
    }

    public void delete(UUID id) {
        PatientInsurance coverage = findCoverage(id);
        coverageRepository.delete(coverage); // soft delete
        publish(coverage.getPatientId(), id, AuditEvent.AuditAction.DELETE,
                Map.of("memberId", coverage.getMemberId()));
    }

    PatientInsurance findCoverage(UUID id) {
        return coverageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient insurance", id));
    }

    // ---- helpers ----

    private void validate(CoverageRequest request) {
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        if (!patientApi.exists(request.subscriberPatientId())) {
            throw new InvalidRequestException("Unknown subscriber");
        }
        adminService.requirePlan(request.planId());
        boolean isSelf = "SELF".equals(request.relationshipToSubscriber());
        boolean samePerson = request.patientId().equals(request.subscriberPatientId());
        if (isSelf && !samePerson) {
            throw new InvalidRequestException(
                    "Relationship SELF requires the subscriber to be the patient");
        }
        if (!isSelf && samePerson) {
            throw new InvalidRequestException(
                    "Use relationship SELF when the patient is the subscriber");
        }
        if (request.effectiveDate() != null && request.terminationDate() != null
                && request.terminationDate().isBefore(request.effectiveDate())) {
            throw new InvalidRequestException("Termination date cannot precede effective date");
        }
    }

    private void publish(UUID patientId, UUID coverageId, AuditEvent.AuditAction action,
                         Map<String, Object> details) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "PatientInsurance", coverageId, action,
                null, details));
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "Patient", patientId, AuditEvent.AuditAction.UPDATE,
                null, Map.of("insurance", action.name())));
    }

    CoverageResponse toResponse(PatientInsurance coverage) {
        InsurancePlan plan = adminService.requirePlan(coverage.getPlanId());
        InsuranceCarrier carrier = adminService.findCarrier(plan.getCarrierId());
        PatientSummary subscriber = patientApi.findSummary(coverage.getSubscriberPatientId())
                .orElse(null);
        return new CoverageResponse(
                coverage.getId(), coverage.getPatientId(), plan.getId(), plan.getPlanName(),
                plan.getPlanType().name(), carrier.getName(),
                coverage.getSubscriberPatientId(),
                subscriber != null ? subscriber.firstName() : null,
                subscriber != null ? subscriber.lastName() : null,
                coverage.getRelationshipToSubscriber().name(), coverage.getMemberId(),
                coverage.getPriority().name(), coverage.getEffectiveDate(),
                coverage.getTerminationDate());
    }
}
