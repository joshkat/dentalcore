package com.dentalcore.appointments.internal.web;

import com.dentalcore.appointments.internal.dto.AppointmentRequest;
import com.dentalcore.appointments.internal.dto.AppointmentResponse;
import com.dentalcore.appointments.internal.dto.SetAppointmentProceduresRequest;
import com.dentalcore.appointments.internal.dto.UpdateAppointmentStatusRequest;
import com.dentalcore.appointments.internal.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointments")
public class AppointmentController {

    private static final String CAN_WRITE =
            "hasAuthority('APPOINTMENTS_WRITE')";

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @Operation(summary = "List appointments in a time range, optionally filtered")
    public List<AppointmentResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) UUID operatoryId,
            @RequestParam(required = false) UUID patientId) {
        return appointmentService.list(from, to, providerId, operatoryId, patientId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an appointment")
    public AppointmentResponse get(@PathVariable UUID id) {
        return appointmentService.get(id);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book an appointment (rejects double-booking)")
    public AppointmentResponse create(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Reschedule or edit an appointment")
    public AppointmentResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Move an appointment through its status lifecycle")
    public AppointmentResponse updateStatus(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateStatus(id, request.status(), request.cancelReason());
    }

    @PutMapping("/{id}/procedures")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Set the planned procedures for a visit")
    public AppointmentResponse setProcedures(@PathVariable UUID id,
                                             @Valid @RequestBody SetAppointmentProceduresRequest request) {
        return appointmentService.setProcedures(id, request.procedureCodeIds());
    }

    @PostMapping("/recurring")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a recurring series; conflicting occurrences are skipped")
    public AppointmentService.RecurringResult createRecurring(
            @Valid @RequestBody RecurringRequest request) {
        return appointmentService.createRecurring(request.base(), request.frequency(),
                request.occurrences());
    }

    @PostMapping("/{id}/send-confirmation")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Send a confirmation request to the patient")
    public AppointmentResponse sendConfirmation(@PathVariable UUID id) {
        return appointmentService.sendConfirmation(id);
    }

    public record RecurringRequest(@Valid @jakarta.validation.constraints.NotNull AppointmentRequest base,
                                   @jakarta.validation.constraints.NotNull String frequency,
                                   int occurrences) {
    }
}
