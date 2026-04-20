package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.CarrierRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CarrierResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.PlanRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.PlanResponse;
import com.dentalcore.insurance.internal.entity.InsuranceCarrier;
import com.dentalcore.insurance.internal.entity.InsurancePlan;
import com.dentalcore.insurance.internal.repository.InsuranceCarrierRepository;
import com.dentalcore.insurance.internal.repository.InsurancePlanRepository;
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
public class InsuranceAdminService {

    private final InsuranceCarrierRepository carrierRepository;
    private final InsurancePlanRepository planRepository;
    private final ApplicationEventPublisher events;

    public InsuranceAdminService(InsuranceCarrierRepository carrierRepository,
                                 InsurancePlanRepository planRepository,
                                 ApplicationEventPublisher events) {
        this.carrierRepository = carrierRepository;
        this.planRepository = planRepository;
        this.events = events;
    }

    // ---- carriers ----

    @Transactional(readOnly = true)
    public Page<CarrierResponse> listCarriers(String q, Pageable pageable) {
        Page<InsuranceCarrier> page = (q == null || q.isBlank())
                ? carrierRepository.findAll(pageable)
                : carrierRepository.search(q.trim(), pageable);
        return page.map(this::toCarrierResponse);
    }

    public CarrierResponse createCarrier(CarrierRequest request) {
        InsuranceCarrier carrier = new InsuranceCarrier(request.name());
        applyCarrier(carrier, request);
        carrier = carrierRepository.save(carrier);
        publish("InsuranceCarrier", carrier.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("name", carrier.getName()));
        return toCarrierResponse(carrier);
    }

    public CarrierResponse updateCarrier(UUID id, CarrierRequest request) {
        InsuranceCarrier carrier = findCarrier(id);
        applyCarrier(carrier, request);
        publish("InsuranceCarrier", id, AuditEvent.AuditAction.UPDATE,
                Map.of("name", carrier.getName()));
        return toCarrierResponse(carrier);
    }

    // ---- plans ----

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(UUID carrierId) {
        findCarrier(carrierId);
        return planRepository.findByCarrierIdOrderByPlanName(carrierId).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    public PlanResponse createPlan(PlanRequest request) {
        findCarrier(request.carrierId());
        InsurancePlan plan = new InsurancePlan(request.carrierId(), request.planName(),
                InsurancePlan.PlanType.valueOf(request.planType()));
        applyPlan(plan, request);
        plan = planRepository.save(plan);
        publish("InsurancePlan", plan.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("plan", plan.getPlanName()));
        return toPlanResponse(plan);
    }

    public PlanResponse updatePlan(UUID id, PlanRequest request) {
        InsurancePlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance plan", id));
        if (!plan.getCarrierId().equals(request.carrierId())) {
            throw new InvalidRequestException("A plan cannot move between carriers");
        }
        applyPlan(plan, request);
        publish("InsurancePlan", id, AuditEvent.AuditAction.UPDATE,
                Map.of("plan", plan.getPlanName()));
        return toPlanResponse(plan);
    }

    // ---- lookups used by sibling services ----

    InsuranceCarrier findCarrier(UUID id) {
        return carrierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance carrier", id));
    }

    InsurancePlan requirePlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Unknown insurance plan"));
    }

    // ---- helpers ----

    private void applyCarrier(InsuranceCarrier carrier, CarrierRequest request) {
        carrier.update(request.name(), request.payerId(), request.phone(),
                request.addressLine1(), request.addressLine2(), request.city(),
                request.state(), request.postalCode());
    }

    private void applyPlan(InsurancePlan plan, PlanRequest request) {
        plan.update(request.planName(), request.groupNumber(),
                InsurancePlan.PlanType.valueOf(request.planType()),
                request.annualMax(), request.deductible(), request.coverageNotes());
    }

    private void publish(String entityType, UUID entityId, AuditEvent.AuditAction action,
                         Map<String, Object> details) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), entityType, entityId, action, null, details));
    }

    private CarrierResponse toCarrierResponse(InsuranceCarrier carrier) {
        return new CarrierResponse(
                carrier.getId(), carrier.getName(), carrier.getPayerId(), carrier.getPhone(),
                carrier.getAddressLine1(), carrier.getAddressLine2(), carrier.getCity(),
                carrier.getState(), carrier.getPostalCode(),
                planRepository.findByCarrierIdOrderByPlanName(carrier.getId()).size());
    }

    PlanResponse toPlanResponse(InsurancePlan plan) {
        InsuranceCarrier carrier = findCarrier(plan.getCarrierId());
        return new PlanResponse(
                plan.getId(), plan.getCarrierId(), carrier.getName(), plan.getPlanName(),
                plan.getGroupNumber(), plan.getPlanType().name(), plan.getAnnualMax(),
                plan.getDeductible(), plan.getCoverageNotes(), plan.getFeeScheduleId());
    }
}
