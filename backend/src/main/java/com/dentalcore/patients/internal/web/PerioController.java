package com.dentalcore.patients.internal.web;

import com.dentalcore.patients.internal.dto.PerioDtos.CreateExamRequest;
import com.dentalcore.patients.internal.dto.PerioDtos.ExamResponse;
import com.dentalcore.patients.internal.dto.PerioDtos.ExamSummaryResponse;
import com.dentalcore.patients.internal.dto.PerioDtos.SaveMeasurementsRequest;
import com.dentalcore.patients.internal.service.PerioService;
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
@RequestMapping("/api/v1/patients/{patientId}/perio-exams")
@Tag(name = "Perio Charting", description = "Periodontal exams and 6-site probing")
public class PerioController {

    private static final String CAN_CHART = "hasAnyRole('ADMIN','DENTIST','HYGIENIST')";

    private final PerioService service;

    public PerioController(PerioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Exam history with summary stats, newest first")
    public List<ExamSummaryResponse> list(@PathVariable UUID patientId) {
        return service.listExams(patientId);
    }

    @GetMapping("/{examId}")
    @Operation(summary = "Full exam grid")
    public ExamResponse get(@PathVariable UUID patientId, @PathVariable UUID examId) {
        return service.getExam(patientId, examId);
    }

    @PostMapping
    @PreAuthorize(CAN_CHART)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a perio exam")
    public ExamResponse create(@PathVariable UUID patientId,
                               @Valid @RequestBody CreateExamRequest request) {
        return service.createExam(patientId, request);
    }

    @PutMapping("/{examId}/measurements")
    @PreAuthorize(CAN_CHART)
    @Operation(summary = "Batch upsert site measurements and tooth findings")
    public ExamResponse saveMeasurements(@PathVariable UUID patientId,
                                         @PathVariable UUID examId,
                                         @Valid @RequestBody SaveMeasurementsRequest request) {
        return service.saveMeasurements(patientId, examId, request);
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize(CAN_CHART)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an exam")
    public void delete(@PathVariable UUID patientId, @PathVariable UUID examId) {
        service.deleteExam(patientId, examId);
    }
}
