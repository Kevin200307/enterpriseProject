package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.Suggestion;

public class DefaultCourierRankingStrategy implements CourierRankingStrategy {
    @Override public List<Suggestion> rank(String zone, List<Candidate> candidates) {
        return candidates.stream().filter(c -> c.active() && c.zone().equals(zone) && c.load()<c.capacity())
                .sorted(Comparator.comparingInt(Candidate::load).thenComparing(c -> c.id().toString())).limit(3)
                .map(c -> new Suggestion(c.id(),c.name(),"Home zone "+zone+"; "+c.load()+" active or reserved parcels; "
                    +(c.capacity()-c.load())+" capacity remaining",c.capacity()-c.load())).toList();
    }
}
