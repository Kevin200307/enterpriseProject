package lk.ac.mrt.swiftdrop.identity_service.service;

import java.time.Instant;
import java.util.List;
import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(role -> role.getId()).sorted().toList();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(user.getId().toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenTtlSeconds()))
                .claim("roles", roles).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresIn() { return properties.accessTokenTtlSeconds(); }
}
