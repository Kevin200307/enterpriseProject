package lk.ac.mrt.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.support.NameUtils;

@SpringBootTest
class GatewayRoutesTests {

	private static Map<String, RouteDefinition> routes;

	@BeforeAll
	static void loadRoutes(@Autowired RouteDefinitionLocator routeDefinitionLocator) {
		routes = routeDefinitionLocator.getRouteDefinitions()
				.collectMap(RouteDefinition::getId)
				.block();
	}

	@Test
	void declaresExactlyTheExpectedApiRoutes() {
		assertThat(routes).isNotNull();
		assertThat(routes.keySet()).containsExactlyInAnyOrder(
				"identity-api",
				"parcel-api",
				"pricing-api",
				"dispatch-api",
				"merchant-api",
				"tracking-api",
				"notification-api",
				"assistant-api");
	}

	@Test
	void sendsEachApiPrefixToItsOwningService() {
		assertRoute("identity-api", "http://identity-service:8080",
				"/api/v1/auth/**", "/api/v1/users/**");
		assertRoute("parcel-api", "http://parcel-service:8080", "/api/v1/parcels/**");
		assertRoute("pricing-api", "http://pricing-service:8080", "/api/v1/pricing/**");
		assertRoute("dispatch-api", "http://dispatch-service:8080",
				"/api/v1/dispatch/**", "/api/v1/couriers/**");
		assertRoute("merchant-api", "http://merchant-service:8080", "/api/v1/merchants/**");
		assertRoute("tracking-api", "http://tracking-service:8080", "/api/v1/tracking/**");
		assertRoute("notification-api", "http://notification-service:8080",
				"/api/v1/notifications/**");
		assertRoute("assistant-api", "http://ai-assistant-service:8080", "/api/v1/assistant/**");
	}

	@Test
	void doesNotProxyActuatorPaths() {
		Set<String> allConfiguredPaths = routes.values().stream()
				.map(this::pathPatterns)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());

		assertThat(allConfiguredPaths)
				.allMatch(path -> path.startsWith("/api/v1/"))
				.noneMatch(path -> path.startsWith("/actuator"));
	}

	private void assertRoute(String routeId, String expectedUri, String... expectedPaths) {
		RouteDefinition route = routes.get(routeId);
		assertThat(route).as("route %s", routeId).isNotNull();
		assertThat(route.getUri()).isEqualTo(URI.create(expectedUri));
		assertThat(pathPatterns(route)).containsExactlyInAnyOrder(expectedPaths);
	}

	private Set<String> pathPatterns(RouteDefinition route) {
		return route.getPredicates().stream()
				.filter(predicate -> "Path".equals(predicate.getName()))
				.flatMap(predicate -> predicate.getArgs().entrySet().stream())
				.filter(entry -> entry.getKey().startsWith(NameUtils.GENERATED_NAME_PREFIX))
				.map(Map.Entry::getValue)
				.flatMap(value -> Arrays.stream(value.split(",")))
				.collect(Collectors.toSet());
	}
}
