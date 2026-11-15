package lk.ac.mrt.swiftdrop.notification_service.config;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static OAuth2TokenValidator<Jwt> claims(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", v -> v != null && v.contains(audience)),
                new JwtClaimValidator<String>("sub", v -> {
                    try { return v != null && UUID.fromString(v).toString().equals(v); }
                    catch (IllegalArgumentException ex) { return false; }
                }),
                new JwtClaimValidator<Object>("roles", v ->
                        v instanceof List<?> values && !values.isEmpty()
                        && values.stream().allMatch(
                                Set.of("ADMIN", "DISPATCHER", "COURIER", "CUSTOMER", "MERCHANT")::contains)));
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${notification.security.jwks}") String jwks,
            @Value("${notification.security.issuer}") String issuer,
            @Value("${notification.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(claims(issuer, audience));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        var entry = (org.springframework.security.web.AuthenticationEntryPoint) (req, res, ex) -> {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write("""
                    {"status":401,"title":"Unauthorized","detail":"A valid bearer token is required"}""");
        };
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (req, res, ex) -> {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write("""
                    {"status":403,"title":"Forbidden","detail":"This operation is not permitted"}""");
        };

        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/api/v1/notifications/health")
                                .permitAll()
                        .requestMatchers("/api/v1/notifications/me",
                                "/api/v1/notifications/preferences",
                                "/api/v1/notifications/preferences/**")
                                .authenticated()
                        .requestMatchers("/api/v1/notifications",
                                "/api/v1/notifications/**")
                                .hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entry)
                        .accessDeniedHandler(denied))
                .build();
    }
}
