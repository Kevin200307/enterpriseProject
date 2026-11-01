package lk.ac.mrt.swiftdrop.identity_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(AdminBootstrapProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Should disable bootstrap and leave credentials empty when properties are missing")
    void shouldDisableBootstrapWhenPropertiesAreMissing() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(null, null, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.bootstrapEmail()).isNull();
        assertThat(properties.bootstrapPassword()).isNull();
        assertThat(properties.bootstrapName()).isEqualTo("SwiftDrop Platform Administrator");
        assertThat(properties.bootstrapPhone()).isEqualTo("+94770000001");
    }

    @Test
    @DisplayName("Should successfully bind properties from Spring environment using @ConfigurationProperties")
    void shouldBindFromApplicationContextUsingConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "app.security.admin.enabled=true",
                        "app.security.admin.bootstrap-email=custom-admin@swiftdrop.lk",
                        "app.security.admin.bootstrap-password=CustomSecretPassword123!",
                        "app.security.admin.bootstrap-name=Custom Admin",
                        "app.security.admin.bootstrap-phone=+94771234567"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AdminBootstrapProperties properties = context.getBean(AdminBootstrapProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.bootstrapEmail()).isEqualTo("custom-admin@swiftdrop.lk");
                    assertThat(properties.bootstrapPassword()).isEqualTo("CustomSecretPassword123!");
                    assertThat(properties.bootstrapName()).isEqualTo("Custom Admin");
                    assertThat(properties.bootstrapPhone()).isEqualTo("+94771234567");
                });
    }
}
