package lk.ac.mrt.swiftdrop.tracking_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trackingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftDrop Tracking Service API")
                        .description("High-performance, privacy-safe parcel tracking service with Redis read models, WebSocket, and SSE streaming.")
                        .version("1.0.0")
                        .contact(new Contact().name("SwiftDrop Engineering").email("support@swiftdrop.lk"))
                        .license(new License().name("Proprietary")))
                .schemaRequirement("BearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT bearer token for private tracking endpoints"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
