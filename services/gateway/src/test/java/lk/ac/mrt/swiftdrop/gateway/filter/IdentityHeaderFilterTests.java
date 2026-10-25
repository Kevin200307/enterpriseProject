package lk.ac.mrt.swiftdrop.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

class IdentityHeaderFilterTests {

	private static final String USER_ID = "3f01b670-7642-4b41-a4ec-12dd605c70c4";
	private static final String MERCHANT_ID = "6d349248-76e0-4a7e-9887-b68adaf7e522";

	private final IdentityHeaderFilter filter = new IdentityHeaderFilter();

	@Test
	void replacesForgedHeadersFromValidatedJwtAndPreservesBearerToken() {
		ServerWebExchange exchange = authenticatedExchange(Map.of(
				"sub", USER_ID,
				"roles", List.of("MERCHANT", "CUSTOMER", "MERCHANT"),
				"merchant_id", MERCHANT_ID));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		HttpHeaders headers = forwarded.get().getRequest().getHeaders();
		assertThat(headers.getFirst(IdentityHeaderFilter.USER_ID_HEADER)).isEqualTo(USER_ID);
		assertThat(headers.getFirst(IdentityHeaderFilter.USER_ROLES_HEADER))
				.isEqualTo("CUSTOMER,MERCHANT");
		assertThat(headers.getFirst(IdentityHeaderFilter.MERCHANT_ID_HEADER)).isEqualTo(MERCHANT_ID);
		assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer signed.jwt.token");
	}

	@Test
	void removesAllForgedHeadersForAnonymousRequest() {
		MockServerWebExchange exchange = forgedExchange();
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		assertIdentityHeadersAreAbsent(forwarded.get().getRequest().getHeaders());
	}

	@Test
	void omitsMerchantHeaderWhenClaimIsAbsent() {
		ServerWebExchange exchange = authenticatedExchange(Map.of(
				"sub", USER_ID,
				"roles", List.of("CUSTOMER")));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		HttpHeaders headers = forwarded.get().getRequest().getHeaders();
		assertThat(headers.getFirst(IdentityHeaderFilter.USER_ID_HEADER)).isEqualTo(USER_ID);
		assertThat(headers.getFirst(IdentityHeaderFilter.USER_ROLES_HEADER)).isEqualTo("CUSTOMER");
		assertThat(headers.get(IdentityHeaderFilter.MERCHANT_ID_HEADER)).isNull();
	}

	@Test
	void invalidClaimsCannotBecomeTrustedHeaders() {
		ServerWebExchange exchange = authenticatedExchange(Map.of(
				"sub", "not-a-uuid",
				"roles", List.of("ADMIN", "FORGED_ROLE"),
				"merchant_id", "also-not-a-uuid"));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		HttpHeaders headers = forwarded.get().getRequest().getHeaders();
		assertThat(headers.get(IdentityHeaderFilter.USER_ID_HEADER)).isNull();
		assertThat(headers.getFirst(IdentityHeaderFilter.USER_ROLES_HEADER)).isEqualTo("ADMIN");
		assertThat(headers.get(IdentityHeaderFilter.MERCHANT_ID_HEADER)).isNull();
	}

	private ServerWebExchange authenticatedExchange(Map<String, Object> claims) {
		Jwt jwt = new Jwt(
				"signed.jwt.token",
				Instant.now(),
				Instant.now().plusSeconds(900),
				Map.of("alg", "RS256"),
				claims);
		return forgedExchange().mutate()
				.principal(Mono.just(new JwtAuthenticationToken(jwt)))
				.build();
	}

	private MockServerWebExchange forgedExchange() {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/parcels")
				.header(HttpHeaders.AUTHORIZATION, "Bearer signed.jwt.token")
				.header(IdentityHeaderFilter.USER_ID_HEADER, "forged-user")
				.header(IdentityHeaderFilter.USER_ROLES_HEADER, "ADMIN")
				.header(IdentityHeaderFilter.MERCHANT_ID_HEADER, "forged-merchant"));
	}

	private void assertIdentityHeadersAreAbsent(HttpHeaders headers) {
		assertThat(headers.get(IdentityHeaderFilter.USER_ID_HEADER)).isNull();
		assertThat(headers.get(IdentityHeaderFilter.USER_ROLES_HEADER)).isNull();
		assertThat(headers.get(IdentityHeaderFilter.MERCHANT_ID_HEADER)).isNull();
	}
}
