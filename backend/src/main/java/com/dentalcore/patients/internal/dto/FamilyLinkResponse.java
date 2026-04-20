package com.dentalcore.patients.internal.dto;

import java.util.UUID;

public record FamilyLinkResponse(
        UUID id,
        UUID relatedPatientId,
        String relatedPatientFirstName,
        String relatedPatientLastName,
        String relationship
) {
}
