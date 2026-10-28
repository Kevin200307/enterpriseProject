package lk.ac.mrt.swiftdrop.identity_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuth2PropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(GoogleOAuth2Properties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Should assign secure default values when constructor arguments are null or blank")
    void shouldUseDefaultValuesWhenPropertiesAreBlankOrNull() {
        GoogleOAuth2Properties properties = new GoogleOAuth2Properties(
                null, "", "   ", null, null, null, null, null
        );

        assertThat(properties.clientId()).isEqualTo("mock-google-client-id.apps.googleusercontent.com");
        assertThat(properties.clientSecret()).isEqualTo("mock-google-client-secret");
        assertThat(properties.redirectUri()).isEqualTo("http://localhost:5173/oauth/callback");
        assertThat(properties.authorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(properties.tokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(properties.userInfoUri()).isEqualTo("https://openidconnect.googleapis.com/v1/userinfo");
        assertThat(properties.jwkSetUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        assertThat(properties.issuer()).isEqualTo("https://accounts.google.com");
    }

    @Test
    @DisplayName("Should preserve custom values passed explicitly")
    void shouldPreserveExplicitlyConfiguredValues() {
        GoogleOAuth2Properties properties = new GoogleOAuth2Properties(
                "custom-client-id.apps.googleusercontent.com",
                "custom-client-secret",
                "https://app.swiftdrop.lk/oauth/callback",
                "https://accounts.google.com/custom-auth",
                "https://oauth2.googleapis.com/custom-token",
                "https://openidconnect.googleapis.com/custom-userinfo",
                "https://www.googleapis.com/custom-certs",
                "https://accounts.google.com/custom"
        );

        assertThat(properties.clientId()).isEqualTo("custom-client-id.apps.googleusercontent.com");
        assertThat(properties.clientSecret()).isEqualTo("custom-client-secret");
        assertThat(properties.redirectUri()).isEqualTo("https://app.swiftdrop.lk/oauth/callback");
        assertThat(properties.authorizationUri()).isEqualTo("https://accounts.google.com/custom-auth");
        assertThat(properties.tokenUri()).isEqualTo("https://oauth2.googleapis.com/custom-token");
        assertThat(properties.userInfoUri()).isEqualTo("https://openidconnect.googleapis.com/custom-userinfo");
        assertThat(properties.jwkSetUri()).isEqualTo("https://www.googleapis.com/custom-certs");
        assertThat(properties.issuer()).isEqualTo("https://accounts.google.com/custom");
    }

    @Test
    @DisplayName("Should successfully bind properties from Spring environment using @ConfigurationProperties")
    void shouldBindFromApplicationContextUsingConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "app.security.oauth2.google.client-id=env-google-client-id",
                        "app.security.oauth2.google.client-secret=env-google-client-secret",
                        "app.security.oauth2.google.redirect-uri=http://localhost:5173/oauth/callback"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(GoogleOAuth2Properties.class);
                    GoogleOAuth2Properties props = context.getBean(GoogleOAuth2Properties.class);
                    assertThat(props.clientId()).isEqualTo("env-google-client-id");
                    assertThat(props.clientSecret()).isEqualTo("env-google-client-secret");
                    assertThat(props.redirectUri()).isEqualTo("http://localhost:5173/oauth/callback");
                    assertThat(props.issuer()).isEqualTo("https://accounts.google.com");
                });
    }
}
