package lk.ac.mrt.swiftdrop.gateway.config;

import java.time.Duration;
import java.util.List;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.gateway.error.GatewayProblemWriter;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	ReactiveJwtDecoder gatewayJwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {
		var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
		var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
		var audienceValidator = new JwtClaimValidator<java.util.List<String>>("aud",
				values -> values != null && values.contains(audience));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
		return decoder;
	}

	@Bean
	ServerAuthenticationEntryPoint gatewayAuthenticationEntryPoint(GatewayProblemWriter problemWriter) {
		return (exchange, exception) -> problemWriter.write(
				exchange,
				ErrorCode.UNAUTHENTICATED,
				"A valid bearer token is required to access this resource",
				requestId(exchange));
	}

	@Bean
	ServerAccessDeniedHandler gatewayAccessDeniedHandler(GatewayProblemWriter problemWriter) {
		return (exchange, exception) -> problemWriter.write(
				exchange,
				ErrorCode.ACCESS_DENIED,
				"You do not have permission to access this resource",
				requestId(exchange));
	}

	@Bean
	SecurityWebFilterChain gatewaySecurityWebFilterChain(
			ServerHttpSecurity http,
			ServerAuthenticationEntryPoint authenticationEntryPoint,
			ServerAccessDeniedHandler accessDeniedHandler,
			CorsConfigurationSource corsConfigurationSource) {

		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.headers(headers -> headers
						.contentTypeOptions(Customizer.withDefaults())
						.frameOptions(frame -> frame.mode(Mode.DENY))
						.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
						.hsts(hsts -> hsts
								.includeSubdomains(true)
								.maxAge(Duration.ofDays(365)))
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; script-src 'self' 'unsafe-inline'; "
										+ "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
										+ "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; "
										+ "base-uri 'self'; form-action 'self'")))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/api/v1/auth/**").permitAll()
						.pathMatchers(HttpMethod.GET, "/api/v1/tracking/*").permitAll()
						.pathMatchers(HttpMethod.GET, "/api/v1/tracking/public/*/stream").permitAll()
						.pathMatchers("/ws", "/ws/**").permitAll()
						.pathMatchers(
								"/docs", "/docs/**",
								"/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
								"/v3/api-docs", "/v3/api-docs/**",
								"/actuator/health")
								.permitAll()
						.anyExchange().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

	@Bean
	CorsConfigurationSource gatewayCorsConfigurationSource(GatewayProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(properties.browser().origin().toString()));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
				HttpHeaders.AUTHORIZATION,
				HttpHeaders.CONTENT_TYPE,
				HttpHeaders.ACCEPT,
				RequestIdFilter.REQUEST_ID_HEADER,
				HttpHeaders.IF_MATCH));
		configuration.setExposedHeaders(List.of(
				RequestIdFilter.REQUEST_ID_HEADER,
				HttpHeaders.LOCATION,
				HttpHeaders.RETRY_AFTER));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private static String requestId(ServerWebExchange exchange) {
		return exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
