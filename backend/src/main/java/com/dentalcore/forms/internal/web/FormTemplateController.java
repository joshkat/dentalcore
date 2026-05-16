package com.dentalcore.forms.internal.web;

import com.dentalcore.forms.internal.dto.FormDtos.TemplateRequest;
import com.dentalcore.forms.internal.dto.FormDtos.TemplateResponse;
import com.dentalcore.forms.internal.service.FormTemplateService;
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
@RequestMapping("/api/v1/forms/templates")
@Tag(name = "Form Templates", description = "Reusable patient form definitions")
public class FormTemplateController {

    private static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private final FormTemplateService service;

    public FormTemplateController(FormTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List form templates (active first, then by name)")
    public List<TemplateResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a form template")
    public TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    @Operation(summary = "Update a form template")
    public TemplateResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody TemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a template (deactivates instead when instances exist)")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
