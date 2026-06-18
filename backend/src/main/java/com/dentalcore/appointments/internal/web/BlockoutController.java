package com.dentalcore.appointments.internal.web;

import com.dentalcore.appointments.internal.service.BlockoutService;
import com.dentalcore.appointments.internal.service.BlockoutService.BlockoutRequest;
import com.dentalcore.appointments.internal.service.BlockoutService.BlockoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blockouts")
@Tag(name = "Operatory Blockouts", description = "Spans during which a chair cannot be booked")
public class BlockoutController {

    private static final String CAN_WRITE = "hasAuthority('APPOINTMENTS_WRITE')";

    private final BlockoutService service;

    public BlockoutController(BlockoutService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Blockouts overlapping a time window")
    public List<BlockoutResponse> list(@RequestParam Instant from, @RequestParam Instant to) {
        return service.list(from, to);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Block an operatory for a span")
    public BlockoutResponse create(@Valid @RequestBody BlockoutRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a blockout")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
