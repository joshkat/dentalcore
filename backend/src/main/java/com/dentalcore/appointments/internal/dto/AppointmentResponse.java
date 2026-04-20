package com.dentalcore.appointments.internal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        String patientFirstName,
        String patientLastName,
        UUID providerId,
        String providerFirstName,
        String providerLastName,
        UUID operatoryId,
        String operatoryName,
        Instant startsAt,
        Instant endsAt,
        String status,
        String notes,
        String color,
        String cancelledReason,
        List<ProcedureDto> procedures,
        Instant createdAt,
        Instant updatedAt
) {
    public record ProcedureDto(
            UUID procedureCodeId,
            String code,
            String description,
            BigDecimal standardFee
    ) {
    }
}
