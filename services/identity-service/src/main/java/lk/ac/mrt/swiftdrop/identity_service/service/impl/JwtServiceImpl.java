package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Production implementation of JwtService.
 * Issues RS256 signed access tokens carrying standard claims specified in the SwiftDrop Auth contract:
 * - iss: http://identity-service:8080 (or configured issuer URI)
 * - sub: user UUID
 * - email: user email
 * - roles: array of string roles (e.g. ['CUSTOMER', 'MERCHANT'])
 * - merchantId: UUID string (if present)
 * - exp: issuedAt + 15 minutes
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RsaKeyProvider rsaKeyProvider;
    private final JwtProperties jwtProperties;

    public JwtServiceImpl(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            RsaKeyProvider rsaKeyProvider,
            JwtProperties jwtProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.rsaKeyProvider = rsaKeyProvider;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public String generateAccessToken(User user) {
        return generateAccessToken(user, null);
    }

    @Override
    public String generateAccessToken(User user, UUID merchantId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID must not be null when generating access token");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.accessTokenTtlSeconds());

        List<String> roles = user.getRoles().stream()
                .map(RoleEntity::getId)
                .sorted()
                .toList();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(expiresAt);

        if (merchantId != null) {
            claimsBuilder.claim("merchantId", merchantId.toString());
        }

        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKeyProvider.getKeyId())
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        log.debug("Issued RS256 access token for user [userId={}, exp={}]", user.getId(), expiresAt);
        return jwt.getTokenValue();
    }

    @Override
    public Jwt decodeToken(String token) {
        return jwtDecoder.decode(token);
    }

    @Override
    public Duration getAccessTokenTtl() {
        return Duration.ofSeconds(jwtProperties.accessTokenTtlSeconds());
    }
}
