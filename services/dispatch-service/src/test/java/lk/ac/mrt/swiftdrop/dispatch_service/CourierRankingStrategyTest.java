package lk.ac.mrt.swiftdrop.dispatch_service;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.service.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CourierRankingStrategyTest {
    @Test void filtersRanksLimitsAndBreaksTiesDeterministically() {
        var strategy=new DefaultCourierRankingStrategy();
        List<CourierRankingStrategy.Candidate> candidates=new ArrayList<>();
        for (int i=8;i>=1;i--) candidates.add(new CourierRankingStrategy.Candidate(new UUID(0,i),"Courier "+i,"COLOMBO",true,1,3));
        candidates.add(new CourierRankingStrategy.Candidate(UUID.randomUUID(),"Inactive","COLOMBO",false,0,3));
        candidates.add(new CourierRankingStrategy.Candidate(UUID.randomUUID(),"Full","COLOMBO",true,3,3));
        candidates.add(new CourierRankingStrategy.Candidate(UUID.randomUUID(),"Wrong zone","KANDY",true,0,3));
        var result=strategy.rank("COLOMBO",candidates);
        assertThat(result).extracting(r -> r.courierId()).containsExactly(new UUID(0,1),new UUID(0,2),new UUID(0,3));
        assertThat(result).allMatch(r -> r.remainingCapacity()==2 && r.reason().contains("COLOMBO"));
    }
    @Test void applicationCanReplaceDefaultStrategy() {
        CourierRankingStrategy replacement=(zone,rows) -> List.of();
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(lk.ac.mrt.swiftdrop.dispatch_service.config.RankingConfig.class)
                .withBean(CourierRankingStrategy.class,() -> replacement)
                .run(context -> assertThat(context.getBean(CourierRankingStrategy.class)).isSameAs(replacement));
    }
}
