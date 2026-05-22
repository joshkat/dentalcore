package com.dentalcore.infrastructure.security;

import com.dentalcore.shared.security.AuthenticatedUser;
import com.dentalcore.users.api.PermissionsApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PermissionsApi permissionsApi;

    public JwtAuthenticationFilter(JwtService jwtService, PermissionsApi permissionsApi) {
        this.jwtService = jwtService;
        this.permissionsApi = permissionsApi;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(BEARER_PREFIX.length())).ifPresent(user -> {
                var authentication = authenticationFor(user, request);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authenticationFor(AuthenticatedUser user,
                                                                  HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user, null, expandedAuthorities(user));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }

    /**
     * ROLE_* authorities from the JWT plus the union of granular permission
     * codes for the user's roles. Permissions are resolved per request (via a
     * cached lookup), so matrix edits take effect without re-login.
     */
    private Set<GrantedAuthority> expandedAuthorities(AuthenticatedUser user) {
        Set<GrantedAuthority> authorities = new HashSet<>(user.authorities());
        permissionsApi.permissionCodesFor(user.roles())
                .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }
}
