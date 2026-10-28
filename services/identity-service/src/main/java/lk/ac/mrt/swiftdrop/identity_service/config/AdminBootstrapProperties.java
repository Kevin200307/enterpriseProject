package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for platform administrator bootstrapping
 * Loaded from 'app.security.admin' in application.yaml with environment variable overrides.
 */
@ConfigurationProperties(prefix = "app.security.admin")
public record AdminBootstrapProperties(
        Boolean enabled,
        String bootstrapEmail,
        String bootstrapPassword,
        String bootstrapName,
        String bootstrapPhone
) {
    public AdminBootstrapProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (bootstrapName == null || bootstrapName.isBlank()) {
            bootstrapName = "SwiftDrop Platform Administrator";
        }
        if (bootstrapPhone == null || bootstrapPhone.isBlank()) {
            bootstrapPhone = "+94770000001";
        }
    }
}
