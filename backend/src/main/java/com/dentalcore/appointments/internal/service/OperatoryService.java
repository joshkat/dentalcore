package com.dentalcore.appointments.internal.service;

import com.dentalcore.appointments.internal.dto.OperatoryRequest;
import com.dentalcore.appointments.internal.dto.OperatoryResponse;
import com.dentalcore.appointments.internal.entity.Operatory;
import com.dentalcore.appointments.internal.repository.OperatoryRepository;
import com.dentalcore.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OperatoryService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final OperatoryRepository operatoryRepository;

    public OperatoryService(OperatoryRepository operatoryRepository) {
        this.operatoryRepository = operatoryRepository;
    }

    @Transactional(readOnly = true)
    public List<OperatoryResponse> list(boolean includeInactive) {
        List<Operatory> operatories = includeInactive
                ? operatoryRepository.findAllByOrderByName()
                : operatoryRepository.findByActiveTrueOrderByName();
        return operatories.stream().map(this::toResponse).toList();
    }

    public OperatoryResponse create(OperatoryRequest request) {
        Operatory operatory = new Operatory(DEFAULT_CLINIC_ID, request.name());
        operatory.update(request.name(), request.activeOrDefault());
        return toResponse(operatoryRepository.save(operatory));
    }

    public OperatoryResponse update(UUID id, OperatoryRequest request) {
        Operatory operatory = operatoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operatory", id));
        operatory.update(request.name(), request.activeOrDefault());
        return toResponse(operatory);
    }

    private OperatoryResponse toResponse(Operatory operatory) {
        return new OperatoryResponse(operatory.getId(), operatory.getName(), operatory.isActive());
    }
}
