package com.dentalcore.users.internal.web;

import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.security.AuthenticatedUser;
import com.dentalcore.shared.web.PageResponse;
import com.dentalcore.users.internal.dto.AdminResetPasswordRequest;
import com.dentalcore.users.internal.dto.CreateUserRequest;
import com.dentalcore.users.internal.dto.UpdateUserRequest;
import com.dentalcore.users.internal.dto.UserPreferencesRequest;
import com.dentalcore.users.internal.dto.UserPreferencesResponse;
import com.dentalcore.users.internal.dto.UserResponse;
import com.dentalcore.users.internal.service.UserPreferencesService;
import com.dentalcore.users.internal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration (ADMIN only, except /me)")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final UserPreferencesService preferencesService;

    public UserController(UserService userService, UserPreferencesService preferencesService) {
        this.userService = userService;
        this.preferencesService = preferencesService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "List users with optional search")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "lastName,asc") String sort) {
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, sortParts[0]));
        return PageResponse.from(userService.list(search, pageable));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("Current user not found");
        }
        return userService.get(principal.id());
    }

    @GetMapping("/me/preferences")
    @Operation(summary = "The current user's language preferences (null = inherit instance default)")
    public UserPreferencesResponse preferences() {
        return preferencesService.getForCurrentUser();
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Update the current user's language preferences (null resets to inherit)")
    public UserPreferencesResponse updatePreferences(
            @RequestBody UserPreferencesRequest request) {
        return preferencesService.updateForCurrentUser(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Get a user by id")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Update a user's profile, roles, and status")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Administratively set a new password for a user")
    public void resetPassword(@PathVariable UUID id,
                              @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
    }
}
