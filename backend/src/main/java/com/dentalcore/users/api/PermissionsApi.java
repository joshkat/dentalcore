package com.dentalcore.users.api;

import java.util.Set;

/**
 * Public interface of the users module for resolving granular permissions.
 * Consumed by the security infrastructure to expand a user's roles into
 * permission-code authorities on every request, so matrix edits take effect
 * without re-login.
 */
public interface PermissionsApi {

    /** Union of permission codes granted to any of the given roles. */
    Set<String> permissionCodesFor(Set<String> roleNames);
}
