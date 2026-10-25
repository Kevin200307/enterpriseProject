package lk.ac.mrt.swiftdrop.gateway.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Replaces untrusted client identity headers with values from the validated JWT.
 */
@Component
public final class IdentityHeaderFilter implements WebFilter, Ordered {

	public static final String USER_ID_HEADER = "X-User-Id";
	public static final String USER_ROLES_HEADER = "X-User-Roles";
	public static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

	private static final String ROLES_CLAIM = "roles";
	private static final String MERCHANT_ID_CLAIM = "merchantId";
	private static final Set<String> KNOWN_ROLES = Arrays.stream(Role.values())
			.map(Role::name)
			.collect(Collectors.toUnmodifiableSet());

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return exchange.getPrincipal()
				.ofType(JwtAuthenticationToken.class)
				.map(authentication -> withTrustedHeaders(exchange, authentication))
				.defaultIfEmpty(withoutIdentityHeaders(exchange))
				.flatMap(chain::filter);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 100;
	}

	private ServerWebExchange withTrustedHeaders(
			ServerWebExchange exchange,
			JwtAuthenticationToken authentication) {

		ServerHttpRequest request = exchange.getRequest().mutate()
				.headers(headers -> {
					removeIdentityHeaders(headers);
					putIfValidUuid(headers, USER_ID_HEADER, authentication.getToken().getSubject());
					putRoles(headers, authentication.getToken().getClaim(ROLES_CLAIM));
					putIfValidUuid(
							headers,
							MERCHANT_ID_HEADER,
							authentication.getToken().getClaimAsString(MERCHANT_ID_CLAIM));
				})
				.build();
		return exchange.mutate().request(request).build();
	}

	private ServerWebExchange withoutIdentityHeaders(ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest().mutate()
				.headers(this::removeIdentityHeaders)
				.build();
		return exchange.mutate().request(request).build();
	}

	private void removeIdentityHeaders(HttpHeaders headers) {
		headers.remove(USER_ID_HEADER);
		headers.remove(USER_ROLES_HEADER);
		headers.remove(MERCHANT_ID_HEADER);
	}

	private void putRoles(HttpHeaders headers, Object claim) {
		if (!(claim instanceof Collection<?> values)) {
			return;
		}
		String roles = values.stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.filter(KNOWN_ROLES::contains)
				.distinct()
				.sorted()
				.collect(Collectors.joining(","));
		if (!roles.isEmpty()) {
			headers.set(USER_ROLES_HEADER, roles);
		}
	}

	private void putIfValidUuid(HttpHeaders headers, String headerName, String value) {
		if (value == null) {
			return;
		}
		try {
			headers.set(headerName, UUID.fromString(value).toString());
		}
		catch (IllegalArgumentException ignored) {
			// An invalid identity claim must never become a trusted downstream header.
		}
	}
}
