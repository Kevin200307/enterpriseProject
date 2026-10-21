package lk.ac.mrt.swiftdrop.gateway.config;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.gateway.error.GatewayProblemWriter;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

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
			ServerAccessDeniedHandler accessDeniedHandler) {

		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/api/v1/auth/**").permitAll()
						.pathMatchers(HttpMethod.GET, "/api/v1/tracking/*").permitAll()
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

	private static String requestId(ServerWebExchange exchange) {
		return exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
