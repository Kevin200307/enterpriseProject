package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.Suggestion;

public interface CourierRankingStrategy {
    record Candidate(UUID id, String name, String zone, boolean active, int load, int capacity) {}
    List<Suggestion> rank(String zone, List<Candidate> candidates);
}
