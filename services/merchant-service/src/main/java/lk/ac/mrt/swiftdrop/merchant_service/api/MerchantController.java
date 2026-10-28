package lk.ac.mrt.swiftdrop.merchant_service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;
import lk.ac.mrt.swiftdrop.merchant_service.service.MerchantService;
import lk.ac.mrt.swiftdrop.merchant_service.service.exception.IdentityServiceException;
import lk.ac.mrt.swiftdrop.merchant_service.service.exception.MerchantAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
@Slf4j
@Tag(name = "Merchant Management", description = "Merchant registration and approval APIs")
public class MerchantController {

    private final MerchantService merchantService;
    private final lk.ac.mrt.swiftdrop.merchant_service.service.ApiKeyService apiKeys;
    private final lk.ac.mrt.swiftdrop.merchant_service.service.MerchantOrderService orders;

    public MerchantController(MerchantService merchantService, lk.ac.mrt.swiftdrop.merchant_service.service.ApiKeyService apiKeys,
                              lk.ac.mrt.swiftdrop.merchant_service.service.MerchantOrderService orders) {
        this.merchantService = merchantService;
        this.apiKeys = apiKeys;
        this.orders = orders;
    }

    @PostMapping("/register")
    @Operation(summary = "Register as a merchant", description = "Authenticated users can register as a merchant")
    public MerchantResponse registerMerchant(
            @Valid @RequestBody MerchantRegistrationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID ownerUserId = UUID.fromString(jwt.getSubject());
        return merchantService.registerMerchant(ownerUserId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all merchants", description = "Admin can list all merchants with optional status filter")
    public Page<MerchantResponse> listMerchants(
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) MerchantStatus status,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        return merchantService.listMerchants(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get merchant by ID", description = "Admin can get merchant details by ID")
    public MerchantResponse getMerchant(@PathVariable UUID id) {
        return merchantService.getMerchant(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a merchant", description = "Admin can approve a pending merchant")
    public MerchantResponse approveMerchant(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID approvedBy = UUID.fromString(jwt.getSubject());
        return merchantService.approveMerchant(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a merchant", description = "Admin can reject a pending merchant")
    public MerchantResponse rejectMerchant(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID rejectedBy = UUID.fromString(jwt.getSubject());
        return merchantService.rejectMerchant(id, rejectedBy);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend a merchant", description = "Admin can suspend an approved merchant")
    public MerchantResponse suspendMerchant(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID suspendedBy = UUID.fromString(jwt.getSubject());
        return merchantService.suspendMerchant(id, suspendedBy);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's merchant profile", description = "Get the merchant profile of the authenticated user")
    public MerchantResponse getMyMerchant(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerUserId = UUID.fromString(jwt.getSubject());
        return merchantService.getMerchantByOwnerUserId(ownerUserId);
    }

    @PostMapping("/me/api-keys")
    public ApiKeyDtos.Created createApiKey(@Valid @RequestBody ApiKeyDtos.CreateRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return apiKeys.create(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/me/api-keys")
    public java.util.List<ApiKeyDtos.View> listApiKeys(@AuthenticationPrincipal Jwt jwt) {
        return apiKeys.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/me/api-keys/{keyId}")
    public ApiKeyDtos.View revokeApiKey(@PathVariable UUID keyId, @AuthenticationPrincipal Jwt jwt) {
        return apiKeys.revoke(UUID.fromString(jwt.getSubject()), keyId);
    }

    @PostMapping("/orders")
    public MerchantOrderDtos.View createOrder(@Valid @RequestBody MerchantOrderDtos.Create request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return orders.create(UUID.fromString(jwt.getSubject()), request, jwt.getTokenValue());
    }

    @GetMapping("/orders")
    public Page<MerchantOrderDtos.View> listOrders(@RequestParam(required=false) String q,
                                                    @RequestParam(defaultValue="0") int page,
                                                    @RequestParam(defaultValue="20") int size,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return orders.list(UUID.fromString(jwt.getSubject()), q,
                PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/orders/{orderId}")
    public MerchantOrderDtos.View getOrder(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return orders.get(UUID.fromString(jwt.getSubject()), orderId);
    }

    @ExceptionHandler(MerchantAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleMerchantAlreadyExists(MerchantAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setType(ErrorCode.CONFLICT.type());
        problem.setTitle(ErrorCode.CONFLICT.title());
        problem.setProperty("errorCode", ErrorCode.CONFLICT.name());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setType(ErrorCode.RESOURCE_NOT_FOUND.type());
        problem.setTitle(ErrorCode.RESOURCE_NOT_FOUND.title());
        problem.setProperty("errorCode", ErrorCode.RESOURCE_NOT_FOUND.name());
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setType(ErrorCode.INVALID_STATUS_TRANSITION.type());
        problem.setTitle(ErrorCode.INVALID_STATUS_TRANSITION.title());
        problem.setProperty("errorCode", ErrorCode.INVALID_STATUS_TRANSITION.name());
        return problem;
    }

    @ExceptionHandler(IdentityServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleIdentityServiceError(IdentityServiceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to update identity service"
        );
        problem.setType(ErrorCode.INTERNAL_ERROR.type());
        problem.setTitle(ErrorCode.INTERNAL_ERROR.title());
        problem.setProperty("errorCode", ErrorCode.INTERNAL_ERROR.name());
        return problem;
    }
}
