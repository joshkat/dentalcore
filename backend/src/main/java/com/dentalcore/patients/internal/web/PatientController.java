package com.dentalcore.patients.internal.web;

import com.dentalcore.patients.internal.dto.FamilyLinkRequest;
import com.dentalcore.patients.internal.dto.FamilyLinkResponse;
import com.dentalcore.patients.internal.dto.MedicalAlertRequest;
import com.dentalcore.patients.internal.dto.MedicalAlertResponse;
import com.dentalcore.patients.internal.dto.PatientMergeDtos;
import com.dentalcore.patients.internal.dto.PatientRequest;
import com.dentalcore.patients.internal.dto.PatientResponse;
import com.dentalcore.patients.internal.dto.PatientSummaryResponse;
import com.dentalcore.patients.internal.dto.TimelineEventResponse;
import com.dentalcore.patients.internal.dto.UpdatePatientStatusRequest;
import com.dentalcore.patients.internal.service.FamilyLinkService;
import com.dentalcore.patients.internal.service.MedicalAlertService;
import com.dentalcore.patients.internal.service.PatientMergeService;
import com.dentalcore.patients.internal.service.PatientService;
import com.dentalcore.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients")
public class PatientController {

    private static final String CAN_WRITE =
            "hasAnyRole('ADMIN','DENTIST','HYGIENIST','FRONT_DESK')";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE =
            Set.of("lastName", "firstName", "dateOfBirth", "createdAt");

    private final PatientService patientService;
    private final MedicalAlertService alertService;
    private final FamilyLinkService familyLinkService;
    private final PatientMergeService mergeService;

    public PatientController(PatientService patientService,
                             MedicalAlertService alertService,
                             FamilyLinkService familyLinkService,
                             PatientMergeService mergeService) {
        this.patientService = patientService;
        this.alertService = alertService;
        this.familyLinkService = familyLinkService;
        this.mergeService = mergeService;
    }

    @GetMapping
    @Operation(summary = "Search patients by name, email, or phone")
    public PageResponse<PatientSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "lastName,asc") String sort) {
        String[] parts = sort.split(",");
        String property = SORTABLE.contains(parts[0]) ? parts[0] : "lastName";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, property));
        return PageResponse.from(patientService.search(q, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full patient details")
    public PatientResponse get(@PathVariable UUID id) {
        return patientService.get(id);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new patient")
    public PatientResponse create(@Valid @RequestBody PatientRequest request) {
        return patientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update patient demographics and phones")
    public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
        return patientService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Change patient status (ACTIVE/INACTIVE/ARCHIVED)")
    public PatientResponse updateStatus(@PathVariable UUID id,
                                        @Valid @RequestBody UpdatePatientStatusRequest request) {
        return patientService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a patient (ADMIN only)")
    public void delete(@PathVariable UUID id) {
        patientService.delete(id);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Patient activity timeline")
    public List<TimelineEventResponse> timeline(@PathVariable UUID id) {
        return patientService.timeline(id);
    }

    public record RecallRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(36)
            Integer intervalMonths,
            java.time.LocalDate nextRecallDate
    ) {
    }

    @PatchMapping("/{id}/recall")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Set the recall interval and next recall date")
    public PatientResponse updateRecall(@PathVariable UUID id,
                                        @Valid @RequestBody RecallRequest request) {
        return patientService.updateRecall(id, request.intervalMonths(), request.nextRecallDate());
    }

    // ---- guarantor (family billing) ----

    /** Billing staff manage who guarantees an account; null clears it (self-guaranteed). */
    private static final String CAN_SET_GUARANTOR =
            "hasAnyRole('ADMIN','FRONT_DESK','BILLING')";

    public record GuarantorRequest(UUID guarantorPatientId) {
    }

    @PutMapping("/{id}/guarantor")
    @PreAuthorize(CAN_SET_GUARANTOR)
    @Operation(summary = "Set or clear (null) the account guarantor — one level only")
    public PatientResponse updateGuarantor(@PathVariable UUID id,
                                           @RequestBody GuarantorRequest request) {
        return patientService.updateGuarantor(id, request.guarantorPatientId());
    }

    // ---- Duplicate detection & merge ----

    /** Merging is destructive bookkeeping; admins only. */
    private static final String CAN_MERGE = "hasAnyRole('ADMIN')";

    @GetMapping("/duplicates")
    @PreAuthorize(CAN_MERGE)
    @Operation(summary = "Scan for potential duplicate patients (ADMIN only)")
    public List<PatientMergeDtos.DuplicateCandidateResponse> duplicates() {
        return mergeService.findDuplicates();
    }

    @PostMapping("/{targetId}/merge")
    @PreAuthorize(CAN_MERGE)
    @Operation(summary = "Merge a duplicate (source) patient into this target (ADMIN only)")
    public PatientMergeDtos.MergeResponse merge(
            @PathVariable UUID targetId,
            @Valid @RequestBody PatientMergeDtos.MergeRequest request) {
        return mergeService.merge(targetId, request.sourceId());
    }

    // ---- Medical alerts ----

    @GetMapping("/{id}/alerts")
    @Operation(summary = "List medical alerts")
    public List<MedicalAlertResponse> listAlerts(@PathVariable UUID id) {
        return alertService.list(id);
    }

    @PostMapping("/{id}/alerts")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a medical alert")
    public MedicalAlertResponse createAlert(@PathVariable UUID id,
                                            @Valid @RequestBody MedicalAlertRequest request) {
        return alertService.create(id, request);
    }

    @PutMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update a medical alert")
    public MedicalAlertResponse updateAlert(@PathVariable UUID id, @PathVariable UUID alertId,
                                            @Valid @RequestBody MedicalAlertRequest request) {
        return alertService.update(id, alertId, request);
    }

    @DeleteMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a medical alert")
    public void deleteAlert(@PathVariable UUID id, @PathVariable UUID alertId) {
        alertService.delete(id, alertId);
    }

    // ---- Family links ----

    @GetMapping("/{id}/family")
    @Operation(summary = "List family relationships")
    public List<FamilyLinkResponse> listFamily(@PathVariable UUID id) {
        return familyLinkService.list(id);
    }

    @PostMapping("/{id}/family")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Link a family member (reverse link is created automatically)")
    public FamilyLinkResponse createFamilyLink(@PathVariable UUID id,
                                               @Valid @RequestBody FamilyLinkRequest request) {
        return familyLinkService.create(id, request);
    }

    @DeleteMapping("/{id}/family/{linkId}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a family link (both directions)")
    public void deleteFamilyLink(@PathVariable UUID id, @PathVariable UUID linkId) {
        familyLinkService.delete(id, linkId);
    }
}
