package com.dentalcore.users.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class PermissionDtos {

    private PermissionDtos() {
    }

    public record PermissionEntry(String code, String description, String category) {
    }

    /** The full matrix: the permission catalog plus each role's granted codes. */
    public record PermissionMatrixResponse(
            List<PermissionEntry> permissions,
            Map<String, List<String>> roles
    ) {
    }

    public record UpdateRolePermissionsRequest(@NotNull List<String> permissionCodes) {
    }

    public record RolePermissionsResponse(String role, List<String> permissionCodes) {
    }
}
