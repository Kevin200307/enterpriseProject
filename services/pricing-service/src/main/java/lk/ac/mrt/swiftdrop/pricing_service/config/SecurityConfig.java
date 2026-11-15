package lk.ac.mrt.swiftdrop.pricing_service.config;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final Set<String> ROLES = Set.of("ADMIN", "DISPATCHER", "COURIER", "CUSTOMER", "MERCHANT");

    static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
            new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(audience)),
            new JwtClaimValidator<String>("sub", value -> {
                try { return value != null && UUID.fromString(value).toString().equals(value); }
                catch (IllegalArgumentException ex) { return false; }
            }),
            new JwtClaimValidator<Object>("roles", value -> value instanceof List<?> roles
                && !roles.isEmpty() && roles.stream().allMatch(ROLES::contains)));
    }

    @Bean JwtDecoder jwtDecoder(@Value("${pricing.security.jwks}") String jwks,
            @Value("${pricing.security.issuer}") String issuer,
            @Value("${pricing.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(validators(issuer, audience));
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, JsonMapper mapper) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        var entry = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, ex) -> {
            response.setStatus(401);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(PricingExceptionHandler.problem(
                lk.ac.mrt.swiftdrop.contracts.error.ErrorCode.UNAUTHENTICATED, "A valid bearer token is required")));
        };
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(PricingExceptionHandler.problem(
                lk.ac.mrt.swiftdrop.contracts.error.ErrorCode.ACCESS_DENIED, "This operation is not permitted")));
        };
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs", "/v3/api-docs/**",
                    "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .build();
    }
}