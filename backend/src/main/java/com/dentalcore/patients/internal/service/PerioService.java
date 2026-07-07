package com.dentalcore.patients.internal.service;

import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.patients.internal.dto.PerioDtos.CreateExamRequest;
import com.dentalcore.patients.internal.dto.PerioDtos.ExamResponse;
import com.dentalcore.patients.internal.dto.PerioDtos.ExamSummaryResponse;
import com.dentalcore.patients.internal.dto.PerioDtos.SaveMeasurementsRequest;
import com.dentalcore.patients.internal.dto.PerioDtos.SiteMeasurement;
import com.dentalcore.patients.internal.dto.PerioDtos.SiteResponse;
import com.dentalcore.patients.internal.dto.PerioDtos.ToothFinding;
import com.dentalcore.patients.internal.dto.PerioDtos.ToothFindingResponse;
import com.dentalcore.patients.internal.entity.PerioExam;
import com.dentalcore.patients.internal.entity.PerioMeasurement;
import com.dentalcore.patients.internal.repository.PerioExamRepository;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class PerioService {

    /** Perio charting applies to permanent dentition: FDI teeth 11-18, 21-28, 31-38, 41-48. */
    private static final Pattern PERMANENT_TOOTH = Pattern.compile("^[1-4][1-8]$");
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PerioExamRepository examRepository;
    private final PatientService patientService;
    private final ClinicTimeService clinicTime;
    private final ApplicationEventPublisher events;

    public PerioService(PerioExamRepository examRepository,
                        PatientService patientService,
                        ClinicTimeService clinicTime,
                        ApplicationEventPublisher events) {
        this.examRepository = examRepository;
        this.patientService = patientService;
        this.clinicTime = clinicTime;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> listExams(UUID patientId) {
        patientService.findPatient(patientId);
        return examRepository.findByPatientIdOrderByExamDateDescCreatedAtDesc(patientId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(UUID patientId, UUID examId) {
        return toResponse(findOwned(patientId, examId));
    }

    public ExamResponse createExam(UUID patientId, CreateExamRequest request) {
        patientService.findPatient(patientId);
        PerioExam exam = examRepository.save(new PerioExam(
                DEFAULT_CLINIC_ID, patientId, request.providerId(),
                request.examDate() != null ? request.examDate()
                        : clinicTime.today(DEFAULT_CLINIC_ID),
                request.notes()));
        publish(patientId, Map.of("perioExamCreated", exam.getExamDate().toString()));
        return toResponse(exam);
    }

    public ExamResponse saveMeasurements(UUID patientId, UUID examId,
                                         SaveMeasurementsRequest request) {
        PerioExam exam = findOwned(patientId, examId);
        for (SiteMeasurement m : request.measurementsOrEmpty()) {
            requirePermanentTooth(m.tooth());
            exam.upsertMeasurement(m.tooth().trim(), m.site(),
                    m.pocketDepth(), m.recession(),
                    Boolean.TRUE.equals(m.bleeding()),
                    Boolean.TRUE.equals(m.suppuration()));
        }
        for (ToothFinding f : request.toothFindingsOrEmpty()) {
            requirePermanentTooth(f.tooth());
            exam.upsertToothFinding(f.tooth().trim(), f.mobility(), f.furcation());
        }
        if (request.notes() != null) {
            exam.setNotes(request.notes());
        }
        publish(patientId, Map.of(
                "perioMeasurementsSaved", request.measurementsOrEmpty().size(),
                "examId", examId.toString()));
        return toResponse(exam);
    }

    public void deleteExam(UUID patientId, UUID examId) {
        PerioExam exam = findOwned(patientId, examId);
        examRepository.delete(exam);
        publish(patientId, Map.of("perioExamDeleted", exam.getExamDate().toString()));
    }

    // ---- helpers ----

    private void requirePermanentTooth(String tooth) {
        if (tooth == null || !PERMANENT_TOOTH.matcher(tooth.trim()).matches()) {
            throw new InvalidRequestException(
                    "Perio charting uses FDI permanent teeth 11-48, got '%s'".formatted(tooth));
        }
    }

    private PerioExam findOwned(UUID patientId, UUID examId) {
        return examRepository.findById(examId)
                .filter(exam -> exam.getPatientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Perio exam", examId));
    }

    private void publish(UUID patientId, Map<String, Object> details) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), PatientService.ENTITY_TYPE, patientId,
                AuditEvent.AuditAction.UPDATE, null, details));
    }

    private ExamSummaryResponse toSummary(PerioExam exam) {
        List<PerioMeasurement> recorded = exam.getMeasurements().stream()
                .filter(m -> m.getPocketDepth() != null)
                .toList();
        return new ExamSummaryResponse(
                exam.getId(), exam.getExamDate(), exam.getProviderId(),
                recorded.size(),
                (int) exam.getMeasurements().stream().filter(PerioMeasurement::isBleeding).count(),
                (int) recorded.stream().filter(m -> m.getPocketDepth() >= 4).count(),
                (int) recorded.stream().filter(m -> m.getPocketDepth() >= 6).count());
    }

    private ExamResponse toResponse(PerioExam exam) {
        return new ExamResponse(
                exam.getId(), exam.getPatientId(), exam.getProviderId(), exam.getExamDate(),
                exam.getNotes(),
                exam.getMeasurements().stream()
                        .sorted(Comparator.comparing(PerioMeasurement::getTooth)
                                .thenComparing(PerioMeasurement::getSite))
                        .map(m -> new SiteResponse(m.getTooth(), m.getSite(), m.getPocketDepth(),
                                m.getRecession(), m.isBleeding(), m.isSuppuration()))
                        .toList(),
                exam.getToothFindings().stream()
                        .map(f -> new ToothFindingResponse(f.getTooth(), f.getMobility(),
                                f.getFurcation()))
                        .toList());
    }
}
