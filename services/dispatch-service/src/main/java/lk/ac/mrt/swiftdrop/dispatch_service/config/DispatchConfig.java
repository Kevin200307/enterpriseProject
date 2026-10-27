package lk.ac.mrt.swiftdrop.dispatch_service.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration @EnableScheduling
public class DispatchConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
