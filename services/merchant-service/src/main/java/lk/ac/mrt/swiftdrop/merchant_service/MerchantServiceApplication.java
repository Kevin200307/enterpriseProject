package lk.ac.mrt.swiftdrop.merchant_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MerchantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerchantServiceApplication.class, args);
	}

}
