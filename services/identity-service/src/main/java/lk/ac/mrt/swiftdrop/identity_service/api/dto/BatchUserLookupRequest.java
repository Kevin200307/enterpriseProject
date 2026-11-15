package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for batch user lookup for inter-service communication.
 * Supports both 'userIds' and 'ids' property aliases.
 */
@Schema(description = "Request payload for batch user lookup across internal services")
public record BatchUserLookupRequest(
        @JsonProperty("userIds")
        @JsonAlias("ids")
        @NotEmpty(message = "userIds must not be empty")
        @Schema(description = "List of user UUIDs to lookup", example = "[\"b1c0e3a6-2d12-4217-91a9-df56e9c9b101\"]")
        List<UUID> userIds
) {
}
