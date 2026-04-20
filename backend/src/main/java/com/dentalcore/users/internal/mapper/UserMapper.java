package com.dentalcore.users.internal.mapper;

import com.dentalcore.users.api.UserAccount;
import com.dentalcore.users.internal.dto.UserResponse;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.entity.UserStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.roleNames(),
                user.getClinicId(),
                user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus() == UserStatus.ACTIVE,
                user.roleNames(),
                user.getFailedAttempts(),
                user.getLockedUntil()
        );
    }
}
