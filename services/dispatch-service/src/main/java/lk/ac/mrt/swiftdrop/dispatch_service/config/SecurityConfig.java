package lk.ac.mrt.swiftdrop.dispatch_service.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;
import lk.ac.mrt.swiftdrop.dispatch_service.api.ApiExceptionHandler;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    public static OAuth2TokenValidator<Jwt> claims(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud",v -> v != null && v.contains(audience)),
                new JwtClaimValidator<String>("sub",v -> {
                    try { return v != null && UUID.fromString(v).toString().equals(v); }
                    catch (IllegalArgumentException ex) { return false; }
                }),new JwtClaimValidator<Object>("roles",v -> v instanceof List<?> values && !values.isEmpty()
                    && values.stream().allMatch(Set.of("ADMIN","DISPATCHER","COURIER","CUSTOMER","MERCHANT")::contains)));
    }
    @Bean JwtDecoder jwtDecoder(@Value("${dispatch.security.jwks}") String jwks,
            @Value("${dispatch.security.issuer}") String issuer,
            @Value("${dispatch.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(claims(issuer,audience)); return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, JsonMapper mapper) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles);
        var entry = (org.springframework.security.web.AuthenticationEntryPoint) (req,res,ex) -> {
            res.setStatus(401); res.setHeader(HttpHeaders.WWW_AUTHENTICATE,"Bearer");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write(mapper.writeValueAsString(ApiExceptionHandler.problem(401,"A valid bearer token is required")));
        };
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (req,res,ex) -> {
            res.setStatus(403); res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write(mapper.writeValueAsString(ApiExceptionHandler.problem(403,"This operation is not permitted")));
        };
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health","/actuator/health/**",
                        "/v3/api-docs","/v3/api-docs/**","/swagger-ui.html","/swagger-ui/**").permitAll()
                    .requestMatchers("/api/v1/couriers/me/assignments","/api/v1/couriers/*/manifest").hasAnyRole("COURIER","DISPATCHER","ADMIN")
                    .requestMatchers("/api/v1/couriers","/api/v1/couriers/**","/api/v1/dispatch/**").hasAnyRole("DISPATCHER","ADMIN")
                    .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entry).accessDeniedHandler(denied)).build();
    }
}
