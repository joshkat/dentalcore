package com.dentalcore.providers.internal.web;

import com.dentalcore.providers.internal.dto.ProviderRequest;
import com.dentalcore.providers.internal.dto.ProviderResponse;
import com.dentalcore.providers.internal.service.ProviderService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/providers")
@Tag(name = "Providers")
public class ProviderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    @Operation(summary = "List providers")
    public PageResponse<ProviderResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "lastName"));
        return PageResponse.from(providerService.list(includeInactive, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a provider")
    public ProviderResponse get(@PathVariable UUID id) {
        return providerService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a provider (ADMIN only)")
    public ProviderResponse create(@Valid @RequestBody ProviderRequest request) {
        return providerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a provider (ADMIN only)")
    public ProviderResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody ProviderRequest request) {
        return providerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a provider (ADMIN only)")
    public void delete(@PathVariable UUID id) {
        providerService.delete(id);
    }
}
