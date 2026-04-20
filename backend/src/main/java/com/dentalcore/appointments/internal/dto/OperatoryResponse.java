package com.dentalcore.appointments.internal.dto;

import java.util.UUID;

public record OperatoryResponse(UUID id, String name, boolean active) {
}
