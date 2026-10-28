package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.config.AdminBootstrapProperties;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Idempotent application runner that provisions or verifies the initial platform administrator user
 * Ensures that the bootstrap admin user exists, is in ACTIVE status, possesses the ADMIN role,
 * and is flagged with must_change_password = true on first login.
 */
@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AdminBootstrapProperties properties
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            log.info("Admin bootstrap is disabled via configuration.");
            return;
        }

        String email = properties.bootstrapEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Admin bootstrap is enabled but ADMIN_BOOTSTRAP_EMAIL is missing");
        }
        if (properties.bootstrapPassword() == null || properties.bootstrapPassword().isBlank()) {
            throw new IllegalStateException("Admin bootstrap is enabled but ADMIN_BOOTSTRAP_PASSWORD is missing");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            boolean updated = false;

            if (!existingUser.hasRole(Role.ADMIN)) {
                RoleEntity adminRole = getOrCreateAdminRole();
                existingUser.addRole(adminRole);
                updated = true;
                log.info("Granted ADMIN role to existing bootstrap user: {}", normalizedEmail);
            }

            if (existingUser.getStatus() != UserStatus.ACTIVE) {
                existingUser.setStatus(UserStatus.ACTIVE);
                updated = true;
                log.info("Reactivated existing bootstrap admin user: {}", normalizedEmail);
            }

            if (updated) {
                userRepository.save(existingUser);
            } else {
                log.info("Initial admin user '{}' already exists and holds verified ADMIN role.", normalizedEmail);
            }
            return;
        }

        RoleEntity adminRole = getOrCreateAdminRole();
        User newAdmin = new User(
                normalizedEmail,
                passwordEncoder.encode(properties.bootstrapPassword()),
                properties.bootstrapName(),
                properties.bootstrapPhone()
        );
        newAdmin.setStatus(UserStatus.ACTIVE);
        newAdmin.setMustChangePassword(true);
        newAdmin.addRole(adminRole);

        userRepository.save(newAdmin);
        log.info("Successfully provisioned initial admin user '{}' with must_change_password=true.", normalizedEmail);
    }

    private RoleEntity getOrCreateAdminRole() {
        return roleRepository.findByRole(Role.ADMIN)
                .orElseGet(() -> roleRepository.save(new RoleEntity(Role.ADMIN, "System Administrator")));
    }
}
