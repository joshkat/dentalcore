package com.dentalcore.forms.internal.web;

import com.dentalcore.forms.internal.dto.FormDtos.AnswersRequest;
import com.dentalcore.forms.internal.dto.FormDtos.InstanceCreateRequest;
import com.dentalcore.forms.internal.dto.FormDtos.InstanceResponse;
import com.dentalcore.forms.internal.dto.FormDtos.SignRequest;
import com.dentalcore.forms.internal.service.FormInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forms/instances")
@Tag(name = "Form Instances", description = "Patient form fill-out and e-signature")
public class FormInstanceController {

    private static final String CAN_WRITE =
            "hasAuthority('FORMS_FILL')";

    private final FormInstanceService service;

    public FormInstanceController(FormInstanceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a form for a patient from a template")
    public InstanceResponse create(@Valid @RequestBody InstanceCreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    @Operation(summary = "List a patient's form instances, newest first")
    public List<InstanceResponse> list(@RequestParam UUID patientId) {
        return service.listForPatient(patientId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a form instance")
    public InstanceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/answers")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Replace the answers (status follows required-field completeness)")
    public InstanceResponse updateAnswers(@PathVariable UUID id,
                                          @Valid @RequestBody AnswersRequest request) {
        return service.updateAnswers(id, request);
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Sign a completed form: renders the PDF into the patient's documents")
    public InstanceResponse sign(@PathVariable UUID id, @Valid @RequestBody SignRequest request) {
        return service.sign(id, request);
    }
}
