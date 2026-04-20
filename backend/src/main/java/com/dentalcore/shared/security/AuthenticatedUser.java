package com.dentalcore.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Principal placed in the SecurityContext after JWT validation.
 * Modules read the current user from here — never from module internals.
 */
public record AuthenticatedUser(UUID id, String email, Set<String> roles) {

    public Set<GrantedAuthority> authorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toSet());
    }
}
