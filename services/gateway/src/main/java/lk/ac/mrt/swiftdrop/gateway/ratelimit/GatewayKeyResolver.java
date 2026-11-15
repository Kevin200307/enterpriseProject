package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import java.net.InetSocketAddress;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Resolves trusted per-user keys, falling back to the direct client address. */
@Component
public final class GatewayKeyResolver implements KeyResolver {

	@Override
	public Mono<String> resolve(ServerWebExchange exchange) {
		return exchange.getPrincipal()
				.ofType(JwtAuthenticationToken.class)
				.map(authentication -> "user:" + authentication.getToken().getSubject())
				.switchIfEmpty(Mono.fromSupplier(() -> anonymousKey(exchange)));
	}

	private String anonymousKey(ServerWebExchange exchange) {
		InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
		if (remoteAddress == null || remoteAddress.getAddress() == null) {
			return "ip:unknown";
		}
		return "ip:" + remoteAddress.getAddress().getHostAddress();
	}
}
