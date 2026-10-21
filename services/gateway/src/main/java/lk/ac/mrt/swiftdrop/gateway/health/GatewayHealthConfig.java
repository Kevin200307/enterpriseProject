package lk.ac.mrt.swiftdrop.gateway.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lk.ac.mrt.swiftdrop.gateway.config.GatewayProperties;

import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class GatewayHealthConfig {

	@Bean
	ReactiveHealthContributor downstreamHealthContributor(GatewayProperties properties) {

		GatewayProperties.Services services = properties.services();
		WebClient.Builder webClientBuilder = WebClient.builder();
		Map<String, ReactiveHealthIndicator> indicators = new LinkedHashMap<>();
		indicators.put("identity", indicator(webClientBuilder, services.identity()));
		indicators.put("parcel", indicator(webClientBuilder, services.parcel()));
		indicators.put("pricing", indicator(webClientBuilder, services.pricing()));
		indicators.put("dispatch", indicator(webClientBuilder, services.dispatch()));
		indicators.put("merchant", indicator(webClientBuilder, services.merchant()));
		indicators.put("tracking", indicator(webClientBuilder, services.tracking()));
		indicators.put("notification", indicator(webClientBuilder, services.notification()));
		indicators.put("aiAssistant", indicator(webClientBuilder, services.aiAssistant()));
		return CompositeReactiveHealthContributor.fromMap(indicators);
	}

	@Bean
	ReactiveHealthIndicator redisHealthIndicator(ReactiveRedisConnectionFactory connectionFactory) {
		return new RateLimitRedisHealthIndicator(connectionFactory);
	}

	private ReactiveHealthIndicator indicator(WebClient.Builder builder, java.net.URI uri) {
		return new DownstreamHealthIndicator(builder.clone(), uri);
	}
}
