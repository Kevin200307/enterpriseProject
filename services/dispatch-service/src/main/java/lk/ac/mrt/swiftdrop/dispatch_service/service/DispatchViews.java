package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelDispatchChangedV1;
import lk.ac.mrt.swiftdrop.contracts.domain.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DispatchViews {
    public static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private final Clock clock;
    public DispatchViews(JdbcTemplate jdbc, JsonMapper mapper, Clock clock) { this.jdbc=jdbc; this.mapper=mapper; this.clock=clock; }
    public List<QueueItem> queue(String zone) {
        return jdbc.query("""
            select p.* from dispatch_parcels p where p.status='CREATED' and p.assignee_user_id is null
            and not exists (select 1 from assignment_commands c where c.pending_parcel_id=p.id)
            and (cast(? as varchar) is null or p.zone_code=?) order by p.created_at,p.id
            """,(rs,n) -> new QueueItem(rs.getObject("id",UUID.class),rs.getString("tracking_number"),
                rs.getString("pickup_city"),PackageSize.valueOf(rs.getString("size")),rs.getBigDecimal("weight_kg"),
                rs.getTimestamp("created_at").toInstant()),zone,zone);
    }
    public List<WorkloadRow> workload() {
        var today = LocalDate.now(clock.withZone(COLOMBO));
        var start = java.sql.Timestamp.from(today.atStartOfDay(COLOMBO).toInstant());
        var end = java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(COLOMBO).toInstant());
        return jdbc.query("""
            select c.id,c.name,c.active,c.max_daily_load,c.open_load,
              count(a.id) filter (where a.closed_at is null) as active_assignments,
              count(a.id) filter (where a.completed_at>=? and a.completed_at<?) as delivered_today,
              count(a.id) filter (where a.failed_at>=? and a.failed_at<?) as failed_today
            from couriers c left join assignments a on a.courier_id=c.id
            group by c.id order by c.name,c.id
            """,(rs,n) -> new WorkloadRow(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBoolean("active"),
                rs.getLong("active_assignments"),rs.getLong("delivered_today"),rs.getLong("failed_today"),
                rs.getInt("max_daily_load")-rs.getInt("open_load")),start,end,start,end);
    }
    public List<Assignment> mine(UUID userId) {
        return jdbc.query("""
            select a.*,p.snapshot,p.status as parcel_status from assignments a
            join couriers c on c.id=a.courier_id join dispatch_parcels p on p.id=a.parcel_id
            where c.user_id=? and a.closed_at is null order by p.pickup_city,p.dropoff_city,p.id
            """,(rs,n) -> assignment(rs),userId);
    }
    public List<Assignment> manifest(UUID courierId, LocalDate date, UUID caller, boolean manager) {
        var start = java.sql.Timestamp.from(date.atStartOfDay(COLOMBO).toInstant());
        var end = java.sql.Timestamp.from(date.plusDays(1).atStartOfDay(COLOMBO).toInstant());
        // The left join preserves a courier row for ownership checks even when their manifest is empty.
        return jdbc.query("""
            select c.user_id,a.*,p.snapshot,p.status as parcel_status from couriers c
            left join assignments a on a.courier_id=c.id and a.assigned_at>=? and a.assigned_at<?
            left join dispatch_parcels p on p.id=a.parcel_id where c.id=?
            order by p.pickup_city,p.dropoff_city,a.assigned_at,a.id
            """,rs -> {
                if (!rs.next() || !manager && !caller.equals(rs.getObject("user_id",UUID.class)))
                    throw new DispatchException(404,"Courier was not found");
                List<Assignment> result = new ArrayList<>();
                do { if (rs.getObject("id") != null) result.add(assignment(rs)); } while (rs.next());
                return result;
            },start,end,courierId);
    }
    private Assignment assignment(ResultSet rs) throws SQLException {
        var snapshot = mapper.readValue(rs.getString("snapshot"),ParcelDispatchChangedV1.class);
        var status = ParcelStatus.valueOf(rs.getString("parcel_status"));
        boolean mask = status == ParcelStatus.CREATED;
        return new Assignment(rs.getObject("id",UUID.class),rs.getObject("parcel_id",UUID.class),snapshot.trackingNumber(),
                status,address(snapshot.pickup(),false),address(snapshot.dropoff(),mask),snapshot.size(),snapshot.weightKg(),
                rs.getTimestamp("assigned_at").toInstant());
    }
    private Address address(ParcelDispatchChangedV1.Address a, boolean mask) {
        String phone = a.phone();
        if (mask) phone = phone.length() > 4 ? "*".repeat(phone.length()-4)+phone.substring(phone.length()-4) : "****";
        return new Address(a.contactName(),phone,a.line1(),a.line2(),a.city(),a.postalCode());
    }
    public List<DailyStat> stats(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>365)
            throw new DispatchException(400,"Date range must be ordered and contain at most 366 days");
        var start = java.sql.Timestamp.from(from.atStartOfDay(COLOMBO).toInstant());
        var end = java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(COLOMBO).toInstant());
        Map<LocalDate,long[]> counts = new HashMap<>();
        jdbc.query("""
            select cast(timezone('Asia/Colombo',event_at) as date) as day,kind,count(*) as total from (
              select assigned_at as event_at,'created' as kind from assignments where assigned_at>=? and assigned_at<?
              union all select completed_at,'completed' from assignments where completed_at>=? and completed_at<?
              union all select failed_at,'failed' from assignments where failed_at>=? and failed_at<?
            ) events group by day,kind
            """,(org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                int index = switch(rs.getString("kind")) { case "created" -> 0; case "completed" -> 1; default -> 2; };
                counts.computeIfAbsent(rs.getObject("day",LocalDate.class),d -> new long[3])[index] = rs.getLong("total");
            },start,end,start,end,start,end);
        return from.datesUntil(to.plusDays(1)).map(d -> {
            var values = counts.getOrDefault(d,new long[3]); return new DailyStat(d,values[0],values[1],values[2]);
        }).toList();
    }
}
