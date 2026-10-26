package lk.ac.mrt.swiftdrop.parcel_service.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class ParcelConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
