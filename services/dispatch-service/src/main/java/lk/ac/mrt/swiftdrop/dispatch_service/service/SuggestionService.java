package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.Suggestion;
import lk.ac.mrt.swiftdrop.dispatch_service.repository.ParcelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {
    private final ParcelRepository parcels;
    private final JdbcTemplate jdbc;
    private final CourierRankingStrategy ranking;
    public SuggestionService(ParcelRepository parcels, JdbcTemplate jdbc, CourierRankingStrategy ranking) {
        this.parcels=parcels; this.jdbc=jdbc; this.ranking=ranking;
    }
    public List<Suggestion> suggest(UUID parcelId) {
        var parcel = parcels.findById(parcelId).orElseThrow(() -> new DispatchException(404,"Parcel was not found"));
        if (!parcel.assignable() || parcel.getAssigneeUserId()!=null) return List.of();
        var candidates = jdbc.query("select id,name,home_zone_code,active,open_load,max_daily_load from couriers where home_zone_code=? and active=true and open_load<max_daily_load",
                (rs,n) -> new CourierRankingStrategy.Candidate(rs.getObject("id",UUID.class),rs.getString("name"),
                    rs.getString("home_zone_code"),rs.getBoolean("active"),rs.getInt("open_load"),rs.getInt("max_daily_load")),parcel.getZoneCode());
        return ranking.rank(parcel.getZoneCode(),candidates);
    }
}
