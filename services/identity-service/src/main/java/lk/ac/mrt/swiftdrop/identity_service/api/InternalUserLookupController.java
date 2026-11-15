package lk.ac.mrt.swiftdrop.identity_service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.BatchUserLookupRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserLookupResponse;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal REST controller for inter-service user lookups.
 * Restricted to internal service callers and administrators.
 */
@RestController
@RequestMapping(path = "/api/v1/internal/users/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Internal Users", description = "Internal endpoints for service-to-service user lookups")
@SecurityRequirement(name = "BearerAuth")
public class InternalUserLookupController {

    private final UserService userService;

    public InternalUserLookupController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("authentication instanceof T(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) and ((authentication.token.claims['client_id'] == 'dispatch-service' and authentication.token.claims['scope'] == 'dispatch.internal') or (authentication.token.claims['client_id'] == 'notification-service' and authentication.token.claims['scope'] == 'notification.internal') or (authentication.token.claims['client_id'] == 'merchant-service' and authentication.token.claims['scope'] == 'merchant.internal') or hasRole('ADMIN'))")
    @Operation(
            summary = "Batch lookup users by IDs",
            description = "Resolves user details (id, fullName, email, phone, status, roles) for a list of UUIDs. Restricted to internal services and administrators."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Batch user lookup results",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserLookupResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed for request body (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated - valid service token required (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - service-to-service credentials or administrator role required (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<List<UserLookupResponse>> batchLookupUsers(
            @Valid @RequestBody BatchUserLookupRequest request
    ) {
        List<UserLookupResponse> results = userService.lookupUsers(request.userIds());
        return ResponseEntity.ok(results);
    }

    @GetMapping
    @PreAuthorize("authentication instanceof T(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) and ((authentication.token.claims['client_id'] == 'dispatch-service' and authentication.token.claims['scope'] == 'dispatch.internal') or (authentication.token.claims['client_id'] == 'notification-service' and authentication.token.claims['scope'] == 'notification.internal') or (authentication.token.claims['client_id'] == 'merchant-service' and authentication.token.claims['scope'] == 'merchant.internal') or hasRole('ADMIN'))")
    @Operation(
            summary = "Lookup a single user by ID",
            description = "Resolves single user details (id, fullName, email, phone, status, roles). Restricted to internal services and administrators."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User details found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserLookupResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated - valid service token required (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - service-to-service credentials or administrator role required (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<UserLookupResponse> lookupSingleUser(
            @Parameter(description = "UUID of the user to lookup")
            @RequestParam("id") UUID userId
    ) {
        UserLookupResponse result = userService.lookupUser(userId);
        return ResponseEntity.ok(result);
    }
}
