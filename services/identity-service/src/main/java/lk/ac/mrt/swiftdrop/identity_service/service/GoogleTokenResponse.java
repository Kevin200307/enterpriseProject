package lk.ac.mrt.swiftdrop.identity_service.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token response returned by Google's OAuth 2.0 token endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("refresh_token") String refreshToken
) {
}
