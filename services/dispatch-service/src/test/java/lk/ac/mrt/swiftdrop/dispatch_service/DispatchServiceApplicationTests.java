package lk.ac.mrt.swiftdrop.dispatch_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class DispatchServiceApplicationTests extends IntegrationSupport {

    @Test
    void contextLoads() {
    }

    @Test
    void contextLoadsAndPublishesOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.3"));
    }
}