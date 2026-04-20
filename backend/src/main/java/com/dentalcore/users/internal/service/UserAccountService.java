package com.dentalcore.users.internal.service;

import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.users.api.UserAccount;
import com.dentalcore.users.api.UserApi;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.mapper.UserMapper;
import com.dentalcore.users.internal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserAccountService implements UserApi {

    private final UserRepository userRepository;
    private final UserMapper mapper;

    public UserAccountService(UserRepository userRepository, UserMapper mapper) {
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(mapper::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID id) {
        return userRepository.findById(id).map(mapper::toAccount);
    }

    /**
     * REQUIRES_NEW: the caller (auth) throws after a failed login, rolling back its
     * transaction — the security counter must survive that rollback.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementFailedAttempts(UUID userId) {
        User user = findUser(userId);
        user.recordFailedAttempt();
        return user.getFailedAttempts();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lock(UUID userId, Instant until) {
        findUser(userId).lockUntil(until);
    }

    @Override
    public void resetLoginState(UUID userId) {
        findUser(userId).resetLoginState();
    }

    @Override
    public void updatePassword(UUID userId, String newPasswordHash) {
        User user = findUser(userId);
        user.changePassword(newPasswordHash);
        user.resetLoginState();
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
