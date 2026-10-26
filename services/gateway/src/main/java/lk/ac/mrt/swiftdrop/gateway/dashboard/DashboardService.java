package lk.ac.mrt.swiftdrop.gateway.dashboard;

import java.net.URI;
import java.time.Duration;

import lk.ac.mrt.swiftdrop.gateway.config.GatewayProperties;
import lk.ac.mrt.swiftdrop.gateway.dashboard.DashboardResponse.Section;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public final class DashboardService {

	private final WebClient client;
	private final GatewayProperties.Services services;
	private final Duration timeout;

	public DashboardService(
			GatewayProperties properties,
			@Value("${gateway.dashboard.timeout:2s}") Duration timeout) {
		this.client = WebClient.builder().build();
		this.services = properties.services();
		this.timeout = timeout;
	}

	public Mono<DashboardResponse> getDashboard(String bearerToken, String requestId) {
		Mono<Section> parcels = fetch(
				services.parcel(), "/api/v1/parcels/summary", bearerToken, requestId);
		Mono<Section> dispatch = fetch(
				services.dispatch(), "/api/v1/dispatch/summary", bearerToken, requestId);
		Mono<Section> merchants = fetch(
				services.merchant(), "/api/v1/merchants/summary", bearerToken, requestId);

		return Mono.zip(parcels, dispatch, merchants)
				.map(sections -> new DashboardResponse(sections.getT1(), sections.getT2(), sections.getT3()));
	}

	private Mono<Section> fetch(
			URI baseUri,
			String path,
			String bearerToken,
			String requestId) {

		return client.get()
				.uri(baseUri.resolve(path))
				.headers(headers -> {
					headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
					headers.set(RequestIdFilter.REQUEST_ID_HEADER, requestId);
				})
				.retrieve()
				.bodyToMono(JsonNode.class)
				.timeout(timeout)
				.map(Section::available)
				.onErrorReturn(Section.unavailable());
	}
}
