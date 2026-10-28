package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service managing user registration and user account lifecycle.
 */
public interface UserService {

    /**
     * Registers a new customer user account.
     * Enforces unique email check, BCrypt password hashing, and default CUSTOMER role assignment.
     *
     * @param request the validated registration request
     * @return the created user profile
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.DuplicateEmailException if email is already taken
     */
    UserResponse register(RegisterRequest request);

    /**
     * Retrieves the profile of an authenticated user by ID.
     *
     * @param userId the user's UUID
     * @return the user profile
     */
    UserResponse getUserProfile(UUID userId);

    /**
     * Updates profile details (full name and phone) for the user.
     *
     * @param userId  the user's UUID
     * @param request the profile update request
     * @return the updated user profile
     */
    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    /**
     * Retrieves a paginated list of users with optional role and status filters (ADMIN only).
     *
     * @param role optional role name filter (e.g. "ADMIN", "CUSTOMER")
     * @param status optional user status filter (e.g. ACTIVE, SUSPENDED)
     * @param pageable pagination parameters (page, size, sort)
     * @return paginated user response
     */
    Page<UserResponse> listUsers(String role, UserStatus status, Pageable pageable);

    /**
     * Updates an existing user's status (ACTIVE or SUSPENDED) (ADMIN only).
     * If the status is updated to SUSPENDED, all active refresh tokens for the user are immediately revoked.
     * Prevents admin self-suspension to avoid administrative lockout.
     *
     * @param userId the ID of the user whose status is being modified
     * @param newStatus the new UserStatus (ACTIVE, SUSPENDED)
     * @param currentAdminId the ID of the authenticated administrator performing the update (may be null)
     * @return updated user profile response
     */
    UserResponse updateUserStatus(UUID userId, UserStatus newStatus, UUID currentAdminId);
}
