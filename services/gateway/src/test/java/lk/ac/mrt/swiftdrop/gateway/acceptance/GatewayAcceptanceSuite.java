package lk.ac.mrt.swiftdrop.gateway.acceptance;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Executable acceptance contract for behavior exposed at the public edge.
 *
 * <p>The selected tests retain their purpose-built fixtures: HTTP stubs for
 * routing and degradation, upgraded connections for WebSocket, incremental
 * publishers for SSE, and Redis Testcontainers for distributed limits.</p>
 */
@Suite
@SuiteDisplayName("Gateway edge acceptance")
@SelectPackages("lk.ac.mrt.swiftdrop.gateway")
@IncludeClassNamePatterns({
		".*GatewayRoutesTests",
		".*SecurityConfigTests",
		".*IdentityHeaderFilterTests",
		".*RequestIdFilterTests",
		".*GatewayErrorHandlerTests",
		".*GatewayCircuitBreakerIntegrationTests",
		".*GatewayRateLimitIntegrationTests",
		".*GatewayWebSocketIntegrationTests",
		".*GatewaySseIntegrationTests",
		".*GatewayOpenApiIntegrationTests",
		".*DashboardIntegrationTests"
})
public class GatewayAcceptanceSuite {
}
