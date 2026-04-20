package com.dentalcore.users.internal.service;

import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.users.internal.dto.CreateUserRequest;
import com.dentalcore.users.internal.dto.UpdateUserRequest;
import com.dentalcore.users.internal.dto.UserResponse;
import com.dentalcore.users.internal.entity.Role;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.entity.UserStatus;
import com.dentalcore.users.internal.mapper.UserMapper;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper mapper,
                       ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Pageable pageable) {
        Page<User> page = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(search.trim(), pageable);
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return mapper.toResponse(findUser(id));
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with this email already exists");
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.clinicId());
        user.setRoles(resolveRoles(request.roles()));
        user = userRepository.save(user);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "User", user.getId(),
                AuditEvent.AuditAction.CREATE,
                null,
                Map.of("email", user.getEmail(), "roles", user.roleNames())));
        return mapper.toResponse(user);
    }

    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findUser(id);
        Map<String, Object> before = Map.of(
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "roles", user.roleNames(),
                "status", user.getStatus().name());

        if (user.getStatus() != UserStatus.valueOf(request.status())
                && CurrentUser.id().map(id::equals).orElse(false)) {
            throw new InvalidRequestException("You cannot change your own account status");
        }

        user.updateProfile(request.firstName(), request.lastName());
        user.setRoles(resolveRoles(request.roles()));
        user.setStatus(UserStatus.valueOf(request.status()));

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "User", user.getId(),
                AuditEvent.AuditAction.UPDATE,
                before,
                Map.of("firstName", user.getFirstName(), "lastName", user.getLastName(),
                        "roles", user.roleNames(), "status", user.getStatus().name())));
        return mapper.toResponse(user);
    }

    public void resetPassword(UUID id, String newPassword) {
        User user = findUser(id);
        user.changePassword(passwordEncoder.encode(newPassword));
        user.resetLoginState();
        events.publishEvent(AuditEvent.of(
                CurrentUser.id().orElse(null), "User", user.getId(),
                AuditEvent.AuditAction.PASSWORD_RESET_COMPLETED));
    }

    private Set<Role> resolveRoles(Set<String> names) {
        Set<Role> roles = roleRepository.findByNameIn(names);
        if (roles.size() != names.size()) {
            throw new InvalidRequestException("One or more roles are unknown");
        }
        return roles;
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
