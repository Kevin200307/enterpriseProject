package lk.ac.mrt.swiftdrop.gateway.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe addresses for services reached by the gateway.
 *
 * <p>The values are supplied by {@code application.yaml} and can be overridden
 * with environment variables in Compose or Kubernetes. Keeping these addresses
 * out of Java routing code lets the same gateway artifact run in every
 * environment.</p>
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
		@NotNull @Valid Services services,
		@NotNull @Valid Browser browser) {

	public record Services(
			@NotNull URI identity,
			@NotNull URI parcel,
			@NotNull URI pricing,
			@NotNull URI dispatch,
			@NotNull URI merchant,
			@NotNull URI tracking,
			@NotNull URI notification,
			@NotNull URI aiAssistant) {
	}

	public record Browser(@NotNull URI origin) {
	}
}
