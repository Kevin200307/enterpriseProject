package lk.ac.mrt.swiftdrop.pricing_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PricingServiceApplicationTests {
	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired JdbcTemplate jdbc;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void migrationsSeedEveryCityAndAllSizeRoutePairs() {
		assertThat(jdbc.queryForObject("select count(*) from zones", Integer.class)).isEqualTo(8);
		assertThat(jdbc.queryForObject("select count(*) from zone_cities", Integer.class)).isEqualTo(8);
		assertThat(jdbc.queryForObject("select count(*) from pricing_rules", Integer.class)).isEqualTo(192);
		assertThat(jdbc.queryForObject("select count(*) from pricing_rules where active", Integer.class)).isEqualTo(192);
		assertThat(jdbc.queryForObject("select count(*) from zone_cities where normalized_city = 'negombo'", Integer.class)).isZero();
	}

	@Test
	void schemaRejectsNegativeFeesAndDuplicateActiveCity() {
		UUID colombo = UUID.fromString("10000000-0000-0000-0000-000000000001");
		UUID gampaha = UUID.fromString("10000000-0000-0000-0000-000000000002");
		assertThatThrownBy(() -> jdbc.update("insert into zone_cities(zone_id, city_name, normalized_city) values (?, ?, ?)",
			gampaha, "COLOMBO", "colombo")).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> jdbc.update("update pricing_rules set base_fee = -1 where id = (select id from pricing_rules limit 1)"))
			.isInstanceOf(RuntimeException.class);
		assertThat(jdbc.queryForObject("select count(*) from zone_cities where zone_id = ? and normalized_city = 'colombo'", Integer.class, colombo))
			.isEqualTo(1);
	}

}