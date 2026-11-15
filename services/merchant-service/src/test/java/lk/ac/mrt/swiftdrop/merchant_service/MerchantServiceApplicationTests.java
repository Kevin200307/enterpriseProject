package lk.ac.mrt.swiftdrop.merchant_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import lk.ac.mrt.swiftdrop.merchant_service.repository.ApiKeyRepository;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantOrderRepository;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantRepository;
import lk.ac.mrt.swiftdrop.merchant_service.repository.WebhookDeliveryRepository;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=" +
				"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
				"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
				"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class MerchantServiceApplicationTests {
	@MockitoBean
	MerchantRepository merchants;

	@MockitoBean
	MerchantOrderRepository orders;

	@MockitoBean
	ApiKeyRepository apiKeys;

	@MockitoBean
	WebhookDeliveryRepository webhookDeliveries;

	@Test
	void contextLoads() {
	}

}
