package com.teamtrace.backend.bootstrap;

import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.EmailValidation;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;
    private final String adminPhone;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public DefaultAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SnowflakeIdGenerator idGenerator,
            @Value("${teamtrace.admin.phone:}") String adminPhone,
            @Value("${teamtrace.admin.email:}") String adminEmail,
            @Value("${teamtrace.admin.password:}") String adminPassword,
            @Value("${teamtrace.admin.name:admin}") String adminName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
        this.adminPhone = adminPhone == null ? "" : adminPhone.trim();
        this.adminEmail = adminEmail == null ? "" : EmailValidation.normalize(adminEmail);
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.adminName = adminName == null ? "admin" : adminName.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank()) {
            log.info("default admin disabled: missing teamtrace.admin.password");
            return;
        }
        if (adminEmail.isBlank() && adminPhone.isBlank()) {
            log.info("default admin disabled: set ADMIN_EMAIL or ADMIN_PHONE");
            return;
        }

        Optional<User> existing = findExistingAdmin();
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() != User.Role.admin) {
                log.warn("default admin identifier already used by role={} userId={}", u.getRole(), u.getId());
                return;
            }
            backfillAdminEmailIfNeeded(u);
            log.info("default admin exists userId={}", u.getId());
            return;
        }

        User admin = new User();
        admin.setUserUuid(idGenerator.nextId());
        admin.setRole(User.Role.admin);
        if (!adminEmail.isBlank()) {
            admin.setEmail(adminEmail);
        }
        if (!adminPhone.isBlank()) {
            admin.setPhone(adminPhone);
        }
        admin.setName(adminName.isBlank() ? "admin" : adminName);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setStatus(1);
        admin.setIsDeleted(0);

        User saved = userRepository.save(admin);
        log.info("default admin created userId={}", saved.getId());
    }

    private void backfillAdminEmailIfNeeded(User admin) {
        if (adminEmail.isBlank()) {
            return;
        }
        String currentEmail = admin.getEmail();
        if (currentEmail != null && !currentEmail.isBlank()) {
            return;
        }
        if (userRepository.existsByEmailAndIsDeleted(adminEmail, 0)) {
            log.warn("default admin email backfill skipped: {} already in use", adminEmail);
            return;
        }
        admin.setEmail(adminEmail);
        userRepository.save(admin);
        log.info("default admin email backfilled userId={} email={}", admin.getId(), adminEmail);
    }

    private Optional<User> findExistingAdmin() {
        if (!adminEmail.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailAndIsDeleted(adminEmail, 0);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (!adminPhone.isBlank()) {
            return userRepository.findByPhoneAndIsDeleted(adminPhone, 0);
        }
        return Optional.empty();
    }
}
