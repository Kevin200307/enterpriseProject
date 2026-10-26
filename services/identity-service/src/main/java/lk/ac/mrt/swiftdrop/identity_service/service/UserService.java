package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;

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
}
