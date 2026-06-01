package com.dentalcore.users.internal.web;

import com.dentalcore.users.internal.dto.PermissionDtos.PermissionMatrixResponse;
import com.dentalcore.users.internal.dto.PermissionDtos.RolePermissionsResponse;
import com.dentalcore.users.internal.dto.PermissionDtos.UpdateRolePermissionsRequest;
import com.dentalcore.users.internal.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Permissions", description = "Role permission matrix administration")
public class PermissionAdminController {

    private static final String CAN_MANAGE_PERMISSIONS = "hasAuthority('PERMISSIONS_MANAGE')";

    private final PermissionService permissionService;

    public PermissionAdminController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/permissions")
    @PreAuthorize(CAN_MANAGE_PERMISSIONS)
    @Operation(summary = "The permission catalog and each role's granted codes")
    public PermissionMatrixResponse matrix() {
        return permissionService.matrix();
    }

    @PutMapping("/roles/{roleName}/permissions")
    @PreAuthorize(CAN_MANAGE_PERMISSIONS)
    @Operation(summary = "Replace a role's permission grants (applies without re-login)")
    public RolePermissionsResponse updateRolePermissions(
            @PathVariable String roleName,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        List<String> updated = permissionService.updateRoleGrants(
                roleName, request.permissionCodes());
        return new RolePermissionsResponse(roleName, updated);
    }
}
