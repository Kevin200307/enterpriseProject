package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing for automated created_at and updated_at timestamp population.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
