package com.dentalcore.users.internal.service;

import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.users.api.PermissionsApi;
import com.dentalcore.users.internal.dto.PermissionDtos.PermissionEntry;
import com.dentalcore.users.internal.dto.PermissionDtos.PermissionMatrixResponse;
import com.dentalcore.users.internal.entity.Permission;
import com.dentalcore.users.internal.entity.Role;
import com.dentalcore.users.internal.repository.PermissionRepository;
import com.dentalcore.users.internal.repository.RoleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves role -> permission-code grants with a small per-role cache so the
 * JWT filter can expand authorities on every request without a query storm.
 * The cache is invalidated explicitly when the matrix changes, which is what
 * makes matrix edits apply immediately without re-login.
 */
@Service
public class PermissionService implements PermissionsApi {

    /** Grants the ADMIN role can never lose, to prevent administrative lockout. */
    private static final Set<String> ADMIN_LOCKOUT_GUARD =
            Set.of("PERMISSIONS_MANAGE", "USERS_MANAGE");

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;

    private final ConcurrentHashMap<String, Set<String>> cacheByRole = new ConcurrentHashMap<>();

    public PermissionService(PermissionRepository permissionRepository,
                             RoleRepository roleRepository,
                             ApplicationEventPublisher events) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.events = events;
    }

    @Override
    public Set<String> permissionCodesFor(Set<String> roleNames) {
        Set<String> union = new HashSet<>();
        for (String role : roleNames) {
            union.addAll(cacheByRole.computeIfAbsent(role,
                    name -> Set.copyOf(permissionRepository.findCodesByRoleName(name))));
        }
        return union;
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse matrix() {
        List<PermissionEntry> permissions = permissionRepository
                .findAll(Sort.by("category", "code")).stream()
                .map(p -> new PermissionEntry(p.getCode(), p.getDescription(), p.getCategory()))
                .toList();
        Map<String, List<String>> roles = new LinkedHashMap<>();
        for (Role role : roleRepository.findAll(Sort.by("name"))) {
            roles.put(role.getName(), permissionRepository.findCodesByRoleName(role.getName()));
        }
        return new PermissionMatrixResponse(permissions, roles);
    }

    @Transactional
    public List<String> updateRoleGrants(String roleName, List<String> permissionCodes) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown role: " + roleName));

        Set<String> requested = new LinkedHashSet<>(permissionCodes);
        List<Permission> permissions = permissionRepository.findByCodeIn(requested);
        if (permissions.size() != requested.size()) {
            Set<String> known = new HashSet<>();
            permissions.forEach(p -> known.add(p.getCode()));
            List<String> unknown = requested.stream().filter(c -> !known.contains(c)).toList();
            throw new InvalidRequestException("Unknown permission codes: " + unknown);
        }
        if ("ADMIN".equals(roleName) && !requested.containsAll(ADMIN_LOCKOUT_GUARD)) {
            throw new InvalidRequestException(
                    "The ADMIN role must keep " + ADMIN_LOCKOUT_GUARD
                            + " to prevent administrative lockout");
        }

        List<String> before = permissionRepository.findCodesByRoleName(roleName);
        permissionRepository.deleteGrantsForRole(role.getId());
        for (Permission permission : permissions) {
            permissionRepository.insertGrant(role.getId(), permission.getId());
        }
        List<String> after = permissionRepository.findCodesByRoleName(roleName);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "RolePermissions", role.getId(),
                AuditEvent.AuditAction.UPDATE,
                Map.of("role", roleName, "permissionCodes", before),
                Map.of("role", roleName, "permissionCodes", after)));

        invalidateAfterCommit(roleName);
        return after;
    }

    private void invalidateAfterCommit(String roleName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cacheByRole.remove(roleName);
                        }
                    });
        } else {
            cacheByRole.remove(roleName);
        }
    }
}
