package com.dentalcore.patients.internal.service;

import com.dentalcore.appointments.api.AppointmentCompletedEvent;
import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.patients.internal.repository.PatientRepository;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.providers.api.ProviderApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Completing a hygiene visit (hygienist provider, or a prophy code on the
 * visit) advances the patient's next recall date by their recall interval.
 */
@Component
public class RecallBumpListener {

    private static final Logger log = LoggerFactory.getLogger(RecallBumpListener.class);
    private static final Set<String> HYGIENE_CODES = Set.of("D1110", "D1120");

    private final PatientRepository patientRepository;
    private final ProviderApi providerApi;
    private final ProcedureCatalogApi catalogApi;
    private final ClinicTimeService clinicTime;

    public RecallBumpListener(PatientRepository patientRepository,
                              ProviderApi providerApi,
                              ProcedureCatalogApi catalogApi,
                              ClinicTimeService clinicTime) {
        this.patientRepository = patientRepository;
        this.providerApi = providerApi;
        this.catalogApi = catalogApi;
        this.clinicTime = clinicTime;
    }

    @EventListener
    @Transactional
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        boolean hygienist = providerApi.findSummary(event.providerId())
                .map(p -> "HYGIENIST".equals(p.type()))
                .orElse(false);
        boolean prophy = !event.procedureCodeIds().isEmpty()
                && catalogApi.findSummaries(Set.copyOf(event.procedureCodeIds())).values().stream()
                .map(ProcedureSummary::code)
                .anyMatch(HYGIENE_CODES::contains);
        if (!hygienist && !prophy) {
            return;
        }
        patientRepository.findById(event.patientId()).ifPresent(patient -> {
            LocalDate next = clinicTime.today(event.clinicId())
                    .plusMonths(patient.getRecallIntervalMonths());
            patient.updateRecall(patient.getRecallIntervalMonths(), next);
            log.info("Recall bumped to {} for patient {} after hygiene visit", next,
                    patient.getId());
        });
    }
}
