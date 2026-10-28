package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.DuplicateEmailException;
import lk.ac.mrt.swiftdrop.identity_service.error.ResourceNotFoundException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Production implementation of UserService.
 * Handles customer registration, password encryption, and profile updates.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: duplicate email detected");
            throw new DuplicateEmailException();
        }

        RoleEntity customerRole = roleRepository.findByRole(Role.CUSTOMER)
                .orElseGet(() -> {
                    log.warn("CUSTOMER role not found in database; initializing default entity");
                    return roleRepository.save(new RoleEntity(Role.CUSTOMER, "Standard consumer role"));
                });

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                normalizedEmail,
                passwordHash,
                request.fullName().trim(),
                request.phone().trim()
        );
        user.addRole(customerRole);

        User savedUser = userRepository.save(user);
        log.info("Successfully registered new user [userId={}, role={}]", savedUser.getId(), Role.CUSTOMER);

        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserResponse.fromEntity(user);
    }

    @Override
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }

        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated profile for user [userId={}]", userId);
        return UserResponse.fromEntity(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String role, UserStatus status, Pageable pageable) {
        Page<User> users;
        String normalizedRole = (role != null && !role.isBlank()) ? role.trim().toUpperCase(Locale.ROOT) : null;

        if (normalizedRole != null && status != null) {
            users = userRepository.findByRoles_IdAndStatus(normalizedRole, status, pageable);
        } else if (normalizedRole != null) {
            users = userRepository.findByRoles_Id(normalizedRole, pageable);
        } else if (status != null) {
            users = userRepository.findByStatus(status, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(UserResponse::fromEntity);
    }
}
