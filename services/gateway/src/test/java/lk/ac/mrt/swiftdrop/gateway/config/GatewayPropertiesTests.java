package lk.ac.mrt.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GatewayPropertiesTests {

	private static final String[] VALID_SERVICE_PROPERTIES = {
			"gateway.services.identity=http://identity:8080",
			"gateway.services.parcel=http://parcel:8080",
			"gateway.services.pricing=http://pricing:8080",
			"gateway.services.dispatch=http://dispatch:8080",
			"gateway.services.merchant=http://merchant:8080",
			"gateway.services.tracking=http://tracking:8080",
			"gateway.services.notification=http://notification:8080",
			"gateway.services.ai-assistant=http://ai-assistant:8080"
	};

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class);

	@Test
	void bindsEveryDownstreamServiceAddress() {
		contextRunner.withPropertyValues(VALID_SERVICE_PROPERTIES).run(context -> {
			assertThat(context).hasNotFailed();

			GatewayProperties.Services services = context.getBean(GatewayProperties.class).services();
			assertThat(services.identity()).isEqualTo(URI.create("http://identity:8080"));
			assertThat(services.parcel()).isEqualTo(URI.create("http://parcel:8080"));
			assertThat(services.pricing()).isEqualTo(URI.create("http://pricing:8080"));
			assertThat(services.dispatch()).isEqualTo(URI.create("http://dispatch:8080"));
			assertThat(services.merchant()).isEqualTo(URI.create("http://merchant:8080"));
			assertThat(services.tracking()).isEqualTo(URI.create("http://tracking:8080"));
			assertThat(services.notification()).isEqualTo(URI.create("http://notification:8080"));
			assertThat(services.aiAssistant()).isEqualTo(URI.create("http://ai-assistant:8080"));
		});
	}

	@Test
	void rejectsAnIncompleteServiceConfiguration() {
		contextRunner
				.withPropertyValues("gateway.services.identity=http://identity:8080")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
				});
	}

	@EnableConfigurationProperties(GatewayProperties.class)
	static class TestConfiguration {
	}
}
