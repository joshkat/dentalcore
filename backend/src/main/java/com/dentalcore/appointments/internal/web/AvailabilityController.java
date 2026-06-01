package com.dentalcore.appointments.internal.web;

import com.dentalcore.appointments.internal.service.AvailabilityService;
import com.dentalcore.appointments.internal.service.AvailabilityService.HoursBlock;
import com.dentalcore.appointments.internal.service.AvailabilityService.TimeOffRequest;
import com.dentalcore.appointments.internal.service.AvailabilityService.TimeOffResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/providers/{providerId}")
@Tag(name = "Provider Availability", description = "Working hours and time off")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/hours")
    @Operation(summary = "Weekly working-hours template (empty = always bookable)")
    public List<HoursBlock> hours(@PathVariable UUID providerId) {
        return service.hoursFor(providerId);
    }

    @PutMapping("/hours")
    @PreAuthorize("hasAuthority('PROVIDERS_MANAGE')")
    @Operation(summary = "Replace the weekly working-hours template (ADMIN)")
    public List<HoursBlock> replaceHours(@PathVariable UUID providerId,
                                         @RequestBody List<@Valid HoursBlock> blocks) {
        return service.replaceHours(providerId, blocks);
    }

    @GetMapping("/time-off")
    @Operation(summary = "Time-off blocks")
    public List<TimeOffResponse> timeOff(@PathVariable UUID providerId) {
        return service.timeOffFor(providerId);
    }

    @PostMapping("/time-off")
    @PreAuthorize("hasAuthority('PROVIDERS_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Block time off (ADMIN)")
    public TimeOffResponse addTimeOff(@PathVariable UUID providerId,
                                      @Valid @RequestBody TimeOffRequest request) {
        return service.addTimeOff(providerId, request);
    }

    @DeleteMapping("/time-off/{timeOffId}")
    @PreAuthorize("hasAuthority('PROVIDERS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a time-off block (ADMIN)")
    public void removeTimeOff(@PathVariable UUID providerId, @PathVariable UUID timeOffId) {
        service.removeTimeOff(providerId, timeOffId);
    }
}
