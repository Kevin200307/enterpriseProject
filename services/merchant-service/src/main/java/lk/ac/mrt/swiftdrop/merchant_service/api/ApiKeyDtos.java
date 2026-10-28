package lk.ac.mrt.swiftdrop.merchant_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class ApiKeyDtos {
    private ApiKeyDtos() {}

    public record CreateRequest(@NotBlank String label, @NotEmpty Set<String> scopes, Instant expiresAt) {}
    public record Created(UUID id, String key, String prefix, String label, Set<String> scopes,
                          Instant expiresAt, Instant createdAt) {}
    public record View(UUID id, String prefix, String label, Set<String> scopes, Instant lastUsedAt,
                       Instant revokedAt, Instant expiresAt, Instant createdAt) {}
}
