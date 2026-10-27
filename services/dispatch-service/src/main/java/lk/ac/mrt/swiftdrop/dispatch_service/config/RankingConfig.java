package lk.ac.mrt.swiftdrop.dispatch_service.config;

import lk.ac.mrt.swiftdrop.dispatch_service.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class RankingConfig {
    @Bean @ConditionalOnMissingBean(CourierRankingStrategy.class)
    CourierRankingStrategy courierRankingStrategy() { return new DefaultCourierRankingStrategy(); }
}
