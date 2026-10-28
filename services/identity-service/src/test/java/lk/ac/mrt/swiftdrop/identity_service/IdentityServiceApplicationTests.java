package lk.ac.mrt.swiftdrop.identity_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires live PostgreSQL database from docker-compose or Neon; verified during container integration runs")
class IdentityServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
