package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.patients.internal.mapper.PatientMapper;
import com.dentalcore.patients.internal.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PatientApiService implements PatientApi {

    private final PatientRepository patientRepository;
    private final PatientMapper mapper;

    public PatientApiService(PatientRepository patientRepository, PatientMapper mapper) {
        this.patientRepository = patientRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PatientSummary> findSummary(UUID patientId) {
        return patientRepository.findById(patientId).map(mapper::toApiSummary);
    }

    @Override
    public java.util.Map<UUID, PatientSummary> findSummaries(java.util.Set<UUID> patientIds) {
        return patientRepository.findAllById(patientIds).stream()
                .map(mapper::toApiSummary)
                .collect(java.util.stream.Collectors.toMap(PatientSummary::id, s -> s));
    }

    @Override
    public boolean exists(UUID patientId) {
        return patientRepository.existsById(patientId);
    }
}
