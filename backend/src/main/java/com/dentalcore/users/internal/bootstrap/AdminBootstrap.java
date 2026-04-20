package com.dentalcore.users.internal.bootstrap;

import com.dentalcore.users.internal.entity.Role;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Creates the first ADMIN user on an empty database. Credentials come from the
 * environment so no password hash is ever committed to a migration. Runs only
 * when the users table is empty — it never touches an initialized system.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${ADMIN_EMAIL:admin@dentalcore.local}") String adminEmail,
                          @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No users exist and ADMIN_PASSWORD is not set — skipping admin bootstrap. "
                    + "Set ADMIN_EMAIL/ADMIN_PASSWORD and restart to create the first admin.");
            return;
        }
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing — run migrations"));
        User admin = new User(adminEmail, passwordEncoder.encode(adminPassword),
                "System", "Administrator", DEFAULT_CLINIC_ID);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);
        log.info("Bootstrapped initial admin user {}", adminEmail);
    }
}
